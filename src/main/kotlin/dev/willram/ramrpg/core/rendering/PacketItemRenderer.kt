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
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.LoreContext
import dev.willram.ramrpg.api.items.SetLoreInfo
import dev.willram.ramrpg.api.items.colorOf
import dev.willram.ramrpg.api.items.isMet
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
    /**
     * WP-lore: a hash of the viewer-state that requirement / set-bonus lore depends on -- whether each
     * requirement is met for THIS viewer, plus the viewer's active set-member count (see
     * [PacketItemRendererImpl.viewerStateHash]). Keyed here so a player whose skills or equipped set
     * pieces changed misses the cache and re-renders, with no unrelated cache invalidation. Defaults to
     * `0` for callers (and pure tests) that render no viewer-state-dependent lore.
     */
    val viewerStateHash: Int = 0,
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

    /**
     * WP-lore: builds the viewer's [ItemRequirementState] so [dev.willram.ramrpg.api.items.LoreSection.Requirements]
     * renders each requirement met (green) / unmet (red) FOR THIS VIEWER. Set by
     * [dev.willram.ramrpg.core.modules.UiModule] at setup() (never RamRPG.kt, B5) via the same
     * module-injected-hook pattern [dev.willram.ramrpg.core.services.EntityProfileRegistryImpl.levelBandService]
     * establishes -- the renderer is constructed in RamRPG.load() before the skill/stat services this needs
     * exist, so it cannot be constructor-injected. Null until wired; requirement lore then renders
     * fail-closed (every requirement unmet), matching WP-2.1c's fail-closed default.
     */
    var requirementStateHook: ((Player) -> ItemRequirementState?)? = null

    /**
     * WP-lore: display name for a requirement's [SkillKey] (from the skill registry) so a skill-level
     * requirement reads "Combat 10", not "combat 10". Set alongside [requirementStateHook] by UiModule.
     */
    var skillNameLookup: (SkillKey) -> Component? = { null }

    /**
     * WP-lore: builds the viewer's [SetLoreInfo] for a rendered item's set -- its active member count for
     * THIS viewer -- or null when the item belongs to no set. Set by
     * [dev.willram.ramrpg.core.modules.SetModule] at setup(); the SetRegistry it reuses is created in
     * SetModule.setup(), after the renderer is constructed, so it too is injected rather than constructed.
     * Null renders no set block (a non-member item legitimately shows nothing).
     */
    var setLoreHook: ((Player, ItemKey) -> SetLoreInfo?)? = null

    override fun invalidate() = cache.invalidateAll()

    override fun render(viewer: Player, item: ItemStack): ItemStack {
        if (item.type.isAir) return item
        val meta = item.itemMeta
        if (meta != null && PDCs.has(meta, GUI_MARKER)) return item
        // Not every container holding RPG items is itself an RPG item (a plain vanilla bundle or
        // shulker box has no ItemInstanceData) - still walk it for nested RPG payloads.
        val data = instances.identify(item) ?: return renderNested(viewer, item)
        val def = registry.get(data.identity.key) ?: return renderNested(viewer, item)
        // WP-lore: per-viewer lore state. Built BEFORE the cache lookup so it both (a) feeds the render
        // and (b) folds into the cache key via viewerStateHash. Cheap -- a few skill-level reads plus one
        // equipped-slot scan -- far cheaper than the ItemStack clone + component build it gates.
        val reqState = requirementStateHook?.invoke(viewer)
        val setInfo = setLoreHook?.invoke(viewer, def.key)
        val key = RenderCacheKey(
            itemKeyId = data.identity.key.id.toString(),
            schemaVersion = data.identity.schemaVersion,
            instanceHash = instanceHash(data),
            viewerLocale = viewer.locale().toString(),
            definitionRev = registry.revision(),
            viewerStateHash = viewerStateHash(def, reqState, setInfo),
        )
        val rendered = cache.get(key) { renderUncached(def, data, viewer, item, reqState, setInfo) }
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

    private fun renderUncached(
        def: ItemDefinition,
        data: ItemInstanceData,
        viewer: Player,
        source: ItemStack,
        requirementState: ItemRequirementState?,
        setBonus: SetLoreInfo?,
    ): ItemStack {
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
            skillNameLookup = skillNameLookup,
            requirementState = requirementState,
            setBonus = setBonus,
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

    /**
     * WP-lore: the per-viewer-state component of [RenderCacheKey]. Requirement met/unmet and the active
     * set-member count depend on the VIEWER (their skills, their equipped gear), not on (itemHash, locale)
     * -- so unless the cache keys on them, a player who levelled a skill or swapped armor would keep seeing
     * stale lore until some unrelated property invalidated the cache. This WP takes approach (B): extend the
     * cache key. It is chosen over composing these sections post-cache (A) because -- unlike cooldown, which
     * ticks continuously and so stays a post-cache compose (see [applyCooldownLore]) -- requirement/set state
     * changes only on discrete skill/equipment events, so per-viewer-state caching keeps the full template
     * (correct section ORDER included) intact and simply refreshes on those events, with no fragile
     * splice-at-position logic. Hashes ONLY what changes the rendered output: whether each requirement is
     * met, and the active set count.
     */
    private fun viewerStateHash(def: ItemDefinition, reqState: ItemRequirementState?, setInfo: SetLoreInfo?): Int {
        var h = 1
        for (req in def.requirements) h = 31 * h + (if (reqState != null && req.isMet(reqState)) 1 else 0)
        h = 31 * h + (setInfo?.activeCount ?: -1)
        return h
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
