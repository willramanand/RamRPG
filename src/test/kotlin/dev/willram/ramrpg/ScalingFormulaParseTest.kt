package dev.willram.ramrpg

import dev.willram.ramrpg.api.effects.ScalingContext
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.identity.EffectKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.core.config.specs.EffectSpec
import dev.willram.ramrpg.core.effects.BlockMatcherRegistry
import dev.willram.ramrpg.core.effects.EffectActionRegistry
import dev.willram.ramrpg.core.effects.EffectConditionRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.StringReader

/**
 * WP-1.5b: the `amount` scaling form (`flat` / `linear` / `linear_with_base`, plus a scalar shorthand)
 * parses and, evaluated against a [ScalingContext], produces the expected number. Parsing goes through
 * the real [EffectSpec] (the scaling parser is private), wrapped in a `type=stat` effect.
 */
class ScalingFormulaParseTest {

    private val reg = EffectSpec.Registries(EffectActionRegistry(), EffectConditionRegistry(), BlockMatcherRegistry())

    private fun ctx(lvl: Int) = object : ScalingContext {
        override val level: Int = lvl
        override fun statValue(key: StatKey): Double = 0.0
        override fun extra(key: String): Double? = null
    }

    private fun hocon(text: String): ConfigurationNode =
        HoconConfigurationLoader.builder()
            .source { BufferedReader(StringReader(text.trimIndent())) }
            .build()
            .load()

    private fun statAmount(amountHocon: String): StatEffect {
        val node = hocon(
            """
            type = stat
            stat = "ramrpg:strength"
            $amountHocon
            """,
        )
        return EffectSpec.one(node, EffectKey.of("ramrpg", "t"), reg) as StatEffect
    }

    @Test
    fun `flat is level-independent`() {
        val amount = statAmount("amount { flat = 7.0 }").amount
        assertEquals(7.0, amount.eval(ctx(1)), 1e-9)
        assertEquals(7.0, amount.eval(ctx(9)), 1e-9)
    }

    @Test
    fun `linear scales by level`() {
        val amount = statAmount("amount { linear = 3.0 }").amount
        assertEquals(0.0, amount.eval(ctx(0)), 1e-9)
        assertEquals(12.0, amount.eval(ctx(4)), 1e-9)
    }

    @Test
    fun `linear_with_base adds base to a per-level term`() {
        val amount = statAmount("amount { linear_with_base { base = 2.0, per_level = 0.5 } }").amount
        assertEquals(2.0, amount.eval(ctx(0)), 1e-9)
        assertEquals(4.0, amount.eval(ctx(4)), 1e-9)
    }

    @Test
    fun `a bare number is shorthand for flat`() {
        val amount = statAmount("amount = 9.0").amount
        assertEquals(9.0, amount.eval(ctx(3)), 1e-9)
    }
}
