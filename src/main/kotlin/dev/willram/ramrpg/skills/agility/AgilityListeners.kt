package dev.willram.ramrpg.skills.agility

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.metadata.Metadata
import dev.willram.ramcore.metadata.MetadataKey
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.agility.AgilitySource
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent


class AgilityListeners {
    companion object {
        private val PLAYER_MOVE_KEY: MetadataKey<Int> = MetadataKey.createIntegerKey("player-skill-agility-movement-key")

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

            Events.subscribe(PlayerMoveEvent::class.java)
                .handler { e ->
                    val from = e.from
                    val to = e.to
                    if (to.blockX == from.blockX && to.blockY == from.blockY && to.blockZ == from.blockZ) return@handler
                    val player: Player = e.player

                    val metadataMap = Metadata.provideForPlayer(player)
                    val currentValue = metadataMap.getOrDefault(PLAYER_MOVE_KEY, 0)
                    metadataMap.put(PLAYER_MOVE_KEY, currentValue + 1)

                    if (metadataMap.get(PLAYER_MOVE_KEY).get() >= 100) {
                        if (player.isInvulnerable) return@handler
                        RamRPG.get().leveler.addXp(player, Skill.AGILITY, 25 * RamRPG.get().sources.getXp(AgilitySource.MOVE_PER_BLOCK))
                        metadataMap.put(PLAYER_MOVE_KEY, 0)
                    }
                }
        }
    }
}