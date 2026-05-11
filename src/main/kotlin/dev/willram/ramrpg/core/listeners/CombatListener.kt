/** Bridges EntityDamageByEntityEvent into DamagePipeline; writes back finalDamage. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamagePipeline
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent

class CombatListener(private val pipeline: DamagePipeline) {
    fun register() {
        Events.subscribe(EntityDamageByEntityEvent::class.java, EventPriority.HIGH)
            .filter { !it.isCancelled }
            .filter { it.entity is LivingEntity }
            .handler { e ->
                val victim = e.entity as LivingEntity
                val attacker: LivingEntity? = when (val d = e.damager) {
                    is LivingEntity -> d
                    is Projectile -> (d.shooter as? LivingEntity)
                    else -> null
                }
                val weapon = (attacker as? Player)?.inventory?.itemInMainHand
                val ctx = DamageContext(
                    attacker = attacker,
                    victim = victim,
                    cause = e.cause,
                    weapon = weapon,
                    baseDamage = e.damage,
                )
                pipeline.process(ctx)
                if (ctx.cancelled) { e.isCancelled = true; return@handler }
                e.damage = ctx.finalDamage
                for (sec in ctx.secondaryQueue) {
                    if (sec.cancelled) continue
                    victim.damage(sec.finalDamage, sec.attacker)
                }
            }
    }
}
