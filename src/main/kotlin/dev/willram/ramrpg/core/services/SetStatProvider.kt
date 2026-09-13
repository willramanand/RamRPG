/**
 * WP-5.3: the armor-set `StatProvider`. Counts a player's equipped, NON-INERT set members and emits
 * the [dev.willram.ramrpg.api.effects.StatEffect] entries of every threshold whose required count is
 * met -- thresholds STACK (reaching 4/4 keeps the 2-piece bonus active too). See
 * docs/design/5.3-armor-sets.md for the full model.
 *
 * Registered via `SetModule` (never `RamRPG.kt` -- B5), and rides the SAME equipment-dirty ->
 * `StatService.recalculateNow` path every other item-based provider already rides: [StatServiceImpl]
 * re-queries every registered `StatProvider` (this one included) on each recalculation, so no new
 * listener hook is needed (rule 2; see `core/listeners/EquipmentListener.kt`'s WP-5.3 note).
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramrpg.api.effects.ScalingContext
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.SetLoreInfo
import dev.willram.ramrpg.api.items.isInert
import dev.willram.ramrpg.api.sets.SetDefinition
import dev.willram.ramrpg.api.sets.SetRegistry
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatContext
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.api.stats.StatProvider
import dev.willram.ramrpg.core.listeners.ItemRequirementServices
import dev.willram.ramrpg.core.listeners.requirementStateFor
import org.bukkit.entity.Player

/**
 * WP-5.3 / WP-2.1c: how many of [equipped]'s (def, data) pairs count toward [set]'s member count --
 * an item counts only when its [dev.willram.ramrpg.api.items.ItemKey] is a [SetDefinition.members]
 * entry AND it is NOT [isInert] against [state] (the SAME shared check every other item-based provider
 * consults -- see docs/design/2.1c-inert-items.md's "Sets (WP-5.3, future)" note this fulfils). An
 * inert or requirement-failing piece can never pad a set's active count.
 */
internal fun equippedSetCount(
    equipped: List<Pair<ItemDefinition, ItemInstanceData>>,
    state: ItemRequirementState,
    set: SetDefinition,
): Int = equipped.count { (def, data) -> def.key in set.members && !def.isInert(data, state) }

/** WP-5.3: every threshold count in [set] that is `<= activeCount`, ascending -- thresholds stack. */
internal fun activeThresholds(set: SetDefinition, activeCount: Int): List<Int> =
    set.thresholds.keys.filter { it <= activeCount }.sorted()

/**
 * WP-5.3: [set]'s stat contribution for [activeCount] active (non-inert) equipped members -- the UNION
 * of every satisfied threshold's [StatEffect] entries (non-stat effects on a threshold are stored on
 * the definition but not consumed here; this is a `StatProvider`, so only stats flow through it -- a
 * future WP may wire damage-stage/triggered set effects elsewhere, same seam shape 1.5b left for items).
 * `activeCount` also seeds the [ScalingContext.level] a `StatEffect.amount` evaluates against (so a
 * content author CAN use `linear`/`linear_with_base` scaling keyed to member count; flat amounts --
 * the shipped sets' choice -- ignore it entirely).
 */
internal fun setStatsFor(set: SetDefinition, activeCount: Int): List<StatModifier> {
    val out = ArrayList<StatModifier>()
    for (count in activeThresholds(set, activeCount)) {
        val scaleCtx = SetScalingContext(count)
        for (eff in set.thresholds.getValue(count)) {
            if (eff !is StatEffect) continue
            val amount = eff.amount.eval(scaleCtx)
            out += StatModifier(eff.stat, amount, eff.operation, ModifierSource(SourceType.ITEM, set.key.id))
        }
    }
    return out
}

/**
 * WP-lore: the [SetLoreInfo] block `PacketItemRenderer` shows for [itemKey] to [player] -- the owning
 * set's display name, total member count, the viewer's current ACTIVE (non-inert) member count, and the
 * threshold -> effects map -- or `null` when the item belongs to no set. Deliberately reuses the SAME
 * [equippedDefs] + [requirementStateFor] + [equippedSetCount] path [SetStatProvider.provideStats] uses,
 * so the lore's active count can never disagree with the count that actually granted the stat bonus
 * (an inert / requirement-failing piece is excluded from both identically -- the inert-exclusion rule
 * lives ONLY in [equippedSetCount], never duplicated here). `activeCount` mirrors what
 * [dev.willram.ramrpg.api.items.LoreRender.setBonus] then uses to light thresholds.
 */
internal fun setLoreInfoFor(
    player: Player,
    itemKey: ItemKey,
    items: ItemInstanceService,
    defs: ItemDefinitionRegistry,
    sets: SetRegistry,
    requirementServices: ItemRequirementServices?,
): SetLoreInfo? {
    val set = sets.all().firstOrNull { itemKey in it.members } ?: return null
    val equipped = equippedDefs(player, items, defs)
    val state = requirementStateFor(player, requirementServices)
    return SetLoreInfo(
        displayName = set.displayName,
        totalMembers = set.members.size,
        activeCount = equippedSetCount(equipped, state, set),
        thresholds = set.thresholds,
    )
}

private fun equippedDefs(
    player: Player,
    items: ItemInstanceService,
    defs: ItemDefinitionRegistry,
): List<Pair<ItemDefinition, ItemInstanceData>> {
    val eq = player.equipment
    val stacks = listOf(eq.itemInMainHand, eq.itemInOffHand, eq.helmet, eq.chestplate, eq.leggings, eq.boots)
    val out = ArrayList<Pair<ItemDefinition, ItemInstanceData>>(stacks.size)
    for (s in stacks) {
        if (s.type.isAir) continue
        val data = items.identify(s) ?: continue
        val def = defs.get(data.identity.key) ?: continue
        out += def to data
    }
    return out
}

class SetStatProvider(
    private val items: ItemInstanceService,
    private val defs: ItemDefinitionRegistry,
    private val sets: SetRegistry,
    /**
     * WP-5.3: unlike the four WP-2.1c providers (whose call sites live in `RamRPG.kt`, frozen by B5),
     * this provider is constructed entirely inside `SetModule` -- a brand-new registration site this WP
     * owns -- so `SetModule` always passes a REAL [ItemRequirementServices]. Still optional (defaulted
     * null) purely so pure tests can omit it and get the same fail-closed [requirementStateFor] default
     * every other item-based provider falls back to.
     */
    private val requirementServices: ItemRequirementServices? = null,
) : StatProvider {
    override fun provideStats(ctx: StatContext, output: MutableList<StatModifier>) {
        val equipped = equippedDefs(ctx.player, items, defs)
        if (equipped.isEmpty()) return
        val state = requirementStateFor(ctx.player, requirementServices)
        for (set in sets.all()) {
            val count = equippedSetCount(equipped, state, set)
            if (count == 0) continue
            output += setStatsFor(set, count)
        }
    }
}

private class SetScalingContext(override val level: Int) : ScalingContext {
    override fun statValue(key: StatKey): Double = 0.0
    override fun extra(key: String): Double? = null
}
