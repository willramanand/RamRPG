/**
 * Folia-aware scheduler abstraction over RamCore Schedulers. Mandatory
 * for entity / region operations to respect Folia thread affinity.
 */
package dev.willram.ramrpg.core.platform

import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.scheduler.Task
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

interface Cancellable { fun cancel() }

interface PlatformScheduler {
    fun runGlobal(task: Runnable)
    fun runAsync(task: Runnable)
    fun runForEntity(entity: Entity, task: Runnable)
    fun runForPlayer(player: Player, task: Runnable)
    fun runAtLocation(loc: Location, task: Runnable)
    fun runLaterGlobal(delayTicks: Long, task: Runnable)
    fun repeatGlobal(periodTicks: Long, task: () -> Unit): Cancellable
    fun repeatForEntity(e: Entity, periodTicks: Long, task: () -> Unit): Cancellable
}

class RamCorePlatformScheduler : PlatformScheduler {
    override fun runGlobal(task: Runnable) { Schedulers.runGlobal(task) }
    override fun runAsync(task: Runnable) { Schedulers.runAsync(task) }
    override fun runForEntity(entity: Entity, task: Runnable) { Schedulers.run(entity, task) }
    override fun runForPlayer(player: Player, task: Runnable) { Schedulers.run(player, task) }
    override fun runAtLocation(loc: Location, task: Runnable) { Schedulers.run(loc, task) }
    override fun runLaterGlobal(delayTicks: Long, task: Runnable) {
        Schedulers.forGlobal().runLater(task, delayTicks)
    }
    override fun repeatGlobal(periodTicks: Long, task: () -> Unit): Cancellable {
        val handle = Schedulers.forGlobal().runRepeating({ _: Task -> task() }, periodTicks, periodTicks)
        return cancellableOf { handle.stop() }
    }
    override fun repeatForEntity(e: Entity, periodTicks: Long, task: () -> Unit): Cancellable {
        val handle = Schedulers.forEntity(e).runRepeating({ _: Task -> task() }, periodTicks, periodTicks)
        return cancellableOf { handle.stop() }
    }
}

private fun cancellableOf(action: () -> Unit) = object : Cancellable { override fun cancel() { action() } }
