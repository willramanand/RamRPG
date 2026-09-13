package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.core.crafting.UpgradeOutcomes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-3.3a: the safe-failure model. A failed upgrade consumes the reagents and only a fraction of the
 * gold, but leaves the item exactly as it was -- never losing, downgrading or corrupting it. Also pins
 * the failure-chance curve (safe below the threshold, linear then capped above) and its determinism.
 * Pure: asserts UpgradeOutcomes + ItemInstanceData, no server.
 */
class UpgradeFailureConsumesMaterialsOnlyTest {

    private val blade = ItemKey.of("ramrpg", "netherite_sword")

    private fun instance(upgradeLevel: Int, quality: Double = 0.7, durability: Int = 400) =
        ItemInstanceData(
            identity = ItemIdentity(blade),
            upgradeLevel = upgradeLevel,
            quality = quality,
            maxDurability = 500,
            durability = durability,
        )

    // ---- Failure-chance curve --------------------------------------------------------------------

    @Test
    fun `upgrades in the safe zone never fail`() {
        for (level in 0..UpgradeOutcomes.SAFE_UPGRADE_LEVEL) {
            assertEquals(0.0, UpgradeOutcomes.failureChance(level), "level $level should be safe")
        }
    }

    @Test
    fun `failure chance rises linearly above the threshold then caps`() {
        assertEquals(0.08, UpgradeOutcomes.failureChance(6), 1e-9)   // 0.08 * 1
        assertEquals(0.40, UpgradeOutcomes.failureChance(10), 1e-9)  // 0.08 * 5
        assertEquals(0.75, UpgradeOutcomes.failureChance(15), 1e-9)  // 0.08 * 10 -> capped at 0.75
        assertEquals(UpgradeOutcomes.MAX_FAILURE_CHANCE, UpgradeOutcomes.failureChance(50), 1e-9)
    }

    @Test
    fun `failure chance is monotonic non-decreasing`() {
        var prev = 0.0
        for (level in 0..30) {
            val chance = UpgradeOutcomes.failureChance(level)
            assertTrue(chance >= prev, "failure chance regressed at $level")
            prev = chance
        }
    }

    @Test
    fun `isFailure is deterministic and always false in the safe zone`() {
        // Same (level, seed) -> same decision.
        assertEquals(UpgradeOutcomes.isFailure(9, 42L), UpgradeOutcomes.isFailure(9, 42L))
        // A zero failure chance can never roll a failure, for any seed.
        for (seed in listOf(0L, 1L, -1L, 123456789L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            assertFalse(UpgradeOutcomes.isFailure(UpgradeOutcomes.SAFE_UPGRADE_LEVEL, seed))
        }
    }

    // ---- A failed attempt: materials only --------------------------------------------------------

    @Test
    fun `a failed upgrade consumes materials but spares the item and refunds most of the gold`() {
        val before = instance(upgradeLevel = 6)
        val fullGold = UpgradeOutcomes.upgradeGoldCost(6, 7, Rarity.RARE) // step to level 7, rarity-weighted

        val failed = UpgradeOutcomes.resolve(before, upgradeLevels = 1, rarity = Rarity.RARE, succeeded = false)

        assertFalse(failed.succeeded)
        assertFalse(failed.atCap)
        assertTrue(failed.materialsConsumed, "a failed upgrade must still consume the reagents")
        assertEquals(before, failed.result, "the item must be returned UNCHANGED on failure")
        assertEquals(before.upgradeLevel, failed.result.upgradeLevel, "the upgrade level must not move on failure")
        assertTrue(failed.goldCharged < fullGold, "a failed upgrade must not charge the full cost")
        assertEquals(fullGold * UpgradeOutcomes.FAILURE_GOLD_FRACTION, failed.goldCharged, 1e-9)
    }

    @Test
    fun `a successful upgrade raises the level and charges the full gold`() {
        val before = instance(upgradeLevel = 6)
        val fullGold = UpgradeOutcomes.upgradeGoldCost(6, 7, Rarity.RARE)

        val ok = UpgradeOutcomes.resolve(before, upgradeLevels = 1, rarity = Rarity.RARE, succeeded = true)

        assertTrue(ok.succeeded)
        assertTrue(ok.materialsConsumed)
        assertEquals(7, ok.result.upgradeLevel)
        assertNotEquals(before.upgradeLevel, ok.result.upgradeLevel)
        assertEquals(fullGold, ok.goldCharged, 1e-9)
        // Nothing but the upgrade level changes.
        assertEquals(before.copy(upgradeLevel = 7), ok.result)
    }
}
