/**
 * Packet-side item renderer. Clones server stack, applies display name +
 * lore template per viewer locale; never mutates the original. Caches
 * results keyed by (item, schema, instance hash, locale, def revision).
 * Walks BundleMeta + ShulkerBox inventories for nested rendering.
 */
package dev.willram.ramrpg.core.rendering

import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.items.LoreContext
import dev.willram.ramrpg.api.items.colorOf
import dev.willram.ramcore.pdc.PDCs
import dev.willram.ramcore.pdc.PdcKey
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.persistence.PersistentDataType
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.StatFormat
import dev.willram.ramrpg.api.stats.StatService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta
data class RenderCacheKey(
    val itemKeyId: String,
    val schemaVersion: Int,
    val instanceHash: Int,
    val viewerLocale: String,
    val definitionRev: Int,
)

class RenderCache(private val cap: Int = 4096) {
    private val map = object : java.util.LinkedHashMap<RenderCacheKey, ItemStack>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RenderCacheKey, ItemStack>): Boolean = size > cap
    }
    private val lock = Any()
    fun get(key: RenderCacheKey, factory: () -> ItemStack): ItemStack {
        synchronized(lock) { map[key]?.let { return it.clone() } }
        val v = factory()
        synchronized(lock) { map[key] = v.clone() }
        return v
    }
    fun invalidateAll() { synchronized(lock) { map.clear() } }
    fun size(): Int = synchronized(lock) { map.size }
}

interface PacketItemRenderer {
    fun render(viewer: Player, item: ItemStack): ItemStack
    fun invalidate()
}

/** PDC key that suppresses RPG render on an ItemStack (used for GUI icons). */
val GUI_MARKER: PdcKey<Byte, Byte> = PdcKey.of("ramrpg", "gui_icon", PersistentDataType.BYTE)

/** Apply the renderer-skip marker so a GUI item shows its raw meta unchanged. */
fun ItemStack.markGuiIcon(): ItemStack {
    val meta = itemMeta ?: return this
    PDCs.set(meta, GUI_MARKER, 1.toByte())
    itemMeta = meta
    return this
}

class PacketItemRendererImpl(
    private val registry: ItemDefinitionRegistry,
    private val instances: ItemInstanceService,
    private val stats: StatService? = null,
    private val enchants: EnchantmentRegistry? = null,
    private val reforges: ReforgeRegistry? = null,
    private val gems: GemRegistry? = null,
    private val cache: RenderCache = RenderCache(),
) : PacketItemRenderer {

    override fun invalidate() = cache.invalidateAll()

    override fun render(viewer: Player, item: ItemStack): ItemStack {
        if (item.type.isAir) return item
        val meta = item.itemMeta
        if (meta != null && PDCs.has(meta, GUI_MARKER)) return item
        val data = instances.identify(item) ?: return item
        val def = registry.get(data.identity.key) ?: return item
        val key = RenderCacheKey(
            itemKeyId = data.identity.key.id.toString(),
            schemaVersion = data.identity.schemaVersion,
            instanceHash = instanceHash(data),
            viewerLocale = viewer.locale().toString(),
            definitionRev = registry.revision(),
        )
        return cache.get(key) { renderUncached(def, data, viewer, item) }
    }

    private fun renderUncached(def: ItemDefinition, data: ItemInstanceData, viewer: Player, source: ItemStack): ItemStack {
        val out = source.clone()
        val meta = out.itemMeta ?: return out
        val rarityColor = colorOf(def.rarity)
        val rawName = data.customName
            ?.let { Component.text(it) }
            ?: def.displayName
        val name = rawName
            .colorIfAbsent(rarityColor)
            .decoration(TextDecoration.ITALIC, false)
        meta.displayName(name)

        val ctx = LoreContext(
            definition = def,
            instance = data,
            viewer = viewer,
            enchantNameLookup = { ek -> enchants?.get(ek)?.displayName },
            statNameLookup = { sk -> stats?.definition(sk)?.displayName },
            statColorLookup = { sk -> stats?.definition(sk)?.color },
            statFormatLookup = { sk -> stats?.definition(sk)?.format ?: StatFormat.WHOLE },
            reforgeNameLookup = { rk -> reforges?.get(rk)?.displayName },
            gemNameLookup = { id -> gems?.get(GemKey(id))?.displayName },
        )
        val lore = def.loreTemplate.render(ctx)
            .map { it.decoration(TextDecoration.ITALIC, false) }
        if (lore.isNotEmpty()) meta.lore(lore)

        renderNested(meta, viewer)
        out.itemMeta = meta
        return out
    }

    private fun renderNested(meta: org.bukkit.inventory.meta.ItemMeta, viewer: Player) {
        if (meta is BundleMeta) {
            meta.setItems(meta.items.map { render(viewer, it) })
        }
        if (meta is BlockStateMeta) {
            val state = meta.blockState
            if (state is ShulkerBox) {
                val inv = state.inventory
                for (i in 0 until inv.size) {
                    val s = inv.getItem(i) ?: continue
                    inv.setItem(i, render(viewer, s))
                }
                meta.blockState = state
            }
        }
    }

    private fun instanceHash(d: ItemInstanceData): Int {
        var h = d.upgradeLevel
        h = 31 * h + (d.reforge?.id?.hashCode() ?: 0)
        h = 31 * h + d.sockets.hashCode()
        h = 31 * h + d.customRolls.hashCode()
        h = 31 * h + d.enchantments.hashCode()
        h = 31 * h + (d.customName?.hashCode() ?: 0)
        return h
    }
}
