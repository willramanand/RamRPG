/**
 * WP-3.3a: PURE smithing-upgrade economics -- the gold cost curve, the failure model and the per-rarity
 * upgrade cap that a live smithing-upgrade runtime (a later pipeline WP) consults AROUND the generic
 * craft engine. It adds nothing to the engine and touches no server.
 *
 * WHY THIS IS SEPARATE FROM THE ENGINE. WP-3.1b's `CraftingServiceImpl` already APPLIES a
 * [dev.willram.ramrpg.api.crafting.RecipeOutcome.UpgradeInput] unconditionally: 3.1a's
 * [dev.willram.ramrpg.api.crafting.plan] collapses it into a single
 * [dev.willram.ramrpg.api.crafting.OutcomePlan.Modify] that just bumps [ItemInstanceData.upgradeLevel],
 * and the engine writes that through its one `ModifyTarget` arm. The raw outcome models neither GOLD, a
 * chance of FAILURE, nor a CAP. This object supplies exactly those smithing-specific semantics as pure,
 * deterministic functions, so the runtime can decide -- before it charges anything or calls the engine --
 * how much gold a step costs, whether a high-level step fails (consuming the reagents but sparing the
 * item), and whether the step would cross the item's rarity cap.
 *
 * NO BESPOKE STAT PATH. An upgrade's gameplay effect is entirely the higher [ItemInstanceData.upgradeLevel]
 * this object stamps onto the instance ([resolve]); `EquipmentStatProvider.equipmentStatsFor` already
 * scales base stats and rolls by `1 + upgradeLevel * 0.05`, so no new stat hook is introduced.
 *
 * PURITY. Everything here is a pure function of numbers and an [ItemInstanceData] -- unit tested with no
 * live server and no Bukkit at all (not even `Material`). [UpgradeMessages] builds Adventure
 * [Component]s (off-server, and Adventure is the codebase's text layer, not Bukkit) for the runtime to
 * show the player.
 *
 * See `docs/design/3.3a-upgrade-costs.md` for the derivation and the tuned numbers.
 */
package dev.willram.ramrpg.core.crafting

import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.RarityRules
import net.kyori.adventure.text.Component
import kotlin.math.ceil
import kotlin.math.min

/**
 * The pure, server-free result of resolving one smithing-upgrade attempt -- what the runtime should
 * write and charge. Assert THIS in a test, never a mutated `ItemStack`.
 *
 * @property succeeded the attempt raised the item's upgrade level.
 * @property atCap the item was ALREADY at (or above) its [RarityRules.upgradeCap]; no step was possible,
 *   so nothing is consumed or charged -- the runtime rejects the craft.
 * @property result the instance to write back: upgraded (clamped to the cap) on success; the UNCHANGED
 *   input on failure or at-cap (a failed or blocked upgrade never loses or corrupts the item).
 * @property materialsConsumed whether the recipe's reagents ([dev.willram.ramrpg.api.crafting.Recipe.inputs])
 *   are drawn down. `true` on BOTH success and failure -- a failed upgrade still eats the materials; only
 *   an at-cap attempt (which never starts) consumes nothing.
 * @property goldCharged the gold to withdraw: the full [upgradeGoldCost] on success; only
 *   [FAILURE_GOLD_FRACTION] of it on failure (materials are lost but most of the gold is refunded); `0`
 *   at cap.
 */
data class UpgradeResolution(
    val succeeded: Boolean,
    val atCap: Boolean,
    val result: ItemInstanceData,
    val materialsConsumed: Boolean,
    val goldCharged: Double,
)

object UpgradeOutcomes {

    // -----------------------------------------------------------------------------------------------
    // Gold cost curve
    // -----------------------------------------------------------------------------------------------
    //
    // A companion to the XP-level curve already in RarityRules.upgradeXpCost: that charges vanilla XP
    // levels; this charges the RecipeCost.money (gold) a smithing upgrade also costs. Per-step cost is
    // LINEAR in the level being reached, so the CUMULATIVE cost of a run of upgrades grows quadratically
    // (triangular) -- late upgrades are the expensive ones, matching the failure ramp below.

    /** Gold for a single step, i.e. reaching upgrade level [toLevel] from `toLevel - 1`. */
    const val GOLD_PER_LEVEL: Double = 100.0

    /** Gold to reach [toLevel] from the level just below it. `0` for a non-positive level. */
    fun goldForStep(toLevel: Int): Double = if (toLevel <= 0) 0.0 else GOLD_PER_LEVEL * toLevel

    /** Total base gold to upgrade [from] -> [to] (sum of each step). `0` when [to] `<=` [from]. */
    fun upgradeGoldCost(from: Int, to: Int): Double {
        if (to <= from) return 0.0
        var total = 0.0
        for (n in (from + 1)..to) total += goldForStep(n)
        return total
    }

    /** Rarity-weighted [upgradeGoldCost], scaled by [RarityRules.upgradeCostMultiplier] and rounded up. */
    fun upgradeGoldCost(from: Int, to: Int, rarity: Rarity): Double =
        ceil(upgradeGoldCost(from, to) * RarityRules.upgradeCostMultiplier(rarity))

    // -----------------------------------------------------------------------------------------------
    // Failure model
    // -----------------------------------------------------------------------------------------------
    //
    // Roadmap 3.3: "failure chance above a threshold that consumes materials but not the item". Below the
    // safe threshold every upgrade succeeds; above it the chance rises linearly with the level being
    // attempted, capped so an upgrade is never a coin-flip worse than MAX_FAILURE_CHANCE. A failed
    // attempt burns the reagents and a small gold fee but leaves the item exactly as it was.

    /** Upgrades to this level or below NEVER fail (the safe zone). */
    const val SAFE_UPGRADE_LEVEL: Int = 5

    /** Failure chance added per level ABOVE [SAFE_UPGRADE_LEVEL]. */
    const val FAILURE_PER_LEVEL: Double = 0.08

    /** The ceiling on [failureChance] -- an upgrade is never worse than this. */
    const val MAX_FAILURE_CHANCE: Double = 0.75

    /** Fraction of the full gold cost still charged on a FAILED attempt (the rest is refunded). */
    const val FAILURE_GOLD_FRACTION: Double = 0.25

    /** Chance in `[0,1]` that reaching [toLevel] fails: `0` in the safe zone, linear then capped above. */
    fun failureChance(toLevel: Int): Double {
        if (toLevel <= SAFE_UPGRADE_LEVEL) return 0.0
        return min(FAILURE_PER_LEVEL * (toLevel - SAFE_UPGRADE_LEVEL), MAX_FAILURE_CHANCE)
    }

    /** Deterministic failure decision for reaching [toLevel] from a craft [seed]. Pure. */
    fun isFailure(toLevel: Int, seed: Long): Boolean = uniform(seed) < failureChance(toLevel)

    /** Gold actually charged when an attempt fails -- [FAILURE_GOLD_FRACTION] of the full cost. */
    fun goldCostOnFailure(fullGold: Double): Double = fullGold * FAILURE_GOLD_FRACTION

    // -----------------------------------------------------------------------------------------------
    // Cap
    // -----------------------------------------------------------------------------------------------
    //
    // The per-rarity ceiling lives in RarityRules.upgradeCap (COMMON 5 .. MYTHIC 20); an upgrade may
    // never push upgradeLevel past it. These helpers wrap that rule so both the runtime and the tests
    // read the cap from ONE source.

    /** The upgrade ceiling for [rarity] (delegates to [RarityRules.upgradeCap]). */
    fun cap(rarity: Rarity): Int = RarityRules.upgradeCap(rarity)

    /** `true` iff [currentLevel] is already at (or past) the [rarity] cap -- no further upgrade is possible. */
    fun isAtCap(currentLevel: Int, rarity: Rarity): Boolean = currentLevel >= cap(rarity)

    /** `true` iff adding [upgradeLevels] to [currentLevel] would stay within the [rarity] cap. */
    fun isWithinCap(currentLevel: Int, upgradeLevels: Int, rarity: Rarity): Boolean =
        currentLevel + upgradeLevels <= cap(rarity)

    /**
     * The level an attempt actually reaches: `currentLevel + upgradeLevels`, but never above the [rarity]
     * cap and never below [currentLevel] (a non-positive [upgradeLevels] is a no-op).
     */
    fun cappedTargetLevel(currentLevel: Int, upgradeLevels: Int, rarity: Rarity): Int =
        min(currentLevel + upgradeLevels, cap(rarity)).coerceAtLeast(currentLevel)

    // -----------------------------------------------------------------------------------------------
    // Resolution (pure)
    // -----------------------------------------------------------------------------------------------

    /**
     * Resolve one upgrade attempt with an EXPLICIT [succeeded] decision (the caller rolled it, or a test
     * fixes it). Applies the cap, computes the gold, and produces the [UpgradeResolution] the runtime
     * writes. On success the resulting instance's [ItemInstanceData.upgradeLevel] is
     * [cappedTargetLevel]; on failure it is the UNCHANGED [input].
     *
     * INTEGRATION CONTRACT. The generic craft engine's [dev.willram.ramrpg.api.crafting.OutcomePlan.Modify]
     * (from 3.1a's [dev.willram.ramrpg.api.crafting.plan] on `UpgradeInput`) bumps `upgradeLevel`
     * UNCONDITIONALLY, with no cap and no failure. The future smithing runtime MUST write
     * [UpgradeResolution.result] from THIS function, not the engine's raw `Modify` plan, or the rarity cap
     * and the failure model are bypassed (see UpgradeCapTest).
     */
    fun resolve(
        input: ItemInstanceData,
        upgradeLevels: Int,
        rarity: Rarity,
        succeeded: Boolean,
    ): UpgradeResolution {
        val current = input.upgradeLevel
        if (isAtCap(current, rarity)) {
            return UpgradeResolution(
                succeeded = false, atCap = true, result = input,
                materialsConsumed = false, goldCharged = 0.0,
            )
        }
        val target = cappedTargetLevel(current, upgradeLevels, rarity)
        val fullGold = upgradeGoldCost(current, target, rarity)
        return if (succeeded) {
            UpgradeResolution(
                succeeded = true, atCap = false, result = input.copy(upgradeLevel = target),
                materialsConsumed = true, goldCharged = fullGold,
            )
        } else {
            UpgradeResolution(
                succeeded = false, atCap = false, result = input,
                materialsConsumed = true, goldCharged = goldCostOnFailure(fullGold),
            )
        }
    }

    /**
     * Resolve one upgrade attempt, rolling the [isFailure] decision deterministically from [seed] against
     * the [cappedTargetLevel] being reached. Same `(input, upgradeLevels, rarity, seed)` -> same result.
     */
    fun resolve(
        input: ItemInstanceData,
        upgradeLevels: Int,
        rarity: Rarity,
        seed: Long,
    ): UpgradeResolution {
        val target = cappedTargetLevel(input.upgradeLevel, upgradeLevels, rarity)
        return resolve(input, upgradeLevels, rarity, succeeded = !isFailure(target, seed))
    }

    // splitmix64 finalizer of a salted seed -> uniform double in [0,1). Pure and well-distributed. The
    // salt is a DISTINCT nothing-up-my-sleeve constant -- deliberately NOT QualityRoll.CRIT_SALT
    // (0x9E3779B97F4A7C15) and NOT the splitmix64 increment below -- so the upgrade-failure roll is
    // INDEPENDENT of QualityRoll's crit/quality rolls on the same craft seed (see
    // UpgradeSaltIndependenceTest). A shared salt would collide the two uniforms bit-for-bit and make
    // every crit craft whose roll is below failureChance ALSO a guaranteed failure.
    private const val FAILURE_SALT: Long = 0x2545F4914F6CDD1DL // xorshift* multiplier; != CRIT_SALT/increment
    private fun uniform(seed: Long): Double {
        var z = (seed xor FAILURE_SALT) + -0x61c8864680b583ebL // + 0x9E3779B97F4A7C15
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        z = z xor (z ushr 31)
        return (z ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}

/**
 * WP-3.3a: the player-facing upgrade feedback strings, keyed into `lang/en_us.json`. Pure Adventure
 * [Component]s the live smithing runtime (a later WP) shows on success / failure / when quoting the cost
 * / when the item is already maxed. Kept here beside the logic so the string keys ship with the model.
 */
object UpgradeMessages {
    /** Shown when an upgrade succeeds. */
    fun success(): Component = Component.translatable("ramrpg.crafting.upgrade.success")

    /** Shown when an upgrade fails (materials consumed, item spared). */
    fun failure(): Component = Component.translatable("ramrpg.crafting.upgrade.failure")

    /**
     * A quoted upgrade cost: [gold] and [xpLevels] fill the `{0}`/`{1}` args of the message, so the
     * rendered string actually shows the numbers. Gold is whole ([upgradeGoldCost] is ceil'd).
     */
    fun cost(gold: Double, xpLevels: Int): Component =
        Component.translatable(
            "ramrpg.crafting.upgrade.cost",
            Component.text(gold.toLong()),
            Component.text(xpLevels),
        )

    /** Shown when the item is already at its rarity upgrade cap. */
    fun atCap(): Component = Component.translatable("ramrpg.crafting.upgrade.at_cap")
}
