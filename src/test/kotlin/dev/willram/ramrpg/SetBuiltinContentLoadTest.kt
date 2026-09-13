package dev.willram.ramrpg

import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.core.config.RpgContentLoader
import dev.willram.ramrpg.core.config.specs.EffectSpec
import dev.willram.ramrpg.core.effects.BlockMatcherRegistry
import dev.willram.ramrpg.core.effects.BuiltinEffectActions
import dev.willram.ramrpg.core.effects.EffectActionRegistry
import dev.willram.ramrpg.core.effects.EffectConditionRegistry
import dev.willram.ramrpg.core.platform.Cancellable
import dev.willram.ramrpg.core.platform.PlatformScheduler
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-5.3: proves the packaged `content/sets/builtin.conf` (loaded through the SAME
 * `RpgContentLoader.loadPackaged` path `SetModule` uses at enable()) parses cleanly with real effect
 * registries -- catching an authoring typo in the shipped content itself, not just the parser logic
 * `SetThresholdCountTest`/`SetStatProviderTest` exercise against hand-built fixtures.
 */
class SetBuiltinContentLoadTest {

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

    private fun registries(): EffectSpec.Registries {
        val actions = EffectActionRegistry()
        val conditions = EffectConditionRegistry()
        val matchers = BlockMatcherRegistry()
        BuiltinEffectActions.registerAll(actions, conditions, matchers, InlinePlatform)
        return EffectSpec.Registries(actions, conditions, matchers)
    }

    @Test
    fun `packaged sets builtin conf loads with no errors`() {
        val result = RpgContentLoader.loadPackaged(registries())
        assertTrue(result.successful(), result.errors().joinToString("; ") { it.describe() })
        assertEquals(4, result.sets.size)
    }

    @Test
    fun `every shipped set has at least a 2-piece and a 4-piece threshold, each with a StatEffect`() {
        val result = RpgContentLoader.loadPackaged(registries())
        for (spec in result.sets) {
            assertTrue(2 in spec.thresholds, "${spec.id} missing a 2-piece threshold")
            assertTrue(4 in spec.thresholds, "${spec.id} missing a 4-piece threshold")
            for ((count, effects) in spec.thresholds) {
                assertTrue(effects.isNotEmpty(), "${spec.id} threshold $count has no effects")
                assertTrue(effects.all { it is StatEffect }, "${spec.id} threshold $count has a non-stat effect")
            }
        }
    }

    @Test
    fun `every shipped set has exactly 4 members, all distinct armor pieces`() {
        val result = RpgContentLoader.loadPackaged(registries())
        for (spec in result.sets) {
            assertEquals(4, spec.members.size, "${spec.id} does not have exactly 4 members")
        }
    }

    @Test
    fun `shipped set members reference real packaged items`() {
        val setsResult = RpgContentLoader.loadPackaged(registries())
        val itemsResult = RpgContentLoader.loadPackaged()
        val itemKeys: Set<ItemKey> = itemsResult.items.map { it.key }.toSet()
        for (spec in setsResult.sets) {
            for (member in spec.members) {
                assertTrue(member in itemKeys, "${spec.id} references unknown item $member")
            }
        }
    }
}
