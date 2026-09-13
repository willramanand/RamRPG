/**
 * WP-3.1e: the PURE outcome-RUNTIME seam. The generic craft engine (WP-3.1b
 * [dev.willram.ramrpg.core.services.CraftEngine] / [dev.willram.ramrpg.core.services.CraftingServiceImpl])
 * already APPLIES every [dev.willram.ramrpg.api.crafting.RecipeOutcome] through its one
 * [dev.willram.ramrpg.api.crafting.OutcomePlan.Modify] / [dev.willram.ramrpg.api.crafting.OutcomePlan.Create]
 * arm -- but it applies the RAW plan: upgrades bump `upgradeLevel` with no cap and no chance of failure,
 * socket cuts append past any limit, and every cost is the STATIC `.conf` cost. The three per-kind rules
 * the raw plan does not model already live as PURE, already-tested helpers ([UpgradeOutcomes],
 * [SocketOutcomes], [ReforgeOutcomes]); this file is the ONE place that consults them.
 *
 * [effectiveCraft] is a DISPATCH TABLE keyed by outcome kind -- NOT a set of bespoke listener branches.
 * It reuses the pure helpers to turn a planned outcome into an [EffectiveCraft]: the rarity-scaled (and,
 * for a failed upgrade, failure-fraction) [cost], the actual [resultData] instance to write (capped /
 * failure-aware), the [status], and -- for the kinds with player-visible semantics -- the
 * [message] the live runtime relays. Every arm that adds no rule ([RecipeOutcome.Repair],
 * [RecipeOutcome.InsertGem], [RecipeOutcome.RemoveGem], [RecipeOutcome.Enchant], and the fresh-item
 * [RecipeOutcome.NewItem] / [RecipeOutcome.Transmute]) falls through [passthrough] to the plan result +
 * conf cost unchanged, so this is a strict, additive refinement of the engine, never a fork of it.
 *
 * PURITY. Everything here is a pure function of a [RecipeOutcome], the pre-computed
 * [dev.willram.ramrpg.api.crafting.OutcomePlan], the target [ItemInstanceData], its [Rarity] and the craft
 * [seed] -- no Bukkit, no live server, no [org.bukkit.entity.Player]. The one non-primitive result field,
 * [EffectiveCraft.message], is an Adventure translatable [Component], which the codebase's pure crafting
 * helpers ([UpgradeMessages] / [SocketOutcomes.message]) already build off-server. So [CraftRuntimeTest]
 * asserts the whole cap/failure/cost model with no server and no mocked menu. See
 * `docs/design/3.1e-crafting-runtime.md`.
 */
package dev.willram.ramrpg.core.crafting

import dev.willram.ramrpg.api.crafting.CraftFailure
import dev.willram.ramrpg.api.crafting.OutcomePlan
import dev.willram.ramrpg.api.crafting.RecipeCost
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.RarityRules
import net.kyori.adventure.text.Component

object CraftRuntime {

    /** The outcome-runtime verdict for one craft attempt. */
    enum class CraftStatus {
        /** The craft happened as intended: reagents consumed, [EffectiveCraft.resultData] written, [EffectiveCraft.cost] charged. */
        SUCCESS,

        /**
         * The attempt HAPPENED but rolled a failure (only an upgrade above the safe threshold): reagents
         * ARE consumed and the (reduced) [EffectiveCraft.cost] charged, but [EffectiveCraft.resultData] is
         * the target UNCHANGED. Still a [dev.willram.ramrpg.api.crafting.CraftResult.Success] to the menu,
         * so the escrow is drained -- see the design note's escrow-interaction section.
         */
        FAILURE,

        /**
         * The craft was refused BEFORE it could start (upgrade at cap, socket cut over the slot cap): the
         * caller returns [dev.willram.ramrpg.api.crafting.CraftResult.Failure] so NOTHING is consumed. The
         * blocking reason is in [EffectiveCraft.rejection].
         */
        REJECTED,
    }

    /**
     * The pure result the live runtime applies.
     *
     * @property status which of the three outcomes above.
     * @property cost the [RecipeCost] to actually charge -- rarity-scaled where the kind scales, the
     *   failure fraction for a failed upgrade, or the conf cost for a passthrough kind.
     * @property resultData the instance to WRITE for a Modify outcome (capped / failure-aware), or `null`
     *   for a fresh-item outcome (the caller keeps the engine's [OutcomePlan.Create] application).
     * @property rejection the blocking [CraftFailure] iff [status] is [CraftStatus.REJECTED], else `null`.
     * @property message a player-facing translatable [Component] the runtime relays (upgrade
     *   success/failure/at-cap, socket cut over cap), or `null` when the menu's generic result line suffices.
     */
    data class EffectiveCraft(
        val status: CraftStatus,
        val cost: RecipeCost,
        val resultData: ItemInstanceData?,
        val rejection: CraftFailure? = null,
        val message: Component? = null,
    )

    /**
     * The per-kind dispatch. [plan] is the engine's already-computed [OutcomePlan] for [outcome] (so a
     * passthrough / socket / reforge arm reuses it verbatim rather than re-deriving the write); [target] is
     * the instance being transformed (`null` for a fresh-item outcome); [rarity] is that target's rarity
     * (the caller resolves it from the item definition, defaulting when there is no target); [confCost] is
     * the recipe's static [RecipeCost]; [seed] drives the deterministic upgrade-failure roll.
     */
    fun effectiveCraft(
        outcome: RecipeOutcome,
        plan: OutcomePlan,
        target: ItemInstanceData?,
        rarity: Rarity,
        confCost: RecipeCost,
        seed: Long,
    ): EffectiveCraft = when (outcome) {
        is RecipeOutcome.UpgradeInput -> upgrade(outcome, target, rarity, seed)
        is RecipeOutcome.AddSocket -> addSocket(outcome, plan, target, rarity)
        is RecipeOutcome.Reforge -> EffectiveCraft(CraftStatus.SUCCESS, ReforgeOutcomes.reforgeCost(rarity), modifyResult(plan))
        else -> passthrough(plan, confCost)
    }

    // -- Upgrade (cap + failure + rarity-scaled cost, via UpgradeOutcomes) -----------------------------

    private fun upgrade(outcome: RecipeOutcome.UpgradeInput, target: ItemInstanceData?, rarity: Rarity, seed: Long): EffectiveCraft {
        // Defensive: a requiresTargetItem outcome only reaches here with a target (the planner gates
        // NO_TARGET_ITEM), but stay total so the dispatch is pure and independently testable.
        target ?: return EffectiveCraft(CraftStatus.REJECTED, RecipeCost.FREE, null, CraftFailure.NO_TARGET_ITEM)

        // At cap -> REJECT before any consume, so the menu keeps the reagents. The item is already maxed.
        if (UpgradeOutcomes.isAtCap(target.upgradeLevel, rarity)) {
            return EffectiveCraft(CraftStatus.REJECTED, RecipeCost.FREE, null, CraftFailure.INVALID_TARGET, UpgradeMessages.atCap())
        }

        val from = target.upgradeLevel
        val reached = UpgradeOutcomes.cappedTargetLevel(from, outcome.upgradeLevels, rarity)
        val resolution = UpgradeOutcomes.resolve(target, outcome.upgradeLevels, rarity, seed)
        return if (resolution.succeeded) {
            EffectiveCraft(
                status = CraftStatus.SUCCESS,
                // Money is the full rarity-scaled gold UpgradeOutcomes computed; the XP-level half reuses
                // the existing RarityRules ladder so both halves scale on the target's rarity.
                cost = RecipeCost(money = resolution.goldCharged, experienceLevels = RarityRules.upgradeXpCost(from, reached, rarity)),
                resultData = resolution.result, // capped upgraded instance
                message = UpgradeMessages.success(),
            )
        } else {
            EffectiveCraft(
                status = CraftStatus.FAILURE,
                // A failed attempt burns the reagents and only the failure gold fraction; no XP levels.
                cost = RecipeCost(money = resolution.goldCharged, experienceLevels = 0),
                resultData = resolution.result, // the UNCHANGED target
                message = UpgradeMessages.failure(),
            )
        }
    }

    // -- Add socket (slot cap + scaled cut cost, via SocketOutcomes) -----------------------------------

    private fun addSocket(outcome: RecipeOutcome.AddSocket, plan: OutcomePlan, target: ItemInstanceData?, rarity: Rarity): EffectiveCraft {
        target ?: return EffectiveCraft(CraftStatus.REJECTED, RecipeCost.FREE, null, CraftFailure.NO_TARGET_ITEM)
        SocketOutcomes.validateAddSocket(target.sockets, outcome.count, rarity)?.let { error ->
            // Over the per-rarity slot cap (or a bad count) -> REJECT, nothing consumed.
            return EffectiveCraft(CraftStatus.REJECTED, RecipeCost.FREE, null, CraftFailure.INVALID_TARGET, SocketOutcomes.message(error))
        }
        return EffectiveCraft(
            status = CraftStatus.SUCCESS,
            cost = SocketOutcomes.addSocketCost(existingSlots = target.sockets.size),
            resultData = modifyResult(plan), // the plan's appended-sockets instance
        )
    }

    // -- Passthrough (Repair / InsertGem / RemoveGem / Enchant / NewItem / Transmute) ------------------

    /** No cap, no failure, no scaling: the plan result (for a Modify) + the conf cost, exactly as the engine already does. */
    private fun passthrough(plan: OutcomePlan, confCost: RecipeCost): EffectiveCraft =
        EffectiveCraft(CraftStatus.SUCCESS, confCost, modifyResult(plan))

    /** The instance a [OutcomePlan.Modify] writes, or `null` for a fresh-item [OutcomePlan.Create]. */
    private fun modifyResult(plan: OutcomePlan): ItemInstanceData? = (plan as? OutcomePlan.Modify)?.result
}
