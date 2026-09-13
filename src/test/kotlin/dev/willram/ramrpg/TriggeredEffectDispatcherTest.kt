package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.effects.BlockMatchers
import dev.willram.ramrpg.api.effects.Condition
import dev.willram.ramrpg.api.effects.EffectAction
import dev.willram.ramrpg.api.effects.EffectContext
import dev.willram.ramrpg.api.effects.EffectTrigger
import dev.willram.ramrpg.api.effects.InteractType
import dev.willram.ramrpg.api.identity.EffectKey
import dev.willram.ramrpg.core.effects.TriggerKind
import dev.willram.ramrpg.core.effects.TriggeredEffectDispatcher
import dev.willram.ramrpg.core.platform.Cancellable
import dev.willram.ramrpg.core.platform.PlatformScheduler
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-1.5b: the dispatcher routes each of the NINE triggers to its own action, honouring the trigger
 * parameters (interact type, block-matcher acceptance, custom id) and the conditions.
 *
 * We assert the DISPATCH / ROUTING DECISION at the pure layer ([TriggeredEffectDispatcher.dispatch]).
 * Firing the real Bukkit events (PlayerInteractEvent, EntityDeathEvent, ...) and the Folia thread hops
 * in [TriggeredEffectDispatcher.bind] needs a live server, so per the WP instruction we do NOT invent a
 * fake for that layer -- the routing core is what carries the correctness, and it is fully exercised
 * here with a null-entity [EffectContext] (no server, no mock).
 */
class TriggeredEffectDispatcherTest {

    /** dispatch() never schedules, so a trivial inline PlatformScheduler suffices for the constructor. */
    private object InlinePlatform : PlatformScheduler {
        private val noop = object : Cancellable { override fun cancel() {} }
        override fun runGlobal(task: Runnable) = task.run()
        override fun runAsync(task: Runnable) = task.run()
        override fun runForEntity(entity: Entity, task: Runnable) = task.run()
        override fun runForPlayer(player: Player, task: Runnable) = task.run()
        override fun runAtLocation(loc: Location, task: Runnable) = task.run()
        override fun runLaterGlobal(delayTicks: Long, task: Runnable) = task.run()
        override fun runForPlayerLater(player: Player, delayTicks: Long, task: Runnable) = task.run()
        override fun repeatGlobal(periodTicks: Long, task: () -> Unit): Cancellable = noop
        override fun repeatForEntity(e: Entity, periodTicks: Long, task: () -> Unit): Cancellable = noop
    }

    /** An entity-free context: enough to drive routing + conditions off-server. */
    private val ctx = object : EffectContext {
        override val source: LivingEntity? = null
        override val target: LivingEntity? = null
        override val player: Player? = null
        override val level: Int = 1
        override val damage: DamageContext? = null
        override val extra: MutableMap<String, Any> = mutableMapOf()
    }

    private val customId = ContentId.of("ramrpg", "on_signal")

    private fun fk(v: String) = EffectKey.of("ramrpg", v)

    /** Registers one effect per trigger; each action records its label into [fired]. */
    private fun dispatcherWithAllNine(fired: MutableSet<String>): TriggeredEffectDispatcher {
        val d = TriggeredEffectDispatcher(InlinePlatform)
        fun eff(label: String, trigger: EffectTrigger) =
            dev.willram.ramrpg.api.effects.TriggeredEffect(fk(label), trigger, action = EffectAction { fired.add(label) })
        d.register(eff("equip", EffectTrigger.OnEquip))
        d.register(eff("hit", EffectTrigger.OnHit))
        d.register(eff("hurt", EffectTrigger.OnHurt))
        d.register(eff("kill", EffectTrigger.OnKill))
        d.register(eff("interact", EffectTrigger.OnInteract(InteractType.RIGHT)))
        d.register(eff("block_break", EffectTrigger.OnBlockBreak(BlockMatchers.ANY)))
        d.register(eff("tick", EffectTrigger.Tick))
        d.register(eff("consume", EffectTrigger.OnConsume))
        d.register(eff("custom", EffectTrigger.Custom(customId)))
        return d
    }

    @Test
    fun `each of the nine triggers routes to exactly its own action`() {
        val fired = mutableSetOf<String>()
        val d = dispatcherWithAllNine(fired)

        fun only(expected: String, run: () -> Int) {
            fired.clear()
            val count = run()
            assertEquals(1, count, "exactly one action should fire for $expected")
            assertEquals(setOf(expected), fired, "only $expected should have fired")
        }

        only("equip") { d.dispatch(TriggerKind.EQUIP, ctx) }
        only("hit") { d.dispatch(TriggerKind.HIT, ctx) }
        only("hurt") { d.dispatch(TriggerKind.HURT, ctx) }
        only("kill") { d.dispatch(TriggerKind.KILL, ctx) }
        only("interact") { d.dispatch(TriggerKind.INTERACT, ctx, interactType = InteractType.RIGHT) }
        only("block_break") { d.dispatch(TriggerKind.BLOCK_BREAK, ctx, matcherEval = { true }) }
        only("tick") { d.dispatch(TriggerKind.TICK, ctx) }
        only("consume") { d.dispatch(TriggerKind.CONSUME, ctx) }
        only("custom") { d.dispatch(TriggerKind.CUSTOM, ctx, customId = customId) }
    }

    @Test
    fun `parameterized triggers only fire when their argument matches`() {
        val fired = mutableSetOf<String>()
        val d = dispatcherWithAllNine(fired)

        // Wrong interact type: the RIGHT-registered effect must not fire on a LEFT click.
        assertEquals(0, d.dispatch(TriggerKind.INTERACT, ctx, interactType = InteractType.LEFT))
        assertTrue(fired.isEmpty())

        // Block matcher rejected the block: block-break effect must not fire.
        assertEquals(0, d.dispatch(TriggerKind.BLOCK_BREAK, ctx, matcherEval = { false }))
        assertTrue(fired.isEmpty())

        // Custom id mismatch: the effect keyed to on_signal must not fire on a different id.
        assertEquals(0, d.dispatch(TriggerKind.CUSTOM, ctx, customId = ContentId.of("ramrpg", "other")))
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `a failing condition blocks the action`() {
        val d = TriggeredEffectDispatcher(InlinePlatform)
        var ran = false
        d.register(
            dev.willram.ramrpg.api.effects.TriggeredEffect(
                fk("gated"),
                EffectTrigger.OnHit,
                conditions = listOf(Condition { false }),
                action = EffectAction { ran = true },
            ),
        )
        assertEquals(0, d.dispatch(TriggerKind.HIT, ctx))
        assertFalse(ran, "the action must not run when a condition fails")
    }
}
