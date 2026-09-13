package dev.willram.ramrpg.core.services

import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.isInert
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatContext
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.api.stats.StatProvider
import dev.willram.ramrpg.core.listeners.ItemRequirementServices
import dev.willram.ramrpg.core.listeners.requirementStateFor

/**
 * WP-2.1c: [def]'s reforge bonus contribution, or empty when [def]/[data] [isInert] against [state] --
 * the SAME shared check every item-based provider consults. Extracted from
 * [ReforgeStatProvider.provideStats] so it is off-server testable without a live `Player`/`ItemStack`.
 */
internal fun reforgeStatsFor(
    def: ItemDefinition,
    data: ItemInstanceData,
    state: ItemRequirementState,
    reforges: ReforgeRegistry,
): List<StatModifier> {
    if (def.isInert(data, state)) return emptyList()
    val refKey = data.reforge ?: return emptyList()
    val rDef = reforges.get(refKey) ?: return emptyList()
    val out = ArrayList<StatModifier>()
    for ((stat, amt) in rDef.universal) {
        out += StatModifier(stat, amt, ModifierOperation.ADD, ModifierSource(SourceType.REFORGE, rDef.key.id))
    }
    for (cat in def.categories) {
        val perCat = rDef.bonusesByCategory[cat] ?: continue
        for ((stat, amt) in perCat) {
            out += StatModifier(stat, amt, ModifierOperation.ADD, ModifierSource(SourceType.REFORGE, rDef.key.id))
        }
    }
    return out
}

/**
 * The SINGLE stat path for reforges: reads [ItemInstanceData.reforge] off each equipped stack and folds
 * the matching [dev.willram.ramrpg.api.reforges.ReforgeDefinition]'s bonuses in via [reforgeStatsFor].
 *
 * WP-3.3c (reforge-as-a-recipe): reforging is now a normal crafting recipe -- a
 * [dev.willram.ramrpg.api.crafting.RecipeOutcome.Reforge] that the generic craft engine collapses to an
 * [dev.willram.ramrpg.api.crafting.OutcomePlan.Modify] and writes to [ItemInstanceData.reforge]. That
 * changes only HOW the field gets set (a recipe, not a bespoke command path); this provider is unchanged
 * and stays the ONLY place reforge stats are produced, so a reforge applied by a recipe yields exactly
 * the same stats it did before. See `docs/design/3.3c-reforge-costs.md`.
 */
class ReforgeStatProvider(
    private val items: ItemInstanceService,
    private val defs: ItemDefinitionRegistry,
    private val reforges: ReforgeRegistry,
    /** WP-2.1c: optional -- see [ItemRequirementServices]'s KDoc for why this can't be required yet. */
    private val requirementServices: ItemRequirementServices? = null,
) : StatProvider {
    override fun provideStats(ctx: StatContext, output: MutableList<StatModifier>) {
        val eq = ctx.player.equipment
        val stacks = listOf(eq.itemInMainHand, eq.itemInOffHand, eq.helmet, eq.chestplate, eq.leggings, eq.boots)
        for (s in stacks) {
            if (s.type.isAir) continue
            val data = items.identify(s) ?: continue
            if (data.reforge == null) continue
            val def = defs.get(data.identity.key) ?: continue
            output += reforgeStatsFor(def, data, requirementStateFor(ctx.player, requirementServices), reforges)
        }
    }
}
