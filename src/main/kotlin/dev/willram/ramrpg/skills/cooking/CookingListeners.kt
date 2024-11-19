package dev.willram.ramrpg.skills.cooking

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.cooking.CookingSource
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.FurnaceExtractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import java.lang.Double.min


class CookingListeners {

    companion object {
        fun register () {
            Events.subscribe(CraftItemEvent::class.java)
                .handler { e ->
                    if (e.inventory.result == null) return@handler
                    val source: CookingSource
                    try {
                        source = CookingSource.valueOf(e.inventory.result!!.type.name)
                    } catch (e: Exception) {
                        return@handler
                    }
                    val player = e.whoClicked as Player
                    if (player.isInvulnerable) return@handler
                    if (e.isShiftClick) shiftClick(e)

                    RamRPG.get().leveler.addXp(player, Skill.COOKING, RamRPG.get().sources.getXp(source))

                }

            Events.subscribe(FurnaceExtractEvent::class.java)
                .handler { e ->
                    if (e.itemType == null) return@handler
                    val source: CookingSource
                    try {
                        source = CookingSource.valueOf(e.itemType.name)
                    } catch (e: Exception) {
                        return@handler
                    }

                    val numCooked: Int = e.itemAmount

                    val player: Player = e.player
                    if (player.isInvulnerable) return@handler
                    RamRPG.get().leveler.addXp(player, Skill.COOKING, numCooked * RamRPG.get().sources.getXp(source))
                }

            Events.subscribe(PlayerItemConsumeEvent::class.java)
                .handler { e ->
                    if (e.item == null) return@handler
                    val source: CookingSource
                    try {
                        source = CookingSource.valueOf(e.item.type.name)
                    } catch (e: Exception) {
                        return@handler
                    }

                    val player: Player = e.player
                    if (player.isInvulnerable) return@handler
                    RamRPG.get().leveler.addXp(player, Skill.COOKING, RamRPG.get().sources.getXp(source))
                }
        }

        private fun shiftClick(event: CraftItemEvent) {
            var itemsChecked = 0
            var possibleCreations = 1
            for (item in event.inventory.matrix) {
                if (item != null && item.type != Material.AIR) {
                    possibleCreations = if (itemsChecked == 0) item.amount
                    else min(possibleCreations.toDouble(), item.amount.toDouble()).toInt()
                    itemsChecked++
                }
            }
            val amountOfItems = event.recipe.result.amount * possibleCreations
            RamRPG.get().leveler.addXp(event.whoClicked as Player, Skill.COOKING, amountOfItems * RamRPG.get().sources.getXp(event.inventory.result?.type?.let { CookingSource.valueOf(it.name) }!!))
        }
    }
}