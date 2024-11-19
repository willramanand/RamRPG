package dev.willram.ramrpg.skills.farming

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.farming.FarmingSource
import dev.willram.ramrpg.utils.BlockUtils
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.data.Ageable
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent


class FarmingListeners {
    companion object {
        fun register() {
            Events.subscribe(BlockBreakEvent::class.java)
                .filter { e -> !BlockUtils.isPlayerPlaced(e.block) }
                .handler { e ->
                    val source: FarmingSource
                    try {
                        source = FarmingSource.valueOf(e.block.type.name)
                    } catch (ex: Exception) {
                        return@handler
                    }
                    val player: Player = e.player

                    if (player.isInvulnerable) return@handler

                    val block = e.block

                    if (source.requiresFullyGrown) {
                        val age = block.blockData as Ageable
                        if (age.age != age.maximumAge) return@handler
                    }

                    var multiBlock = 1
                    if (source.isMultiblock) {
                        multiBlock += calculateMultiblock(block.location, block)
                    }

                    if (multiBlock > 1) {
                        RamRPG.get().leveler.addXp(player, Skill.FARMING, multiBlock * RamRPG.get().sources.getXp(source))
                    } else {
                        RamRPG.get().leveler.addXp(player, Skill.FARMING, RamRPG.get().sources.getXp(source))
                    }
                }

            Events.subscribe(PlayerInteractEvent::class.java)
                .filter { e -> e.action == Action.RIGHT_CLICK_BLOCK }
                .filter { e -> !e.player.isSneaking}
                .filter { e -> e.clickedBlock != null }
                .handler { e ->
                    val source: FarmingSource
                    try {
                        source = FarmingSource.valueOf(e.clickedBlock?.type?.name!!)
                    } catch (ex: Exception) {
                        return@handler
                    }
                    if (!source.isRightClickHarvestable) return@handler

                    val age = e.clickedBlock?.blockData as Ageable
                    if (age.age != age.maximumAge) return@handler

                    RamRPG.get().leveler.addXp(e.player, Skill.FARMING, RamRPG.get().sources.getXp(source))
                }
        }

        private fun calculateMultiblock(location: Location, block: Block): Int {
            var isBlock = true
            var blockCount = 0

            while (isBlock) {
                location.y += 1
                if (location.block.type == block.type) {
                    blockCount++
                } else {
                    isBlock = false
                }
            }
            return blockCount
        }
    }
}