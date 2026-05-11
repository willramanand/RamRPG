/** Per-entity 1-tick mana regeneration: maxMana / 180 per tick. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.builtin.identity.RamStats
import dev.willram.ramrpg.core.platform.Cancellable
import dev.willram.ramrpg.core.platform.PlatformScheduler
import dev.willram.ramrpg.core.storage.PlayerStore
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ManaRegen(
    private val stats: StatService,
    private val store: PlayerStore,
    private val platform: PlatformScheduler,
) {
    private val tasks = ConcurrentHashMap<UUID, Cancellable>()

    fun register() {
        Events.subscribe(PlayerJoinEvent::class.java).handler { e -> start(e.player) }
        Events.subscribe(PlayerQuitEvent::class.java).handler { e -> stop(e.player.uniqueId) }
    }

    private fun start(player: Player) {
        val handle = platform.repeatForEntity(player, 1L) {
            val data = store.require(player.uniqueId)
            val maxMana = stats.snapshot(player)[RamStats.WISDOM]
            data.maxManaCache = maxMana
            if (data.currentMana < 0.0) {
                data.currentMana = maxMana
                return@repeatForEntity
            }
            val current = data.currentMana
            if (current < maxMana) {
                val regen = maxMana / 180.0
                data.currentMana = (current + regen).coerceAtMost(maxMana)
            } else if (current > maxMana) {
                data.currentMana = maxMana
            }
        }
        tasks[player.uniqueId] = handle
    }

    private fun stop(id: UUID) {
        tasks.remove(id)?.cancel()
    }

    fun shutdown() {
        for (t in tasks.values) t.cancel()
        tasks.clear()
    }
}
