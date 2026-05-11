/**
 * Subscribes ProtocolLib packets WINDOW_ITEMS, SET_SLOT, ENTITY_METADATA
 * and routes each ItemStack through PacketItemRenderer.
 */
package dev.willram.ramrpg.core.rendering

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketContainer
import dev.willram.ramcore.protocol.Protocol
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class PacketRenderListener(private val renderer: PacketItemRenderer) {

    fun register() {
        Protocol.subscribe(
            PacketType.Play.Server.WINDOW_ITEMS,
            PacketType.Play.Server.SET_SLOT,
            PacketType.Play.Server.ENTITY_METADATA,
            PacketType.Play.Server.ENTITY_EQUIPMENT,
        ).handler { ev ->
            val player = ev.player ?: return@handler
            val packet: PacketContainer = ev.packet
            try {
                renderSingles(packet, player)
                renderItemList(packet, player)
                renderEntityMetadata(packet, player)
                renderEquipmentList(packet, player)
            } catch (_: Throwable) {
                // best-effort: leave packet untouched on render failure
            }
        }
    }

    private fun renderEntityMetadata(packet: PacketContainer, viewer: Player) {
        val dvMod = try { packet.dataValueCollectionModifier } catch (_: Throwable) { return }
        if (dvMod.size() == 0) return
        for (i in 0 until dvMod.size()) {
            val list = dvMod.read(i) ?: continue
            var changed = false
            val out = ArrayList<com.comphenix.protocol.wrappers.WrappedDataValue>(list.size)
            for (dv in list) {
                val v = dv.value
                if (v is ItemStack) {
                    val rendered = renderer.render(viewer, v)
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
