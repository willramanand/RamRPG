/** Builtin DamageStage implementations covering the offensive + defensive pipeline. */
package dev.willram.ramrpg.builtin.stats

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.combat.DamageStage
import dev.willram.ramrpg.api.combat.DamageTag
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.builtin.identity.RamStats
import org.bukkit.entity.Player
import kotlin.random.Random

private fun id(v: String) = ContentId.of("ramrpg", v)

class WeaponBaseStage(private val stats: StatService) : DamageStage {
    override val key = id("weapon_base")
    override val priority = DamagePriority.WEAPON_BASE
    override fun apply(ctx: DamageContext) {
        val p = ctx.attacker as? Player ?: return
        val rpgDamage = stats.snapshot(p).get(RamStats.DAMAGE)
        if (rpgDamage > 0) ctx.finalDamage = rpgDamage
    }
}

class StrengthStage(private val stats: StatService) : DamageStage {
    override val key = id("strength")
    override val priority = DamagePriority.STRENGTH
    override fun apply(ctx: DamageContext) {
        val p = ctx.attacker as? Player ?: return
        val str = stats.snapshot(p).get(RamStats.STRENGTH)
        ctx.finalDamage *= (1.0 + str / 100.0)
    }
}

class CritRollStage(private val stats: StatService) : DamageStage {
    override val key = id("crit_roll")
    override val priority = DamagePriority.CRIT_ROLL
    override fun apply(ctx: DamageContext) {
        val p = ctx.attacker as? Player ?: return
        val snap = stats.snapshot(p)
        val chance = snap.get(RamStats.CRIT_CHANCE)
        if (Random.nextDouble(0.0, 100.0) < chance) {
            val critDmg = snap.get(RamStats.CRIT_DAMAGE)
            ctx.finalDamage *= (1.0 + critDmg / 100.0)
            ctx.tags.add(DamageTag.CRIT)
        }
    }
}

class ArmorMitigationStage(private val stats: StatService) : DamageStage {
    override val key = id("armor_mitigation")
    override val priority = DamagePriority.ARMOR_MITIGATION
    override fun apply(ctx: DamageContext) {
        if (DamageTag.TRUE in ctx.tags) return
        val def: Double = when (val v = ctx.victim) {
            is Player -> stats.snapshot(v).get(RamStats.DEFENSE)
            else -> dev.willram.ramcore.pdc.PDCs.get(v, dev.willram.ramrpg.core.listeners.EntityProfilePdc.DEFENSE).orElse(0.0)
        }
        if (def <= 0) return
        val factor = 1.0 - (def / (def + 100.0))
        ctx.finalDamage *= factor
    }
}

class TrueDefenseStage(private val stats: StatService) : DamageStage {
    override val key = id("true_defense")
    override val priority = DamagePriority.TRUE_DEFENSE
    override fun apply(ctx: DamageContext) {
        val p = ctx.victim as? Player ?: return
        when (ctx.cause) {
            org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE,
            org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE_TICK,
            org.bukkit.event.entity.EntityDamageEvent.DamageCause.LAVA,
            org.bukkit.event.entity.EntityDamageEvent.DamageCause.HOT_FLOOR -> {}
            else -> return
        }
        val td = stats.snapshot(p).get(RamStats.TRUE_DEFENSE)
        if (td <= 0) return
        val factor = 1.0 - (td / (td + 100.0))
        ctx.finalDamage *= factor
    }
}

class LifestealStage(private val stats: StatService) : DamageStage {
    override val key = id("lifesteal")
    override val priority = DamagePriority.LIFESTEAL
    override fun apply(ctx: DamageContext) {
        val p = ctx.attacker as? Player ?: return
        val ls = stats.snapshot(p).get(RamStats.LIFESTEAL)
        if (ls <= 0) return
        val heal = ctx.finalDamage * ls / 100.0
        p.health = (p.health + heal).coerceAtMost(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: p.health)
    }
}

class FerocityStage(private val stats: StatService) : DamageStage {
    override val key = id("ferocity")
    override val priority = DamagePriority.FEROCITY
    override fun apply(ctx: DamageContext) {
        if (DamageTag.SECONDARY in ctx.tags) return
        val p = ctx.attacker as? Player ?: return
        val fer = stats.snapshot(p).get(RamStats.FEROCITY)
        if (fer <= 0) return
        var remaining = fer
        while (remaining > 0) {
            val proc = if (remaining >= 100) 100.0 else remaining
            if (Random.nextDouble(0.0, 100.0) < proc) {
                val sec = DamageContext(ctx.attacker, ctx.victim, ctx.cause, ctx.weapon, ctx.baseDamage)
                sec.tags.add(DamageTag.SECONDARY)
                ctx.secondaryQueue.add(sec)
            }
            remaining -= 100
        }
    }
}

class IndicatorStage : DamageStage {
    override val key = id("indicator")
    override val priority = DamagePriority.INDICATOR
    override fun apply(ctx: DamageContext) { /* hooks register via DamagePipelineEffect */ }
}

class ApplyStage : DamageStage {
    override val key = id("apply")
    override val priority = DamagePriority.APPLY
    override fun apply(ctx: DamageContext) { /* writes back into event in CombatListener */ }
}
