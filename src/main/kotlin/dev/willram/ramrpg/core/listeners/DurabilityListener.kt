/** Cancels all item durability damage globally. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerItemDamageEvent

class DurabilityListener {
    fun register() {
        Events.subscribe(PlayerItemDamageEvent::class.java, EventPriority.NORMAL)
            .handler { it.isCancelled = true }
    }
}
