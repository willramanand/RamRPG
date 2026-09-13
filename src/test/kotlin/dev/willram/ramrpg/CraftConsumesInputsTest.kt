package dev.willram.ramrpg

import dev.willram.ramrpg.api.crafting.ConsumedInput
import dev.willram.ramrpg.api.crafting.CraftContext
import dev.willram.ramrpg.api.crafting.CraftPlanner
import dev.willram.ramrpg.api.crafting.CraftResult
import dev.willram.ramrpg.api.crafting.Ingredient
import dev.willram.ramrpg.api.crafting.IngredientCandidate
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeKey
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.StationKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.core.services.CraftEngine
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * WP-3.1b: the PURE consumption plan the generic engine removes from -- the [ConsumedInput] list folded to
 * an `ItemKey|Material -> count` delta by [CraftEngine.consumptionTotals]. Asserts the delta, never an
 * [org.bukkit.inventory.ItemStack] mutation. This is the single source of truth the runtime draws down;
 * it is never re-derived from [Recipe.inputs], so an RPG stack matched by a tag/category ingredient is
 * counted once (by key) and never double-consumed.
 */
class CraftConsumesInputsTest {

    private val station = StationKey.of("ramrpg", "smithing")
    private val steel = ItemKey.of("ramrpg", "steel_sword")
    private val whetstone = ItemKey.of("ramrpg", "whetstone")

    private class FakeState : ItemRequirementState {
        override fun skillLevel(skill: SkillKey) = 0
        override fun statValue(stat: StatKey) = 0.0
    }

    private fun recipe(inputs: List<Ingredient>) = Recipe(
        key = RecipeKey.of("ramrpg", "r"),
        station = station,
        inputs = inputs,
        requirements = emptyList(),
        outcome = RecipeOutcome.NewItem(steel),
    )

    @Test
    fun `folds repeated and mixed consumed inputs into per-target totals`() {
        val totals = CraftEngine.consumptionTotals(
            listOf(
                ConsumedInput(count = 2, itemKey = whetstone),
                ConsumedInput(count = 1, itemKey = whetstone),
                ConsumedInput(count = 3, material = Material.FLINT),
            ),
        )
        assertEquals(
            mapOf(
                CraftEngine.ConsumeTarget.Rpg(whetstone) to 3,
                CraftEngine.ConsumeTarget.Vanilla(Material.FLINT) to 3,
            ),
            totals,
        )
    }

    @Test
    fun `the planner's consumption folds to exactly the amounts the engine will remove`() {
        val r = recipe(
            listOf(
                Ingredient.Item(whetstone, count = 2),
                Ingredient.MaterialTag(setOf(Material.FLINT), count = 1),
            ),
        )
        val result = CraftPlanner.plan(
            r,
            CraftContext(
                requirementState = FakeState(),
                inputs = listOf(
                    IngredientCandidate(Material.IRON_INGOT, count = 5, itemKey = whetstone),
                    IngredientCandidate(Material.FLINT, count = 3),
                ),
            ),
        )
        result as CraftResult.Success
        assertEquals(
            mapOf(
                CraftEngine.ConsumeTarget.Rpg(whetstone) to 2,
                CraftEngine.ConsumeTarget.Vanilla(Material.FLINT) to 1,
            ),
            CraftEngine.consumptionTotals(result.consumption),
        )
    }

    @Test
    fun `a tag or category match on an RPG stack is counted once by its key, never twice`() {
        val r = recipe(listOf(Ingredient.Category(ItemCategory.SWORD, count = 1)))
        val result = CraftPlanner.plan(
            r,
            CraftContext(
                requirementState = FakeState(),
                inputs = listOf(
                    IngredientCandidate(
                        Material.DIAMOND_SWORD,
                        count = 1,
                        itemKey = whetstone,
                        categories = setOf(ItemCategory.SWORD),
                    ),
                ),
            ),
        )
        result as CraftResult.Success
        // Recorded once, by ItemKey -- not also as a Vanilla(DIAMOND_SWORD) removal.
        assertEquals(mapOf(CraftEngine.ConsumeTarget.Rpg(whetstone) to 1), CraftEngine.consumptionTotals(result.consumption))
    }
}
