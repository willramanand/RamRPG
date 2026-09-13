/**
 * Forces inventory refresh after click/drag so packet renderer's lore wins
 * over client-side prediction (1.21+ state-ID prediction would otherwise
 * keep the client's predicted lore-less stack).
 */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramrpg.core.platform.PlatformScheduler
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

class InventoryRefreshListener(private val platform: PlatformScheduler) {
    fun register(consumer: TerminableConsumer) {
        consumer.bind(Events.subscribe(InventoryClickEvent::class.java, EventPriority.MONITOR)
            .filter { !it.isCancelled }
            .filter { it.whoClicked is Player }
            .handler { e -> refreshNextTick(e.whoClicked as Player) })
        consumer.bind(Events.subscribe(InventoryDragEvent::class.java, EventPriority.MONITOR)
            .filter { !it.isCancelled }
            .filter { it.whoClicked is Player }
            .handler { e -> refreshNextTick(e.whoClicked as Player) })
    }

    private fun refreshNextTick(player: Player) {
        platform.runForPlayerLater(player, 1L) { player.updateInventory() }
    }
}
