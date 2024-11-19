package dev.willram.ramrpg.skills.combat

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.combat.CombatSource
import org.bukkit.entity.Boss
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent


class CombatListeners {

    companion object {
        fun register() {
            Events.subscribe(EntityDamageByEntityEvent::class.java, EventPriority.MONITOR)
                .handler { e ->
                    if (e.isCancelled) return@handler
                    val player: Player = if (e.damager is Player) {
                        e.damager as Player
                    } else if (e.damager is Projectile && (e.damager as Projectile).shooter is Player) {
                        (e.damager as Projectile).shooter as Player
                    } else return@handler

                    if (e.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                        && e.cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                        && e.cause != EntityDamageEvent.DamageCause.PROJECTILE) return@handler

                    if (e.entity !is LivingEntity) return@handler
                    val type = e.entityType
                    if (e.entity == player) return@handler
                    val health = (e.entity as LivingEntity).health
                    var damage = 1.0.coerceAtMost((e.finalDamage / health))
                    if (e.entity is Boss) {
                        damage = 0.01.coerceAtMost((e.finalDamage / health) / 100)
                    }
                    if (e.finalDamage > health) return@handler
                    RamRPG.get().leveler.addXp(player, Skill.COMBAT, damage * RamRPG.get().sources.getXp(CombatSource.valueOf(type.toString())))
                }

            Events.subscribe(EntityDeathEvent::class.java, EventPriority.HIGHEST)
                .handler { event ->
                    val e: LivingEntity = event.entity
                    if (e.killer == null) return@handler
                    if (e.lastDamageCause !is EntityDamageByEntityEvent) return@handler
                    if ((e.lastDamageCause as EntityDamageByEntityEvent).damager is Player) {
                        val p = (e.lastDamageCause as EntityDamageByEntityEvent).damager as Player
                        val type = e.type
                        if (e == p) return@handler
                        RamRPG.get().leveler.addXp(p, Skill.COMBAT, RamRPG.get().sources.getXp(CombatSource.valueOf(type.toString())))
                    } else if ((e.lastDamageCause as EntityDamageByEntityEvent).damager is Projectile) {
                        val projectile = (e.lastDamageCause as EntityDamageByEntityEvent).damager as Projectile
                        val type = e.type
                        if (projectile.shooter !is Player) return@handler
                        val p = projectile.shooter as Player
                        if (e == p) return@handler
                        RamRPG.get().leveler.addXp(p, Skill.COMBAT, RamRPG.get().sources.getXp(CombatSource.valueOf(type.toString())))
                    }
                }

        }
    }

}