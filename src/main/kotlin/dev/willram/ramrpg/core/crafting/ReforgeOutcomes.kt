/**
 * WP-3.3c: PURE reforge helpers for reforging-as-a-recipe.
 *
 * Reforging is no longer a bespoke command/GUI path: it is a normal crafting [Recipe] whose outcome is a
 * [RecipeOutcome.Reforge] (see `content/recipes/reforge.conf` and `docs/design/3.3c-reforge-costs.md`).
 * The generic craft engine already APPLIES that outcome -- [RecipeOutcome.plan] collapses
 * `Reforge(key)` to an [OutcomePlan.Modify] that stamps [dev.willram.ramrpg.api.items.ItemInstanceData.reforge],
 * which [dev.willram.ramrpg.core.services.ReforgeStatProvider] then reads UNCHANGED. So this file adds no
 * engine code and no new stat path; it only holds the pure, server-free bits a reforge recipe needs:
 *
 *  - **Cost by rarity** ([reforgeCost] / [xpCost] / [moneyCost]) -- reuses the EXISTING, already-tested
 *    [RarityRules.reforgeXpCost] rarity ladder as the single source of truth for the XP-level cost, and
 *    derives a rarity-scaled money cost from it. A [Recipe.cost] in the `.conf` is a static baseline
 *    (a recipe cannot know the target item's rarity); a runtime that scales the charge to the target's
 *    rarity calls [reforgeCost].
 *  - **Seeded selection from a pool** ([pool] / [selectReforge] / [rollReforge] / [rolledOutcome]) -- the
 *    reforge pool is data (the set of reforge recipes / registered reforges); a "random reforge" picks one
 *    DETERMINISTICALLY from it for a given seed (splitmix64), so a preview and the real craft agree and a
 *    test can pin the choice. The single-reforge recipe grammar (`type = reforge, reforge = "..."`) needs
 *    no roll; these helpers serve a future random-reforge recipe/runtime without an api change.
 *  - **Display strings** ([appliedMessage] / [costLine]) -- pure Adventure [Component]s (translatable, so
 *    no server) a station's reforge preview/result surfaces; the player-facing reforge strings live in
 *    `lang/en_us.json` under `ramrpg.reforge.*`.
 *
 * Everything here is deterministic and off-server-testable (`ReforgeAsRecipeTest`). No Bukkit.
 */
package dev.willram.ramrpg.core.crafting

import dev.willram.ramrpg.api.crafting.OutcomePlan
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeCost
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.RarityRules
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import net.kyori.adventure.text.Component

object ReforgeOutcomes {

    /** Money charged per XP level of reforge cost, so money and XP track the ONE rarity ladder. */
    const val MONEY_PER_XP_LEVEL: Double = 25.0

    // -- Cost by rarity ---------------------------------------------------------------------------

    /** The reforge XP-level cost for [rarity] -- the existing, already-tested [RarityRules] ladder. */
    fun xpCost(rarity: Rarity): Int = RarityRules.reforgeXpCost(rarity)

    /** The reforge money cost for [rarity], derived from [xpCost] so both scale on the one ladder. */
    fun moneyCost(rarity: Rarity): Double = xpCost(rarity) * MONEY_PER_XP_LEVEL

    /**
     * The full rarity-scaled reforge [RecipeCost]. A runtime that charges by the TARGET item's rarity
     * uses this; a `.conf` recipe's static [Recipe.cost] is a fixed baseline (see the file header).
     */
    fun reforgeCost(rarity: Rarity): RecipeCost =
        RecipeCost(money = moneyCost(rarity), experienceLevels = xpCost(rarity))

    // -- Reforge pool + seeded selection ----------------------------------------------------------

    /**
     * The reforge pool: every registered reforge's [ReforgeKey], ordered DETERMINISTICALLY by id so a
     * seeded [selectReforge] is stable regardless of registration order.
     */
    fun pool(reforges: ReforgeRegistry): List<ReforgeKey> =
        reforges.all().map { it.key }.sortedBy { it.id.toString() }

    /**
     * Deterministically pick one [ReforgeKey] from [pool] for [seed] (splitmix64 -> index). Same
     * `(pool, seed)` -> same key; `null` for an empty pool. [pool] is sorted first so the choice does not
     * depend on caller ordering.
     */
    fun selectReforge(pool: List<ReforgeKey>, seed: Long): ReforgeKey? {
        val ordered = pool.distinct().sortedBy { it.id.toString() }
        if (ordered.isEmpty()) return null
        val index = Math.floorMod(mix(seed), ordered.size.toLong()).toInt()
        return ordered[index]
    }

    /** [selectReforge] over the whole registered [pool] of [reforges]. */
    fun rollReforge(reforges: ReforgeRegistry, seed: Long): ReforgeKey? =
        selectReforge(pool(reforges), seed)

    /** The [RecipeOutcome.Reforge] for a fixed [key] -- the exact outcome the `type = reforge` grammar builds. */
    fun outcomeFor(key: ReforgeKey): RecipeOutcome.Reforge = RecipeOutcome.Reforge(key)

    /**
     * A rolled [RecipeOutcome.Reforge] for a random-reforge recipe: seeded-select from [pool] and wrap it.
     * `null` when the pool is empty. The single-reforge grammar uses [outcomeFor] instead (no roll).
     */
    fun rolledOutcome(pool: List<ReforgeKey>, seed: Long): RecipeOutcome.Reforge? =
        selectReforge(pool, seed)?.let(::outcomeFor)

    // -- Display (pure translatable Components) ----------------------------------------------------

    /** "Reforged to {0}" -- the reforge-result message, with the applied reforge's display name. */
    fun appliedMessage(reforgeName: Component): Component =
        Component.translatable("ramrpg.reforge.applied", reforgeName)

    /** "Reforge cost: {0} XP levels" -- the reforge cost line for a station preview. */
    fun costLine(cost: RecipeCost): Component =
        Component.translatable("ramrpg.reforge.cost", Component.text(cost.experienceLevels))

    /** splitmix64 finalizer -- pure, deterministic, well-distributed (mirrors `QualityRoll`'s mixer). */
    private fun mix(seed: Long): Long {
        var z = seed + -0x61c8864680b583ebL          // 0x9E3779B97F4A7C15
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }
}
