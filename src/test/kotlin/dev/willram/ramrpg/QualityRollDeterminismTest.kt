package dev.willram.ramrpg

import dev.willram.ramrpg.api.crafting.QualityRoll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-3.1a: [QualityRoll] is pure and deterministic -- same `(skill, seed)` gives the identical `Double`,
 * different seeds diverge, higher skill raises expected (and, for a fixed seed, exact) quality, and the
 * result is always in `[0,1]`. Matches the formula documented in `docs/design/3.1a-crafting-model.md`.
 */
class QualityRollDeterminismTest {

    @Test
    fun `same skill and seed yields the identical double`() {
        assertEquals(QualityRoll(50, 12345L), QualityRoll(50, 12345L))
        assertEquals(QualityRoll(0, -999L), QualityRoll(0, -999L))
        assertEquals(QualityRoll(100, Long.MAX_VALUE), QualityRoll(100, Long.MAX_VALUE))
    }

    @Test
    fun `different seeds diverge`() {
        // Across a run of seeds the roll is not constant (splitmix64 spreads them).
        val values = (1L..25L).map { QualityRoll(50, it) }.toSet()
        assertTrue(values.size > 1, "seed must vary the roll")
        // And two specific distinct seeds differ.
        assertNotEquals(QualityRoll(50, 1L), QualityRoll(50, 999_999L))
    }

    @Test
    fun `higher skill yields higher quality for a fixed seed`() {
        val seed = 4242L
        val low = QualityRoll(10, seed)
        val high = QualityRoll(90, seed)
        assertTrue(high > low, "same seed, more skill -> strictly higher quality (got low=$low high=$high)")
    }

    @Test
    fun `expected quality rises with skill across many seeds`() {
        fun mean(skill: Int): Double = (1L..500L).sumOf { QualityRoll(skill, it) } / 500.0
        assertTrue(mean(90) > mean(10), "higher skill must raise mean quality")
    }

    @Test
    fun `result is always within zero and one`() {
        for (skill in intArrayOf(-50, 0, 25, 50, 100, 250)) {
            for (seed in longArrayOf(Long.MIN_VALUE, -7L, 0L, 1L, 42L, Long.MAX_VALUE)) {
                val q = QualityRoll(skill, seed)
                assertTrue(q in 0.0..1.0, "quality $q out of range for skill=$skill seed=$seed")
            }
        }
    }

    @Test
    fun `skill floor window matches the documented model`() {
        // SKILL_WEIGHT = 0.5: skill 0 -> [0, 0.5]; skill cap -> [0.5, 1.0], for every seed.
        for (seed in longArrayOf(-7L, 0L, 1L, 42L, 9_000L)) {
            assertTrue(QualityRoll(0, seed) <= 0.5 + 1e-9)
            assertTrue(QualityRoll(100, seed) >= 0.5 - 1e-9)
        }
    }

    @Test
    fun `critical chance is monotonic, bounded, and deterministic`() {
        assertEquals(QualityRoll.CRIT_BASE, QualityRoll.criticalChance(0), 1e-9)
        assertEquals(QualityRoll.CRIT_BASE + QualityRoll.CRIT_PER_SKILL, QualityRoll.criticalChance(100), 1e-9)
        assertTrue(QualityRoll.criticalChance(80) > QualityRoll.criticalChance(20))
        for (skill in intArrayOf(-10, 0, 50, 100, 500)) {
            assertTrue(QualityRoll.criticalChance(skill) in 0.0..1.0)
        }
        // Deterministic decision for a given (skill, seed).
        assertEquals(QualityRoll.isCritical(60, 77L), QualityRoll.isCritical(60, 77L))
    }

    @Test
    fun `critical roll varies with seed and is independent of the quality roll (CRIT_SALT works)`() {
        val skill = 50 // criticalChance = 0.15; quality range [0.25, 0.75]
        val seeds = 1L..3000L

        // Varies with seed: both true and false occur.
        val outcomes = seeds.map { QualityRoll.isCritical(skill, it) }.toSet()
        assertTrue(true in outcomes && false in outcomes, "crit must vary with seed")

        // Empirical rate ~ criticalChance: the salted roll is a real, working uniform (not degenerate).
        val rate = seeds.count { QualityRoll.isCritical(skill, it) }.toDouble() / 3000.0
        assertTrue(rate in 0.10..0.20, "crit rate $rate should sit near ${QualityRoll.criticalChance(skill)}")

        // Independence: if crit reused the SAME uniform as quality (no salt), a crit-true seed would force
        // a LOW quality (u < 0.15 => quality < 0.325 at skill 50). A crit-true seed whose quality exceeds
        // 0.5 can only happen because the crit roll is seeded independently via CRIT_SALT.
        assertTrue(
            seeds.any { QualityRoll.isCritical(skill, it) && QualityRoll(skill, it) > 0.5 },
            "a high-quality critical craft proves the crit roll is independent of the quality roll",
        )
    }
}
