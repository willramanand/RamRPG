package dev.willram.ramrpg

import dev.willram.ramrpg.api.crafting.Ingredient
import dev.willram.ramrpg.api.crafting.IngredientCandidate
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.core.config.specs.RecipeIngredientKind
import dev.willram.ramrpg.core.config.specs.RecipeSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.bukkit.Material
import java.io.BufferedReader
import java.io.StringReader

/**
 * WP-3.1a: each [Ingredient] variant matches/rejects a pure [IngredientCandidate] correctly, INCLUDING
 * count -- no live server. Also proves the HOCON [RecipeSpec] parses each ingredient form into the right
 * raw [dev.willram.ramrpg.core.config.specs.RecipeIngredientSpec] (WP-3.1b resolves those into these
 * live `Ingredient`s).
 */
class IngredientMatchingTest {

    private val whetstone = ItemKey.of("ramrpg", "whetstone")
    private val blade = ItemKey.of("ramrpg", "legendary_blade")

    // ---- Item ------------------------------------------------------------------------------------

    @Test
    fun `item matches its key with enough count`() {
        val ing = Ingredient.Item(whetstone, count = 2)
        assertTrue(ing.matches(IngredientCandidate(Material.FLINT, count = 2, itemKey = whetstone)))
        assertTrue(ing.matches(IngredientCandidate(Material.FLINT, count = 5, itemKey = whetstone)))
    }

    @Test
    fun `item rejects the wrong key and insufficient count`() {
        val ing = Ingredient.Item(whetstone, count = 2)
        assertFalse(ing.matches(IngredientCandidate(Material.FLINT, count = 2, itemKey = blade)))
        assertFalse(ing.matches(IngredientCandidate(Material.FLINT, count = 2, itemKey = null)))
        assertFalse(ing.matches(IngredientCandidate(Material.FLINT, count = 1, itemKey = whetstone)))
    }

    // ---- MaterialTag -----------------------------------------------------------------------------

    @Test
    fun `material tag matches any material in the set with enough count`() {
        val ing = Ingredient.MaterialTag(setOf(Material.FLINT, Material.COBBLESTONE), count = 3)
        assertTrue(ing.matches(IngredientCandidate(Material.FLINT, count = 3)))
        assertTrue(ing.matches(IngredientCandidate(Material.COBBLESTONE, count = 4)))
    }

    @Test
    fun `material tag rejects a material outside the set and low count`() {
        val ing = Ingredient.MaterialTag(setOf(Material.FLINT), count = 3)
        assertFalse(ing.matches(IngredientCandidate(Material.DIAMOND, count = 3)))
        assertFalse(ing.matches(IngredientCandidate(Material.FLINT, count = 2)))
    }

    // ---- Category --------------------------------------------------------------------------------

    @Test
    fun `category matches when the candidate carries that category`() {
        val ing = Ingredient.Category(ItemCategory.SWORD, count = 1)
        assertTrue(ing.matches(IngredientCandidate(Material.DIAMOND_SWORD, itemKey = blade, categories = setOf(ItemCategory.SWORD))))
        assertFalse(ing.matches(IngredientCandidate(Material.DIAMOND_PICKAXE, categories = setOf(ItemCategory.PICKAXE))))
        assertFalse(ing.matches(IngredientCandidate(Material.STICK, categories = emptySet())))
    }

    // ---- Predicate -------------------------------------------------------------------------------

    @Test
    fun `predicate requires every non-null constraint`() {
        // "a sword with quality >= 0.5 and upgrade level >= 1"
        val ing = Ingredient.Predicate(category = ItemCategory.SWORD, minQuality = 0.5, minUpgradeLevel = 1)
        assertTrue(
            ing.matches(
                IngredientCandidate(
                    Material.DIAMOND_SWORD, itemKey = blade,
                    categories = setOf(ItemCategory.SWORD), quality = 0.6, upgradeLevel = 2,
                ),
            ),
        )
        // quality too low
        assertFalse(
            ing.matches(
                IngredientCandidate(Material.DIAMOND_SWORD, categories = setOf(ItemCategory.SWORD), quality = 0.4, upgradeLevel = 2),
            ),
        )
        // wrong category
        assertFalse(
            ing.matches(IngredientCandidate(Material.DIAMOND_PICKAXE, categories = setOf(ItemCategory.PICKAXE), quality = 0.9, upgradeLevel = 5)),
        )
    }

    @Test
    fun `predicate is fail-closed when an instance field it needs is unknown`() {
        val needsQuality = Ingredient.Predicate(minQuality = 0.5)
        assertFalse(needsQuality.matches(IngredientCandidate(Material.DIAMOND_SWORD, quality = null)))

        val needsUpgrade = Ingredient.Predicate(minUpgradeLevel = 1)
        assertFalse(needsUpgrade.matches(IngredientCandidate(Material.DIAMOND_SWORD, upgradeLevel = null)))
    }

    @Test
    fun `predicate with only a count still enforces count`() {
        val ing = Ingredient.Predicate(count = 4)
        assertTrue(ing.matches(IngredientCandidate(Material.STICK, count = 4)))
        assertFalse(ing.matches(IngredientCandidate(Material.STICK, count = 3)))
    }

    // ---- Spec parsing (the HOCON-loadable raw form) ----------------------------------------------

    @Test
    fun `each ingredient type parses into the right raw spec`() {
        val spec = recipe(
            """
            id = "ramrpg:test_recipe"
            station = "ramrpg:smithing"
            inputs = [
              { type = item, item = "ramrpg:whetstone", count = 2 }
              { type = material, materials = ["FLINT", "cobblestone"], count = 1 }
              { type = category, category = "SWORD" }
              { type = predicate, item = "ramrpg:legendary_blade", category = "SWORD", min-quality = 0.5, min-upgrade-level = 1 }
            ]
            outcome { type = repair }
            """,
        )
        assertEquals(4, spec.inputs.size)

        val item = spec.inputs[0]
        assertEquals(RecipeIngredientKind.ITEM, item.kind)
        assertEquals(whetstone, item.itemKey)
        assertEquals(2, item.count)

        val material = spec.inputs[1]
        assertEquals(RecipeIngredientKind.MATERIAL, material.kind)
        assertEquals(setOf("FLINT", "COBBLESTONE"), material.materials) // upper-cased, ready for Material resolution

        val category = spec.inputs[2]
        assertEquals(RecipeIngredientKind.CATEGORY, category.kind)
        assertEquals(ItemCategory.SWORD, category.category)
        assertEquals(1, category.count) // default

        val predicate = spec.inputs[3]
        assertEquals(RecipeIngredientKind.PREDICATE, predicate.kind)
        assertEquals(blade, predicate.itemKey)
        assertEquals(ItemCategory.SWORD, predicate.category)
        assertEquals(0.5, predicate.minQuality)
        assertEquals(1, predicate.minUpgradeLevel)
    }

    private fun recipe(text: String): RecipeSpec = RecipeSpec.deserialize(hocon(text))

    private fun hocon(text: String): ConfigurationNode =
        HoconConfigurationLoader.builder()
            .source { BufferedReader(StringReader(text.trimIndent())) }
            .build()
            .load()
}
