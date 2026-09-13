/**
 * WP-3.1b: opens a crafting station when a player right-clicks a block whose type matches some
 * [Station.blockMatcher]. Folia-safe: the handler already runs on the player's owning region thread, and
 * [CraftingService.open] -> `Menus.open` -> `MenuSession.open()` anchors the inventory open to the player
 * scheduler itself, so no extra scheduler hop is needed here (WP-3.1b review, N1). Binds through the
 * module's [TerminableConsumer] (never RamRPG.kt).
 */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramrpg.api.crafting.CraftingService
import dev.willram.ramrpg.api.crafting.StationRegistry
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class StationBlockListener(
    private val stations: StationRegistry,
    private val crafting: CraftingService,
) {
    fun register(consumer: TerminableConsumer) {
        consumer.bind(Events.subscribe(PlayerInteractEvent::class.java, EventPriority.HIGH).handler { e ->
            if (e.action != Action.RIGHT_CLICK_BLOCK) return@handler
            // Only the main hand, so a click never opens the station twice (both hands fire the event).
            if (e.hand != EquipmentSlot.HAND) return@handler
            val block = e.clickedBlock ?: return@handler
            val player = e.player
            // Sneak-right-click is "place the block in hand"; don't hijack it.
            if (player.isSneaking) return@handler
            val station = stations.all().firstOrNull { it.blockMatcher.matches(block) } ?: return@handler
            // Suppress the vanilla block use (e.g. the smithing-table GUI) so only the RPG station opens.
            e.isCancelled = true
            crafting.open(player, station)
        })
    }
}
