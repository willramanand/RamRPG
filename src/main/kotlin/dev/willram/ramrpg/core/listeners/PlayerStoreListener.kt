/** Async load on login; async save on quit via FilePlayerStore. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.core.storage.FilePlayerStore
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerStoreListener(private val store: FilePlayerStore) {
    fun register() {
        Events.subscribe(PlayerLoginEvent::class.java, EventPriority.HIGH)
            .filter { it.result == PlayerLoginEvent.Result.ALLOWED }
            .handler { e -> store.loadAsync(e.player.uniqueId) { } }
        Events.subscribe(PlayerQuitEvent::class.java).handler { e ->
            store.saveAsync(e.player.uniqueId)
        }
    }
}
