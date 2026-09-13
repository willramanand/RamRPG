package dev.willram.ramrpg

import dev.willram.ramrpg.api.crafting.ConsumedInput
import dev.willram.ramrpg.api.crafting.CraftContext
import dev.willram.ramrpg.api.crafting.CraftFailure
import dev.willram.ramrpg.api.crafting.CraftPlanner
import dev.willram.ramrpg.api.crafting.CraftResult
import dev.willram.ramrpg.api.crafting.Ingredient
import dev.willram.ramrpg.api.crafting.IngredientCandidate
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeCost
import dev.willram.ramrpg.api.crafting.RecipeKey
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.RecipeOutcomeKind
import dev.willram.ramrpg.api.crafting.StationKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemRequirement
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.core.config.specs.RecipeSpec
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.StringReader

/**
 * WP-3.1a: [CraftPlanner] gates a [Recipe] against a pure [CraftContext] -- reusing WP-2.1a
 * [ItemRequirement.isMet] for the requirement gate -- and returns a pure [CraftResult] with no server.
 * A recipe with unmet requirement(s) fails; with them met it passes. Also covers the other gates
 * (station kind, inputs, funds, missing target) and confirms the reused-grammar requirement parse.
 */
class RecipeRequirementGatingTest {

    private class FakeState(
        private val skillLevels: Map<SkillKey, Int> = emptyMap(),
        private val statValues: Map<StatKey, Double> = emptyMap(),
    ) : ItemRequirementState {
        override fun skillLevel(skill: SkillKey): Int = skillLevels[skill] ?: 0
        override fun statValue(stat: StatKey): Double = statValues[stat] ?: 0.0
    }

    private val combat = SkillKey.of("ramrpg", "combat")
    private val station = StationKey.of("ramrpg", "smithing")
    private val steel = ItemKey.of("ramrpg", "steel_sword")
    private val whetstone = ItemKey.of("ramrpg", "whetstone")

    private fun recipe(
        inputs: List<Ingredient> = emptyList(),
        requirements: List<ItemRequirement> = listOf(ItemRequirement.SkillLevel(combat, 10)),
        cost: RecipeCost = RecipeCost.FREE,
        outcome: RecipeOutcome = RecipeOutcome.NewItem(steel),
    ) = Recipe(RecipeKey.of("ramrpg", "make_steel"), station, inputs, requirements, cost, outcome)

    @Test
    fun `unmet requirement gates the craft`() {
        val result = CraftPlanner.plan(recipe(), CraftContext(requirementState = FakeState()))
        assertInstanceOf(CraftResult.Failure::class.java, result)
        assertEquals(CraftFailure.UNMET_REQUIREMENT, (result as CraftResult.Failure).reason)
    }

    @Test
    fun `met requirement passes`() {
        val result = CraftPlanner.plan(
            recipe(),
            CraftContext(requirementState = FakeState(skillLevels = mapOf(combat to 10))),
        )
        assertInstanceOf(CraftResult.Success::class.java, result)
    }

    @Test
    fun `station that does not permit the outcome kind gates`() {
        val result = CraftPlanner.plan(
            recipe(),
            CraftContext(
                requirementState = FakeState(skillLevels = mapOf(combat to 10)),
                permittedKinds = setOf(RecipeOutcomeKind.REPAIR), // NewItem not allowed
            ),
        )
        assertEquals(CraftFailure.DISALLOWED_OUTCOME, (result as CraftResult.Failure).reason)
    }

    @Test
    fun `missing inputs gate, and matched inputs produce the consumption delta`() {
        val ungated = emptyList<ItemRequirement>()
        val withInput = recipe(inputs = listOf(Ingredient.Item(whetstone, count = 2)), requirements = ungated)

        val missing = CraftPlanner.plan(withInput, CraftContext(requirementState = FakeState()))
        assertEquals(CraftFailure.MISSING_INPUTS, (missing as CraftResult.Failure).reason)

        val supplied = CraftPlanner.plan(
            withInput,
            CraftContext(
                requirementState = FakeState(),
                inputs = listOf(IngredientCandidate(Material.FLINT, count = 5, itemKey = whetstone)),
            ),
        )
        assertInstanceOf(CraftResult.Success::class.java, supplied)
        assertEquals(listOf(ConsumedInput(count = 2, itemKey = whetstone)), (supplied as CraftResult.Success).consumption)
    }

    @Test
    fun `a tag or category match is recorded once, and a vanilla match is expressible by material`() {
        val ungated = emptyList<ItemRequirement>()

        // A MaterialTag ingredient matching a plain vanilla stack (no ItemKey) -> recorded by Material, once.
        val vanilla = CraftPlanner.plan(
            recipe(inputs = listOf(Ingredient.MaterialTag(setOf(Material.FLINT), count = 1)), requirements = ungated),
            CraftContext(requirementState = FakeState(), inputs = listOf(IngredientCandidate(Material.FLINT, count = 3))),
        ) as CraftResult.Success
        assertEquals(listOf(ConsumedInput(count = 1, material = Material.FLINT)), vanilla.consumption)

        // A Category ingredient matching an RPG stack -> recorded ONCE, by its ItemKey (never twice).
        val rpg = CraftPlanner.plan(
            recipe(inputs = listOf(Ingredient.Category(ItemCategory.SWORD, count = 1)), requirements = ungated),
            CraftContext(
                requirementState = FakeState(),
                inputs = listOf(IngredientCandidate(Material.DIAMOND_SWORD, count = 1, itemKey = whetstone, categories = setOf(ItemCategory.SWORD))),
            ),
        ) as CraftResult.Success
        assertEquals(listOf(ConsumedInput(count = 1, itemKey = whetstone)), rpg.consumption)
    }

    @Test
    fun `specific ingredients match before general ones so a solvable craft is not failed`() {
        val ungated = emptyList<ItemRequirement>()
        // Two IRON stacks: one is the specific RPG whetstone, one is plain vanilla iron. A recipe needing
        // BOTH "the whetstone" and "any iron" is solvable only if Item matches before MaterialTag.
        val withBoth = recipe(
            inputs = listOf(
                Ingredient.MaterialTag(setOf(Material.IRON_INGOT), count = 1), // general (listed first on purpose)
                Ingredient.Item(whetstone, count = 1),                          // specific
            ),
            requirements = ungated,
        )
        val result = CraftPlanner.plan(
            withBoth,
            CraftContext(
                requirementState = FakeState(),
                inputs = listOf(
                    IngredientCandidate(Material.IRON_INGOT, count = 1, itemKey = whetstone), // the specific stack
                    IngredientCandidate(Material.IRON_INGOT, count = 1),                       // plain iron
                ),
            ),
        )
        assertInstanceOf(CraftResult.Success::class.java, result)
        val consumption = (result as CraftResult.Success).consumption
        // Specific-first matching consumes the whetstone for the Item ingredient and plain iron for the tag.
        assertTrue(consumption.any { it.itemKey == whetstone && it.count == 1 })
        assertTrue(consumption.any { it.itemKey == null && it.material == Material.IRON_INGOT && it.count == 1 })
    }

    @Test
    fun `insufficient funds gate`() {
        val result = CraftPlanner.plan(
            recipe(requirements = emptyList(), cost = RecipeCost(money = 100.0)),
            CraftContext(requirementState = FakeState(), balance = 50.0),
        )
        assertEquals(CraftFailure.INSUFFICIENT_FUNDS, (result as CraftResult.Failure).reason)
    }

    @Test
    fun `a target-consuming outcome without a target gates`() {
        val result = CraftPlanner.plan(
            recipe(requirements = emptyList(), outcome = RecipeOutcome.Repair()),
            CraftContext(requirementState = FakeState(), target = null),
        )
        assertEquals(CraftFailure.NO_TARGET_ITEM, (result as CraftResult.Failure).reason)
    }

    @Test
    fun `recipe spec parses the reused requirement grammar`() {
        val spec = RecipeSpec.deserialize(
            hocon(
                """
                id = "ramrpg:make_steel"
                station = "ramrpg:smithing"
                inputs = []
                requirements = [ { type = skill_level, skill = "ramrpg:combat", level = 10 } ]
                outcome { type = new_item, item = "ramrpg:steel_sword" }
                """,
            ),
        )
        val req = spec.requirements.single()
        assertTrue(req is ItemRequirement.SkillLevel)
        req as ItemRequirement.SkillLevel
        assertEquals(combat, req.skill)
        assertEquals(10, req.level)
    }

    private fun hocon(text: String): ConfigurationNode =
        HoconConfigurationLoader.builder()
            .source { BufferedReader(StringReader(text.trimIndent())) }
            .build()
            .load()
}
