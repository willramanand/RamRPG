package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatDefinition
import dev.willram.ramrpg.api.stats.StatModifier
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StatServiceTest {

    private val key = StatKey.of("test", "x")
    private val src = ModifierSource(SourceType.ITEM, dev.willram.ramcore.content.ContentId.of("test", "src"))

    @Test
    fun `aggregation order ADD then MULT_BASE then MULT_TOTAL`() {
        val mods = listOf(
            StatModifier(key, 10.0, ModifierOperation.ADD, src),
            StatModifier(key, 5.0, ModifierOperation.ADD, src),
            StatModifier(key, 0.5, ModifierOperation.MULTIPLY_BASE, src),
            StatModifier(key, 0.2, ModifierOperation.MULTIPLY_TOTAL, src),
        )
        val v = aggregate(mods, defaultBase = 0.0)
        // (0+10+5) * (1+0.5) * (1+0.2) = 15 * 1.5 * 1.2 = 27.0
        assertEquals(27.0, v, 0.0001)
    }

    @Test
    fun `clamp min and max`() {
        val def = StatDefinition(key, Component.text("x"), defaultBase = 0.0, min = 0.0, max = 10.0)
        val mods = listOf(StatModifier(key, 100.0, ModifierOperation.ADD, src))
        val v = aggregate(mods, defaultBase = def.defaultBase, min = def.min, max = def.max)
        assertEquals(10.0, v, 0.0001)
    }

    private fun aggregate(mods: List<StatModifier>, defaultBase: Double, min: Double? = null, max: Double? = null): Double {
        var add = defaultBase
        var mb = 0.0
        var mt = 1.0
        for (m in mods) {
            when (m.operation) {
                ModifierOperation.ADD -> add += m.amount
                ModifierOperation.MULTIPLY_BASE -> mb += m.amount
                ModifierOperation.MULTIPLY_TOTAL -> mt *= (1.0 + m.amount)
            }
        }
        var v = add * (1.0 + mb) * mt
        if (min != null) v = maxOf(v, min)
        if (max != null) v = minOf(v, max)
        return v
    }
}
