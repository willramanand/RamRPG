package dev.willram.ramrpg.skills.woodcutting

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.woodcutting.WoodcuttingSource
import dev.willram.ramrpg.utils.BlockUtils
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent


class WoodcuttingListeners {

    companion object {
        fun register() {
            Events.subscribe(BlockBreakEvent::class.java)
                .filter { e -> !BlockUtils.isPlayerPlaced(e.block) }
                .handler { e ->
                    val source: WoodcuttingSource
                    try {
                        source = WoodcuttingSource.valueOf(e.block.type.name)
                    } catch (e: Exception) {
                        return@handler
                    }
                    val player: Player = e.player

                    if (player.isInvulnerable) return@handler
                    RamRPG.get().leveler.addXp(player, Skill.WOODCUTTING, RamRPG.get().sources.getXp(source))
                }
        }
    }
}