package dev.willram.ramrpg.skills.defense

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.enums.EntityStats
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source
import dev.willram.ramrpg.source.defense.DefenseSource
import dev.willram.ramrpg.stats.Stat
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import kotlin.math.E
import kotlin.math.pow


class DefenseListeners {

    companion object {
        fun register() {
            Events.subscribe(EntityDamageByEntityEvent::class.java)
                .handler { e ->
                    if (e.isCancelled) return@handler
                    if (e.entity !is Player) return@handler
                    if (e.entity == e.damager) return@handler
                    val source: Source = if (e.damager is Player) {
                        DefenseSource.PLAYER_DAMAGE
                    } else {
                        DefenseSource.MOB_DAMAGE
                    }
                    RamRPG.get().leveler.addXp(
                        e.entity as Player,
                        Skill.DEFENSE,
                        e.damage * RamRPG.get().sources.getXp(source)
                    )
                }

            Events.subscribe(EntityDamageByEntityEvent::class.java, EventPriority.HIGH)
                .handler { e ->
                    if (e.isCancelled) return@handler
                    if (e.entity !is LivingEntity) return@handler

                    val defense: Double
                    if (e.entity is Player) {
                        val data = RamRPG.get().players.get(e.entity.uniqueId)
                        defense = data.statPoints[Stat.DEFENSE] ?: 0.0
                    } else {
                        defense = try {
                            EntityStats.valueOf(e.entity.type.toString()).defense
                        } catch (e: Exception) {
                            0.0
                        }
                    }
                    val calculateMitigation = defense / (defense + 100)
                    val newDamage = e.finalDamage * (1 - calculateMitigation)

                    e.damage = newDamage
                }
        }
    }
}