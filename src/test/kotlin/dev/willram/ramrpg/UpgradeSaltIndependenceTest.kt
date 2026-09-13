package dev.willram.ramrpg

import dev.willram.ramrpg.api.crafting.QualityRoll
import dev.willram.ramrpg.core.crafting.UpgradeOutcomes
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-3.3a: the upgrade-FAILURE roll must be INDEPENDENT of QualityRoll's CRIT roll on the same craft
 * seed. Both use the same splitmix64 body, so if UpgradeOutcomes.FAILURE_SALT equalled
 * QualityRoll.CRIT_SALT the two uniforms would collide bit-for-bit and every crit craft whose roll is
 * below failureChance would ALSO be a guaranteed failure. The salt is deliberately distinct; this test
 * pins that -- a regression to a shared salt makes crit-but-not-failed seeds vanish. Deterministic over a
 * fixed seed range (not flaky); pure, no server.
 */
class UpgradeSaltIndependenceTest {

    @Test
    fun `failure roll is not perfectly correlated with the crit roll on the same seed`() {
        // skill 100 -> crit chance 0.25; target level 15 -> failure chance 0.75 (capped). Because
        // critChance < failChance, a SHARED salt (crit uniform == failure uniform) would force crit=true
        // => failure=true, so "crit and NOT failed" would be impossible (exactly 0). Independent salts
        // make it common (~0.25*0.25*n).
        val skill = 100
        val level = 15
        var critAndNotFail = 0
        var critAndFail = 0
        var notCritAndFail = 0
        var notCritAndNotFail = 0
        val n = 20_000L
        for (seed in 0L until n) {
            val crit = QualityRoll.isCritical(skill, seed)
            val fail = UpgradeOutcomes.isFailure(level, seed)
            when {
                crit && !fail -> critAndNotFail++
                crit && fail -> critAndFail++
                !crit && fail -> notCritAndFail++
                else -> notCritAndNotFail++
            }
        }
        // The shared-salt bug makes this exactly 0; independence expects ~1250 for n=20000.
        assertTrue(
            critAndNotFail > 200,
            "expected many crit-but-not-failed seeds (shared-salt regression?); got $critAndNotFail",
        )
        // All four joint outcomes must occur -> the two decisions are not one shared bit.
        assertTrue(
            critAndFail > 0 && notCritAndFail > 0 && notCritAndNotFail > 0,
            "all four crit/failure combinations should occur; got " +
                "cf=$critAndFail ncf=$notCritAndFail ncnf=$notCritAndNotFail",
        )
    }

    @Test
    fun `failure decision is deterministic per seed`() {
        for (seed in listOf(0L, 7L, -3L, 999L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            assertTrue(UpgradeOutcomes.isFailure(12, seed) == UpgradeOutcomes.isFailure(12, seed))
        }
    }
}
