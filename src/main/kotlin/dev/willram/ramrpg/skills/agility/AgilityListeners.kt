package dev.willram.ramrpg.skills.agility

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.agility.AgilitySource
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.metadata.FixedMetadataValue


class AgilityListeners {
    companion object {
        fun register() {
            Events.subscribe(EntityDamageEvent::class.java, EventPriority.MONITOR)
                .handler { e ->
                    if (e.cause != EntityDamageEvent.DamageCause.FALL) return@handler
                    if (e.entity !is Player) return@handler
                    val player = e.entity as Player
                    if (e.entity.isInvulnerable) return@handler
                    if (e.finalDamage > player.health) return@handler
                    RamRPG.get().leveler.addXp(player, Skill.AGILITY, e.getOriginalDamage(EntityDamageEvent.DamageModifier.BASE) * RamRPG.get().sources.getXp(AgilitySource.FALL_DAMAGE))
                }

//            Events.subscribe(PlayerMoveEvent::class.java)
//                .handler { e ->
//                    val from: Location = e.from
//                    val to: Location = e.to
//                    if (to.blockX == from.blockX && to.blockY == from.blockY && to.blockZ == from.blockZ) return@handler
//                    val player: Player = e.player
//                    if (!(player.hasMetadata("skills_moving"))) {
//                        player.setMetadata("skills_moving", FixedMetadataValue(plugin, 1))
//                    }
//
//                    player.setMetadata(
//                        "skills_moving",
//                        FixedMetadataValue(plugin, player.getMetadata("skills_moving")[0].asInt() + 1)
//                    )
//                    if (player.getMetadata("skills_moving")[0].asInt() >= 100) {
//                        if (player.isInvulnerable) return@handler
//                        plugin.getLeveler()
//                            .addXp(player, Skills.AGILITY, 25 * getXp(player, AgilitySource.MOVE_PER_BLOCK))
//                        player.removeMetadata("skills_moving", plugin)
//                    }
//                }
        }
    }
}