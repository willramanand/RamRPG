package dev.willram.ramrpg

import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.RarityRules
import dev.willram.ramrpg.core.config.specs.RecipeIngredientKind
import dev.willram.ramrpg.core.config.specs.RecipeSpec
import dev.willram.ramrpg.core.crafting.UpgradeOutcomes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * WP-0.1 / WP-3.3a: the upgrade cost curves. The original XP-level curve (RarityRules.upgradeXpCost) is
 * kept; WP-3.3a adds the GOLD curve (UpgradeOutcomes.upgradeGoldCost) that a smithing upgrade also
 * charges, and asserts the shipped content/recipes/smithing_upgrade.conf quotes both costs on
 * upgrade_input outcomes. Pure: no server, the conf is parsed straight through the real RecipeSpec.
 */
class UpgradeCostTest {

    // ---- XP-level curve (pre-existing) ------------------------------------------------------------

    @Test
    fun `0 to 1 costs 3`() = assertEquals(3, RarityRules.upgradeXpCost(0, 1))

    @Test
    fun `0 to 3 costs 3+4+5 = 12`() = assertEquals(12, RarityRules.upgradeXpCost(0, 3))

    @Test
    fun `5 to 8 costs 8+9+10 = 27`() = assertEquals(27, RarityRules.upgradeXpCost(5, 8))

    @Test
    fun `same level zero cost`() = assertEquals(0, RarityRules.upgradeXpCost(5, 5))

    @Test
    fun `downgrade zero cost`() = assertEquals(0, RarityRules.upgradeXpCost(7, 3))

    // ---- Gold curve (WP-3.3a) --------------------------------------------------------------------

    @Test
    fun `gold per step is linear in the level reached`() {
        assertEquals(100.0, UpgradeOutcomes.goldForStep(1))
        assertEquals(300.0, UpgradeOutcomes.goldForStep(3))
        assertEquals(0.0, UpgradeOutcomes.goldForStep(0))
        assertEquals(0.0, UpgradeOutcomes.goldForStep(-2))
    }

    @Test
    fun `cumulative gold is triangular`() {
        assertEquals(100.0, UpgradeOutcomes.upgradeGoldCost(0, 1))       // 100
        assertEquals(600.0, UpgradeOutcomes.upgradeGoldCost(0, 3))       // 100+200+300
        assertEquals(2100.0, UpgradeOutcomes.upgradeGoldCost(5, 8))      // 600+700+800
    }

    @Test
    fun `gold same level or downgrade is free`() {
        assertEquals(0.0, UpgradeOutcomes.upgradeGoldCost(5, 5))
        assertEquals(0.0, UpgradeOutcomes.upgradeGoldCost(7, 3))
    }

    @Test
    fun `gold is rarity-weighted and rounded up`() {
        assertEquals(100.0, UpgradeOutcomes.upgradeGoldCost(0, 1, Rarity.COMMON))    // *1.0
        assertEquals(200.0, UpgradeOutcomes.upgradeGoldCost(0, 1, Rarity.RARE))      // *2.0
        assertEquals(800.0, UpgradeOutcomes.upgradeGoldCost(0, 1, Rarity.MYTHIC))    // *8.0
    }

    @Test
    fun `gold cost is monotonic non-decreasing by rarity`() {
        val costs = Rarity.entries.map { UpgradeOutcomes.upgradeGoldCost(0, 3, it) }
        for (i in 1 until costs.size) {
            assertTrue(costs[i] >= costs[i - 1], "gold cost regressed at $i: $costs")
        }
    }

    // ---- Shipped content quotes both costs on upgrade_input outcomes -----------------------------

    @Test
    fun `every shipped upgrade recipe is an upgrade_input with a positive gold cost`() {
        val recipes = childrenOf("content/recipes/smithing_upgrade.conf").map { RecipeSpec.deserialize(it) }
        assertTrue(recipes.isNotEmpty(), "smithing_upgrade.conf must define upgrade recipes")
        for (recipe in recipes) {
            assertTrue(
                recipe.outcome is RecipeOutcome.UpgradeInput,
                "recipe ${recipe.key} must be an upgrade_input outcome, was ${recipe.outcome}",
            )
            assertTrue(
                (recipe.outcome as RecipeOutcome.UpgradeInput).upgradeLevels >= 1,
                "recipe ${recipe.key} must raise the upgrade level by at least 1",
            )
            assertTrue(recipe.cost.money > 0.0, "recipe ${recipe.key} must charge gold")
            assertTrue(recipe.cost.experienceLevels > 0, "recipe ${recipe.key} must charge XP levels")
            // Reagents are item-id gated (3.2a seam), never bare vanilla materials.
            val tiered = recipe.inputs.filter { it.kind == RecipeIngredientKind.ITEM }
            assertTrue(
                tiered.isNotEmpty(),
                "recipe ${recipe.key} has no item-gated reagent -- a vanilla stack could complete it",
            )
        }
    }

    private companion object {
        fun childrenOf(resource: String): List<ConfigurationNode> {
            val stream = UpgradeCostTest::class.java.classLoader.getResourceAsStream(resource)
                ?: error("shipped resource not on the classpath: $resource")
            val root = stream.use {
                HoconConfigurationLoader.builder()
                    .source { BufferedReader(InputStreamReader(it, Charsets.UTF_8)) }
                    .build()
                    .load()
            }
            return root.childrenMap().values.toList()
        }
    }
}
