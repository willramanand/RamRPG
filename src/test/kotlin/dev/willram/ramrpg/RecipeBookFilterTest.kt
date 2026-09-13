package dev.willram.ramrpg

import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeKey
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.RecipeOutcomeKind
import dev.willram.ramrpg.api.crafting.StationKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemRequirement
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.core.menus.RecipeBookFilter
import dev.willram.ramrpg.core.menus.RecipeBookQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-3.1c: the PURE recipe-book filter -- which recipes show for a station and for a player's skill state
 * -- with no server, no [org.bukkit.entity.Player] and no [dev.willram.ramcore.menu.MenuView]. Everything
 * here is decided from [Recipe.station], the outcome kind, and [Recipe.requirements] via the WP-2.1a
 * [dev.willram.ramrpg.api.items.isMet].
 */
class RecipeBookFilterTest {

    private val smithing = StationKey.of("ramrpg", "smithing")
    private val enchanting = StationKey.of("ramrpg", "enchanting")
    private val mining = SkillKey.of("ramrpg", "mining")

    /** A requirement state reporting a single skill's level; every other skill is 0 and every stat 0. */
    private class SkillState(private val skill: SkillKey, private val lvl: Int) : ItemRequirementState {
        override fun skillLevel(skill: SkillKey): Int = if (skill == this.skill) lvl else 0
        override fun statValue(stat: StatKey): Double = 0.0
    }

    private fun recipe(
        id: String,
        station: StationKey,
        outcome: RecipeOutcome = RecipeOutcome.NewItem(ItemKey.of("ramrpg", id)),
        requirements: List<ItemRequirement> = emptyList(),
    ): Recipe = Recipe(
        key = RecipeKey.of("ramrpg", id),
        station = station,
        inputs = emptyList(),
        requirements = requirements,
        outcome = outcome,
    )

    @Test
    fun `station filter keeps only that station's recipes`() {
        val recipes = listOf(
            recipe("a", smithing),
            recipe("b", enchanting),
            recipe("c", smithing),
        )
        val out = RecipeBookFilter.visible(recipes, RecipeBookQuery(station = smithing), SkillState(mining, 0))
        assertEquals(listOf("a", "c"), out.map { it.key.id.value() })
    }

    @Test
    fun `null station returns every station's recipes`() {
        val recipes = listOf(recipe("a", smithing), recipe("b", enchanting))
        val out = RecipeBookFilter.visible(recipes, RecipeBookQuery(station = null), SkillState(mining, 0))
        assertEquals(setOf("a", "b"), out.map { it.key.id.value() }.toSet())
    }

    @Test
    fun `permitted-kinds gate keeps only recipes whose outcome kind is allowed`() {
        val recipes = listOf(
            recipe("newitem", smithing, RecipeOutcome.NewItem(ItemKey.of("ramrpg", "x"))),
            recipe("repair", smithing, RecipeOutcome.Repair()),
            recipe("enchant", smithing, RecipeOutcome.Enchant(dev.willram.ramrpg.api.identity.EnchantmentKey.of("ramrpg", "sharp"))),
        )
        val query = RecipeBookQuery(permittedKinds = setOf(RecipeOutcomeKind.NEW_ITEM, RecipeOutcomeKind.REPAIR))
        val out = RecipeBookFilter.visible(recipes, query, SkillState(mining, 0))
        assertEquals(setOf("newitem", "repair"), out.map { it.key.id.value() }.toSet())
    }

    @Test
    fun `canCraft is true only when every requirement is met`() {
        val gated = recipe("g", smithing, requirements = listOf(ItemRequirement.SkillLevel(mining, 10)))
        assertFalse(RecipeBookFilter.canCraft(gated, SkillState(mining, 5)))
        assertTrue(RecipeBookFilter.canCraft(gated, SkillState(mining, 10)))
        assertTrue(RecipeBookFilter.canCraft(recipe("free", smithing), SkillState(mining, 0)))
    }

    @Test
    fun `onlyCraftable drops recipes whose requirements are unmet`() {
        val recipes = listOf(
            recipe("free", smithing),
            recipe("locked", smithing, requirements = listOf(ItemRequirement.SkillLevel(mining, 10))),
        )
        val lowSkill = SkillState(mining, 5)
        assertEquals(
            listOf("free"),
            RecipeBookFilter.visible(recipes, RecipeBookQuery(onlyCraftable = true), lowSkill).map { it.key.id.value() },
        )
        // With the requirement met, both show.
        assertEquals(
            setOf("free", "locked"),
            RecipeBookFilter.visible(recipes, RecipeBookQuery(onlyCraftable = true), SkillState(mining, 10))
                .map { it.key.id.value() }.toSet(),
        )
    }

    @Test
    fun `visible sorts craftable-first then by recipe key`() {
        val recipes = listOf(
            recipe("zeta", smithing), // craftable, late alphabetically
            recipe("locked", smithing, requirements = listOf(ItemRequirement.SkillLevel(mining, 10))), // not craftable
            recipe("alpha", smithing), // craftable, early alphabetically
        )
        val out = RecipeBookFilter.visible(recipes, RecipeBookQuery(), SkillState(mining, 0))
        // craftable (alpha, zeta) sorted by key first, then the locked one last.
        assertEquals(listOf("alpha", "zeta", "locked"), out.map { it.key.id.value() })
    }

    @Test
    fun `an empty recipe set yields an empty book`() {
        assertTrue(RecipeBookFilter.visible(emptyList(), RecipeBookQuery(), SkillState(mining, 0)).isEmpty())
    }
}
