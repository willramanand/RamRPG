package dev.willram.ramrpg.skills.excavation

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.excavation.ExcavationSource
import dev.willram.ramrpg.utils.BlockUtils
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent


class ExcavationListeners {
    companion object {
        fun register() {
            Events.subscribe(BlockBreakEvent::class.java)
                .filter { e -> !BlockUtils.isPlayerPlaced(e.block) }
                .handler { e ->
                    val source: ExcavationSource
                    try {
                        source = ExcavationSource.valueOf(e.block.type.name)
                    } catch (ex: Exception) {
                        return@handler
                    }
                    val player: Player = e.player

                    if (player.isInvulnerable) return@handler
                    RamRPG.get().leveler.addXp(player, Skill.EXCAVATION, RamRPG.get().sources.getXp(source))
                }
        }
    }
}