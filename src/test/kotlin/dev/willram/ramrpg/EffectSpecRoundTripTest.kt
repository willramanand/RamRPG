package dev.willram.ramrpg

import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.effects.DamagePipelineEffect
import dev.willram.ramrpg.api.effects.EffectTrigger
import dev.willram.ramrpg.api.effects.InteractType
import dev.willram.ramrpg.api.effects.ScalingContext
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.effects.TriggeredEffect
import dev.willram.ramrpg.api.identity.EffectKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.core.config.specs.EffectSpec
import dev.willram.ramrpg.core.effects.BlockMatcherRegistry
import dev.willram.ramrpg.core.effects.BuiltinEffectActions
import dev.willram.ramrpg.core.effects.EffectActionRegistry
import dev.willram.ramrpg.core.effects.EffectConditionRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.StringReader

/**
 * WP-1.5b: each of the three HOCON effect forms parses into the right [Effect] subtype with the right
 * fields -- covering all three stat operations and all three scaling forms, the named-priority
 * `damage_stage`, and a `triggered` effect whose trigger, condition and action all resolve.
 */
class EffectSpecRoundTripTest {

    private val reg = EffectSpec.Registries(
        EffectActionRegistry(),
        EffectConditionRegistry(),
        BlockMatcherRegistry(),
    ).also { BuiltinEffectActions.registerAll(it.actions, it.conditions, it.matchers, RecordingScheduler()) }

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

    private fun one(text: String) = EffectSpec.one(hocon(text), EffectKey.of("ramrpg", "fx"), reg)

    @Test
    fun `stat ADD with flat scaling`() {
        val e = one(
            """
            type = stat
            stat = "ramrpg:strength"
            op = ADD
            amount { flat = 5.0 }
            """,
        )
        assertTrue(e is StatEffect)
        e as StatEffect
        assertEquals(StatKey.of("ramrpg", "strength"), e.stat)
        assertEquals(ModifierOperation.ADD, e.operation)
        assertEquals(5.0, e.amount.eval(ctx(3)), 1e-9)
    }

    @Test
    fun `stat MULTIPLY_BASE with linear scaling`() {
        val e = one(
            """
            type = stat
            stat = "ramrpg:crit_damage"
            op = MULTIPLY_BASE
            amount { linear = 10.0 }
            """,
        ) as StatEffect
        assertEquals(ModifierOperation.MULTIPLY_BASE, e.operation)
        assertEquals(40.0, e.amount.eval(ctx(4)), 1e-9)
    }

    @Test
    fun `stat MULTIPLY_TOTAL with linear_with_base scaling`() {
        val e = one(
            """
            type = stat
            stat = "ramrpg:health"
            op = MULTIPLY_TOTAL
            amount { linear_with_base { base = 2.0, per_level = 0.5 } }
            """,
        ) as StatEffect
        assertEquals(ModifierOperation.MULTIPLY_TOTAL, e.operation)
        assertEquals(4.0, e.amount.eval(ctx(4)), 1e-9)
    }

    @Test
    fun `damage_stage resolves a named priority and an action hook`() {
        val e = one(
            """
            type = damage_stage
            stage = ENCHANT_OFFENSE
            action = "ramrpg:ignite"
            params { ticks = 60 }
            """,
        )
        assertTrue(e is DamagePipelineEffect)
        e as DamagePipelineEffect
        assertEquals(DamagePriority.ENCHANT_OFFENSE, e.priority)
    }

    @Test
    fun `triggered resolves trigger, conditions and action`() {
        val e = one(
            """
            type = triggered
            trigger = "on_interact:SHIFT_RIGHT"
            conditions = ["ramrpg:sneaking"]
            action = "ramrpg:message"
            params { text = "<green>hi" }
            """,
        )
        assertTrue(e is TriggeredEffect)
        e as TriggeredEffect
        val trigger = e.trigger
        assertTrue(trigger is EffectTrigger.OnInteract)
        assertEquals(InteractType.SHIFT_RIGHT, (trigger as EffectTrigger.OnInteract).type)
        assertEquals(1, e.conditions.size)
    }

    @Test
    fun `triggered on_block_break carries its matcher and custom carries its id`() {
        val blockBreak = one(
            """
            type = triggered
            trigger = "on_block_break:ramrpg:ores"
            action = "ramrpg:message"
            params { text = "ore" }
            """,
        ) as TriggeredEffect
        assertTrue(blockBreak.trigger is EffectTrigger.OnBlockBreak)

        val custom = one(
            """
            type = triggered
            trigger = "custom:ramrpg:on_signal"
            action = "ramrpg:message"
            params { text = "signal" }
            """,
        ) as TriggeredEffect
        val trigger = custom.trigger
        assertTrue(trigger is EffectTrigger.Custom)
        assertEquals("ramrpg:on_signal", (trigger as EffectTrigger.Custom).key.toString())
    }
}
