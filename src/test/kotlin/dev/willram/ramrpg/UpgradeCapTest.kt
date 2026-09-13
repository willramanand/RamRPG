package dev.willram.ramrpg

import dev.willram.ramrpg.api.crafting.OutcomePlan
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.plan
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.RarityRules
import dev.willram.ramrpg.core.config.specs.RecipeSpec
import dev.willram.ramrpg.core.crafting.UpgradeOutcomes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * WP-3.3a: an item's upgrade level can never exceed its per-rarity cap (RarityRules.upgradeCap). The pure
 * engine outcome (RecipeOutcome.plan) bumps upgradeLevel unconditionally; UpgradeOutcomes is the wrapper
 * that clamps to the cap and refuses an at-cap step. Also proves the shipped smithing_upgrade.conf feeds
 * that wrapper. Pure: no server.
 */
class UpgradeCapTest {

    private val blade = ItemKey.of("ramrpg", "diamond_sword")

    private fun instance(upgradeLevel: Int) =
        ItemInstanceData(identity = ItemIdentity(blade), upgradeLevel = upgradeLevel)

    // ---- Cap helpers ------------------------------------------------------------------------------

    @Test
    fun `cap delegates to RarityRules upgradeCap`() {
        for (rarity in Rarity.entries) {
            assertEquals(RarityRules.upgradeCap(rarity), UpgradeOutcomes.cap(rarity))
        }
    }

    @Test
    fun `isWithinCap allows up to the cap and rejects beyond it`() {
        val cap = UpgradeOutcomes.cap(Rarity.LEGENDARY) // 15
        assertTrue(UpgradeOutcomes.isWithinCap(cap - 1, 1, Rarity.LEGENDARY))
        assertTrue(UpgradeOutcomes.isWithinCap(0, cap, Rarity.LEGENDARY))
        assertFalse(UpgradeOutcomes.isWithinCap(cap, 1, Rarity.LEGENDARY))
        assertFalse(UpgradeOutcomes.isWithinCap(cap - 1, 3, Rarity.LEGENDARY))
    }

    @Test
    fun `isAtCap is true only at or above the cap`() {
        val cap = UpgradeOutcomes.cap(Rarity.COMMON) // 5
        assertFalse(UpgradeOutcomes.isAtCap(cap - 1, Rarity.COMMON))
        assertTrue(UpgradeOutcomes.isAtCap(cap, Rarity.COMMON))
        assertTrue(UpgradeOutcomes.isAtCap(cap + 3, Rarity.COMMON))
    }

    @Test
    fun `cappedTargetLevel clamps to the cap and never below the current level`() {
        val cap = UpgradeOutcomes.cap(Rarity.LEGENDARY) // 15
        assertEquals(cap, UpgradeOutcomes.cappedTargetLevel(cap - 1, 5, Rarity.LEGENDARY)) // 14 + 5 -> 15
        assertEquals(10, UpgradeOutcomes.cappedTargetLevel(7, 3, Rarity.LEGENDARY))        // ordinary
        assertEquals(9, UpgradeOutcomes.cappedTargetLevel(9, -4, Rarity.LEGENDARY))        // never below current
    }

    // ---- Resolution respects the cap -------------------------------------------------------------

    @Test
    fun `an at-cap item cannot be upgraded and nothing is consumed`() {
        val maxed = instance(UpgradeOutcomes.cap(Rarity.RARE)) // 10
        val res = UpgradeOutcomes.resolve(maxed, upgradeLevels = 1, rarity = Rarity.RARE, succeeded = true)

        assertTrue(res.atCap)
        assertFalse(res.succeeded)
        assertFalse(res.materialsConsumed, "an at-cap attempt consumes no materials")
        assertEquals(0.0, res.goldCharged)
        assertEquals(maxed.upgradeLevel, res.result.upgradeLevel, "level must not move past the cap")
    }

    @Test
    fun `a successful upgrade that overshoots the cap is clamped to it`() {
        val nearCap = instance(UpgradeOutcomes.cap(Rarity.EPIC) - 1) // 11 (cap 12)
        val res = UpgradeOutcomes.resolve(nearCap, upgradeLevels = 5, rarity = Rarity.EPIC, succeeded = true)

        assertTrue(res.succeeded)
        assertEquals(UpgradeOutcomes.cap(Rarity.EPIC), res.result.upgradeLevel) // clamped to 12
    }

    // ---- Shipped content feeds the cap wrapper ---------------------------------------------------

    @Test
    fun `the raw engine outcome bumps the level and the cap wrapper then bounds it`() {
        val recipe = childrenOf("content/recipes/smithing_upgrade.conf")
            .map { RecipeSpec.deserialize(it) }
            .first { it.outcome is RecipeOutcome.UpgradeInput }
        val upgrade = recipe.outcome as RecipeOutcome.UpgradeInput

        // The generic engine (via RecipeOutcome.plan) bumps unconditionally, with no cap awareness.
        val atCommonCap = instance(UpgradeOutcomes.cap(Rarity.COMMON)) // 5
        val rawPlan = upgrade.plan(atCommonCap, quality = 0.0) as OutcomePlan.Modify
        assertEquals(
            atCommonCap.upgradeLevel + upgrade.upgradeLevels, rawPlan.result.upgradeLevel,
            "the raw engine outcome bumps upgradeLevel with no cap check",
        )

        // The WP-3.3a wrapper is what refuses the over-cap step.
        val bounded = UpgradeOutcomes.resolve(atCommonCap, upgrade.upgradeLevels, Rarity.COMMON, succeeded = true)
        assertTrue(bounded.atCap, "the cap wrapper must refuse an at-cap upgrade the raw outcome would allow")
        assertEquals(atCommonCap.upgradeLevel, bounded.result.upgradeLevel)
    }

    private companion object {
        fun childrenOf(resource: String): List<ConfigurationNode> {
            val stream = UpgradeCapTest::class.java.classLoader.getResourceAsStream(resource)
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
