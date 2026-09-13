/** Builtin DamageStage implementations covering the offensive + defensive pipeline. */
package dev.willram.ramrpg.builtin.stats

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.combat.DamageStage
import dev.willram.ramrpg.api.combat.DamageTag
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.builtin.identity.RamStats
import dev.willram.ramrpg.core.services.DurabilityService
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
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

class ApplyStage : DamageStage {
    override val key = id("apply")
    override val priority = DamagePriority.APPLY
    override fun apply(ctx: DamageContext) { /* writes back into event in CombatListener */ }
}

/**
 * WP-2.2: drains RPG durability at [DamagePriority.APPLY] (2000) -- after every offense/defense stage has
 * settled `ctx.finalDamage`, so the drain reflects "a hit landed", not merely an attempt (the pipeline
 * already stops running stages once `ctx.cancelled`, so a cancelled attack never reaches this stage; see
 * `DamagePipelineImpl.runStages`). Drains the attacker's held weapon by [DurabilityService.DRAIN_PER_HIT]
 * and, when the victim is a [Player], each worn armor piece by [DurabilityService.DRAIN_PER_ARMOR_HIT] --
 * both directions run through the same pure [DurabilityService.damage]. See
 * `docs/design/2.2-durability.md` for the rates and why armor is included.
 *
 * Inert-at-zero is NOT decided here -- [dev.willram.ramrpg.api.items.ItemDefinition.inertReason] (WP-2.1c)
 * reads `ItemInstanceData.durability` directly, so once [DurabilityService.damage] clamps a stack to 0 the
 * very next stats recalculation treats it as inert. This stage's only job is making that number reach 0
 * (and telling the owner about it) -- it never re-implements or redefines what "inert" means.
 *
 * Folia-safety: like [LifestealStage] (which mutates `p.health` directly with no scheduler hop) this stage
 * writes back to the attacker's/victim's live inventory/equipment inline -- `DamagePipeline.process`
 * already runs synchronously inside `CombatListener`'s `EntityDamageByEntityEvent` handler, which Folia
 * already dispatches on the entity's own region thread, so there is nothing extra to schedule.
 */
class DurabilityDrainStage(
    private val items: ItemInstanceService,
    private val durability: DurabilityService,
) : DamageStage {
    override val key = id("durability_drain")
    override val priority = DamagePriority.APPLY

    override fun apply(ctx: DamageContext) {
        val attacker = ctx.attacker as? Player
        if (attacker != null) {
            drainAndWrite(attacker.inventory.itemInMainHand, DurabilityService.DRAIN_PER_HIT, attacker) {
                attacker.inventory.setItemInMainHand(it)
            }
        }
        val victim = ctx.victim as? Player ?: return
        val eq = victim.equipment
        drainAndWrite(eq.helmet, DurabilityService.DRAIN_PER_ARMOR_HIT, victim) { eq.setHelmet(it) }
        drainAndWrite(eq.chestplate, DurabilityService.DRAIN_PER_ARMOR_HIT, victim) { eq.setChestplate(it) }
        drainAndWrite(eq.leggings, DurabilityService.DRAIN_PER_ARMOR_HIT, victim) { eq.setLeggings(it) }
        drainAndWrite(eq.boots, DurabilityService.DRAIN_PER_ARMOR_HIT, victim) { eq.setBoots(it) }
    }

    private fun drainAndWrite(stack: ItemStack?, amount: Int, owner: Player, write: (ItemStack) -> Unit) {
        if (stack == null || stack.type.isAir) return
        val data = items.identify(stack) ?: return
        if (data.durability <= 0) return // already inert; damage() would no-op anyway -- nothing to notify again
        val updated = durability.damage(data, amount)
        if (updated.durability == data.durability) return
        write(items.write(stack, updated))
        notify(owner, stack, data.durability, updated)
    }

    /** [ramrpg.item.broken] once, exactly on the hit that reaches 0; a one-time [ramrpg.item.durability_warning] on the hit that crosses [DurabilityService.WARNING_THRESHOLD_FRACTION] -- never repeated while it stays below. */
    private fun notify(owner: Player, stack: ItemStack, before: Int, updated: ItemInstanceData) {
        val max = updated.maxDurability
        if (max <= 0) return
        val name = stack.itemMeta?.let { if (it.hasDisplayName()) it.displayName() else null }
            ?: Component.text(stack.type.name)
        if (updated.durability <= 0) {
            owner.sendMessage(Component.translatable("ramrpg.item.broken", name))
            return
        }
        val warnAt = max * DurabilityService.WARNING_THRESHOLD_FRACTION
        if (before > warnAt && updated.durability <= warnAt) {
            owner.sendMessage(Component.translatable(
                "ramrpg.item.durability_warning", name, Component.text(updated.durability), Component.text(max)
            ))
        }
    }
}
