/**
 * Packet-side item renderer. Clones server stack, applies display name +
 * lore template per viewer locale; never mutates the original. Caches
 * results keyed by (item, schema, instance hash, locale, def revision).
 * Walks BundleMeta + ShulkerBox inventories for nested rendering, including
 * containers that are themselves plain vanilla items (WP-1.6b).
 */
package dev.willram.ramrpg.core.rendering

import dev.willram.ramrpg.api.abilities.AbilityService
import dev.willram.ramrpg.api.abilities.formatCooldown
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.identity.AbilityKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.items.LoreContext
import dev.willram.ramrpg.api.items.colorOf
import dev.willram.ramcore.pdc.PDCs
import dev.willram.ramcore.pdc.PdcKey
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.translation.GlobalTranslator
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
import org.bukkit.inventory.meta.ItemMeta
import java.time.Duration
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
    /** Re-renders a bundle/shulker-box payload's contained stacks. No-op for anything else. */
    fun renderNested(viewer: Player, item: ItemStack): ItemStack
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

/**
 * Pure core of nested-container rendering: re-renders each stack in [items] through [render],
 * returning the exact same list instance when nothing changed so callers can skip re-applying
 * meta. Deliberately has no Bukkit meta/ItemFactory dependency (unlike BundleMeta/ShulkerBox,
 * which need a live server to construct) so it is unit-testable off-server.
 */
fun renderNestedItems(items: List<ItemStack>, render: (ItemStack) -> ItemStack): List<ItemStack> {
    var changed = false
    val rendered = items.map { s ->
        val r = render(s)
        if (r !== s) changed = true
        r
    }
    return if (changed) rendered else items
}

class PacketItemRendererImpl(
    private val registry: ItemDefinitionRegistry,
    private val instances: ItemInstanceService,
    private val stats: StatService? = null,
    private val enchants: EnchantmentRegistry? = null,
    private val reforges: ReforgeRegistry? = null,
    private val gems: GemRegistry? = null,
    /** Ability whose cooldown should be shown as a lore line, if any, for a rendered item's definition. */
    private val abilities: AbilityService? = null,
    private val abilityKeyOf: (ItemKey) -> AbilityKey? = { null },
    private val cache: RenderCache = RenderCache(),
) : PacketItemRenderer {

    override fun invalidate() = cache.invalidateAll()

    override fun render(viewer: Player, item: ItemStack): ItemStack {
        if (item.type.isAir) return item
        val meta = item.itemMeta
        if (meta != null && PDCs.has(meta, GUI_MARKER)) return item
        // Not every container holding RPG items is itself an RPG item (a plain vanilla bundle or
        // shulker box has no ItemInstanceData) - still walk it for nested RPG payloads.
        val data = instances.identify(item) ?: return renderNested(viewer, item)
        val def = registry.get(data.identity.key) ?: return renderNested(viewer, item)
        val key = RenderCacheKey(
            itemKeyId = data.identity.key.id.toString(),
            schemaVersion = data.identity.schemaVersion,
            instanceHash = instanceHash(data),
            viewerLocale = viewer.locale().toString(),
            definitionRev = registry.revision(),
        )
        val rendered = cache.get(key) { renderUncached(def, data, viewer, item) }
        // Cooldown remaining changes every tick; composing it here (post-cache) instead of baking
        // it into the cached snapshot keeps RenderCache keyed by (itemHash, locale) only - a live
        // countdown would otherwise go stale until some other item property invalidated the cache.
        return applyCooldownLore(rendered, def.key, viewer)
    }

    override fun renderNested(viewer: Player, item: ItemStack): ItemStack {
        if (item.type.isAir) return item
        val meta = item.itemMeta ?: return item
        if (PDCs.has(meta, GUI_MARKER)) return item
        if (meta !is BundleMeta && meta !is BlockStateMeta) return item
        if (!renderNestedInto(meta, viewer)) return item
        val out = item.clone()
        out.itemMeta = meta
        return out
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

        renderNestedInto(meta, viewer)
        out.itemMeta = meta
        return out
    }

    /** Mutates [meta] in place, re-rendering any nested bundle/shulker payload. Returns whether anything changed. */
    private fun renderNestedInto(meta: ItemMeta, viewer: Player): Boolean {
        var changed = false
        if (meta is BundleMeta) {
            val items = meta.items
            val rendered = renderNestedItems(items) { render(viewer, it) }
            if (rendered !== items) {
                meta.setItems(rendered)
                changed = true
            }
        }
        if (meta is BlockStateMeta) {
            val state = meta.blockState
            if (state is ShulkerBox) {
                val inv = state.inventory
                var stateChanged = false
                for (i in 0 until inv.size) {
                    val s = inv.getItem(i) ?: continue
                    val rendered = render(viewer, s)
                    if (rendered !== s) {
                        inv.setItem(i, rendered)
                        stateChanged = true
                    }
                }
                if (stateChanged) {
                    meta.blockState = state
                    changed = true
                }
            }
        }
        return changed
    }

    /** Appends a "Cooldown: <time>" lore line when [itemKey] maps to an ability that is on cooldown for [viewer]. */
    private fun applyCooldownLore(item: ItemStack, itemKey: ItemKey, viewer: Player): ItemStack {
        val svc = abilities ?: return item
        val abilityKey = abilityKeyOf(itemKey) ?: return item
        val remaining = svc.remaining(viewer, abilityKey)
        if (remaining <= Duration.ZERO) return item
        val meta = item.itemMeta ?: return item
        val line = GlobalTranslator.render(
            Component.translatable("ramrpg.ability.cooldown_lore", Component.text(formatCooldown(remaining))),
            viewer.locale(),
        ).decoration(TextDecoration.ITALIC, false)
        meta.lore((meta.lore() ?: emptyList()) + line)
        item.itemMeta = meta
        return item
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
