/**
 * Subscribes ProtocolLib packets WINDOW_ITEMS, SET_SLOT, ENTITY_METADATA
 * and routes each ItemStack through PacketItemRenderer.
 */
package dev.willram.ramrpg.core.rendering

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketContainer
import dev.willram.ramcore.packet.PacketVisualOperation
import dev.willram.ramcore.packet.PacketVisualTransport
import dev.willram.ramcore.packet.PacketViewer
import dev.willram.ramcore.packet.Packets
import dev.willram.ramcore.protocol.Protocol
import dev.willram.ramrpg.api.items.ItemInstanceService
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Re-renders the display item of a dropped item entity or item frame (entity id [entityId]) for
 * one viewer through RamCore's packet-visual layer (dev.willram.ramcore.packet) rather than a
 * bespoke metadata format. [dev.willram.ramcore.packet.PacketVisualState.metadataPreview] takes an
 * opaque string key, not an NMS entity-data index, so this never hardcodes one. Converting the
 * logical METADATA_PREVIEW operation into a real extra packet is the concrete, version-guarded
 * packet factory's job; RamCore ships no [dev.willram.ramcore.packet.ProtocolVisualPacketFactory]
 * for this action yet (WP-1.6b report), so [PacketRenderListener] still writes the rendered stack
 * into the ENTITY_METADATA packet it already intercepted, reusing that packet's own
 * index/serializer verbatim rather than inventing one. This class makes the render/no-render
 * decision observable and unit-testable off-server via InMemoryPacketVisualTransport.
 */
class EntityItemVisualRenderer(
    private val renderer: PacketItemRenderer,
    private val transport: PacketVisualTransport,
) {
    /**
     * Renders [item] (entity [entityId]'s current display item) for [viewer]. Returns the
     * resulting METADATA_PREVIEW operation - whose data carries the rendered stack under
     * [ITEM_METADATA_KEY] - when rendering changed the stack, or null when it did not.
     */
    fun renderEntityItem(viewer: Player, entityId: Int, item: ItemStack): PacketVisualOperation? {
        val rendered = renderer.render(viewer, item)
        if (rendered === item) return null
        val session = Packets.session(PacketViewer.of(viewer), transport)
        return session.metadataPreview(entityId, ITEM_METADATA_KEY, rendered)
    }

    companion object {
        /** Logical (non-NMS) metadata-preview key for "the entity's displayed item". */
        const val ITEM_METADATA_KEY = "item"
    }
}

class PacketRenderListener(
    private val renderer: PacketItemRenderer,
    private val instances: ItemInstanceService? = null,
    private val entityItemVisuals: EntityItemVisualRenderer? = null,
) {

    fun register() {
        Protocol.subscribe(
            PacketType.Play.Server.WINDOW_ITEMS,
            PacketType.Play.Server.SET_SLOT,
            PacketType.Play.Server.ENTITY_METADATA,
            PacketType.Play.Server.ENTITY_EQUIPMENT,
            PacketType.Play.Server.OPEN_WINDOW_MERCHANT,
        ).handler { ev ->
            val player = ev.player ?: return@handler
            val packet: PacketContainer = ev.packet
            try {
                renderSingles(packet, player)
                renderItemList(packet, player)
                renderEntityMetadata(packet, player)
                renderEquipmentList(packet, player)
                renderMerchantRecipes(packet, player)
            } catch (_: Throwable) {
                // best-effort: leave packet untouched on render failure
            }
        }
        if (instances != null) registerCreativeCanonicalizer()
    }

    private fun renderMerchantRecipes(packet: PacketContainer, viewer: Player) {
        val mod = try { packet.merchantRecipeLists } catch (_: Throwable) { return }
        if (mod.size() == 0) return
        for (i in 0 until mod.size()) {
            val recipes: List<org.bukkit.inventory.MerchantRecipe> = mod.read(i) ?: continue
            var changed = false
            val out = ArrayList<org.bukkit.inventory.MerchantRecipe>(recipes.size)
            for (r in recipes) {
                val origIngredients = r.ingredients
                val origResult = r.result
                val newResult = renderer.render(viewer, origResult)
                val newIngredients = origIngredients.map { renderer.render(viewer, it) }
                val mutated = newResult !== origResult || newIngredients.indices.any { newIngredients[it] !== origIngredients[it] }
                if (!mutated) { out.add(r); continue }
                val nr = org.bukkit.inventory.MerchantRecipe(
                    newResult, r.uses, r.maxUses, r.hasExperienceReward(),
                    r.villagerExperience, r.priceMultiplier,
                )
                nr.ingredients = newIngredients
                out.add(nr)
                changed = true
            }
            if (changed) mod.write(i, out)
        }
    }

    private fun registerCreativeCanonicalizer() {
        Protocol.subscribe(PacketType.Play.Client.SET_CREATIVE_SLOT).handler { ev ->
            try {
                val mod = ev.packet.itemModifier
                if (mod.size() == 0) return@handler
                for (i in 0 until mod.size()) {
                    val incoming: ItemStack = mod.read(i) ?: continue
                    if (incoming.type.isAir) continue
                    val data = instances!!.identify(incoming) ?: continue
                    val canon = instances.write(ItemStack(incoming.type, incoming.amount), data)
                    mod.write(i, canon)
                }
            } catch (_: Throwable) {
                // best-effort: leave inbound packet untouched on failure
            }
        }
    }

    private fun renderEntityMetadata(packet: PacketContainer, viewer: Player) {
        val dvMod = try { packet.dataValueCollectionModifier } catch (_: Throwable) { return }
        if (dvMod.size() == 0) return
        // Entity id is only needed to key the packet-visual session below; when it can't be read
        // (unexpected packet shape), fall back to a plain render so dropped items/item frames still
        // get rendered - just without the ramcore-protocol bookkeeping.
        val entityId = try { packet.integers.read(0) } catch (_: Throwable) { null }
        for (i in 0 until dvMod.size()) {
            val list = dvMod.read(i) ?: continue
            var changed = false
            val out = ArrayList<com.comphenix.protocol.wrappers.WrappedDataValue>(list.size)
            for (dv in list) {
                val v = dv.value
                if (v is ItemStack) {
                    val rendered = renderEntityStack(viewer, entityId, v)
                    if (rendered !== v) {
                        out.add(com.comphenix.protocol.wrappers.WrappedDataValue(dv.index, dv.serializer, rendered))
                        changed = true
                        continue
                    }
                }
                out.add(dv)
            }
            if (changed) dvMod.write(i, out)
        }
    }

    /**
     * Renders an entity-metadata ItemStack - the display item of a dropped item entity or item
     * frame. Routes through [entityItemVisuals] (the ramcore-protocol packet-visual layer) when an
     * entity id is known; otherwise renders directly. Either way the real packet write above reuses
     * the intercepted WrappedDataValue's own index/serializer, so no metadata index is invented.
     */
    private fun renderEntityStack(viewer: Player, entityId: Int?, item: ItemStack): ItemStack {
        val visuals = entityItemVisuals
        if (visuals == null || entityId == null) return renderer.render(viewer, item)
        val op = visuals.renderEntityItem(viewer, entityId, item) ?: return item
        return op.data()[EntityItemVisualRenderer.ITEM_METADATA_KEY] as? ItemStack ?: item
    }

    private fun renderSingles(packet: PacketContainer, viewer: Player) {
        val mod = packet.itemModifier
        if (mod.size() == 0) return
        for (i in 0 until mod.size()) {
            val orig: ItemStack = mod.read(i) ?: continue
            val rendered = renderer.render(viewer, orig)
            if (rendered !== orig) mod.write(i, rendered)
        }
    }

    private fun renderItemList(packet: PacketContainer, viewer: Player) {
        val mod = packet.itemListModifier
        if (mod.size() == 0) return
        for (i in 0 until mod.size()) {
            val list: List<ItemStack> = mod.read(i) ?: continue
            val out = ArrayList<ItemStack>(list.size)
            for (s in list) out.add(renderer.render(viewer, s))
            mod.write(i, out)
        }
    }

    private fun renderEquipmentList(packet: PacketContainer, viewer: Player) {
        val mod = try { packet.slotStackPairLists } catch (_: Throwable) { return }
        if (mod.size() == 0) return
        for (i in 0 until mod.size()) {
            val pairs = mod.read(i) ?: continue
            var changed = false
            val out = ArrayList<com.comphenix.protocol.wrappers.Pair<com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot, ItemStack>>(pairs.size)
            for (p in pairs) {
                val orig = p.second
                val rendered = renderer.render(viewer, orig)
                if (rendered !== orig) {
                    out.add(com.comphenix.protocol.wrappers.Pair(p.first, rendered))
                    changed = true
                } else {
                    out.add(p)
                }
            }
            if (changed) mod.write(i, out)
        }
    }
}
