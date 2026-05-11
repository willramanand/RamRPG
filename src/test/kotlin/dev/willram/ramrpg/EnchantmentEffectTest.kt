package dev.willram.ramrpg

import dev.willram.ramrpg.api.effects.DamagePipelineEffect
import dev.willram.ramrpg.api.effects.ScalingContext
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.builtin.enchants.Critical
import dev.willram.ramrpg.builtin.enchants.Sharpness
import dev.willram.ramrpg.builtin.identity.RamStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnchantmentEffectTest {
    @Test
    fun `Sharpness produces DamagePipelineEffect at offense priority`() {
        val effects = Sharpness().effects(3)
        assertEquals(1, effects.size)
        val eff = effects.single()
        assertTrue(eff is DamagePipelineEffect)
    }

    @Test
    fun `Critical level scales crit damage stat 10 per level`() {
        val effects = Critical().effects(4)
        val stat = effects.filterIsInstance<StatEffect>().single()
        assertEquals(RamStats.CRIT_DAMAGE, stat.stat)
        val ctx = object : ScalingContext {
            override val level: Int = 4
            override fun statValue(key: StatKey): Double = 0.0
            override fun extra(key: String): Double? = null
        }
        assertEquals(40.0, stat.amount.eval(ctx), 0.0001)
    }
}
