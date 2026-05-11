/**
 * Combat damage pipeline. [DamageContext] flows through ordered
 * [DamageStage]s (priority asc) emitting tags and components. Stable
 * priorities defined in [DamagePriority] let third-party stages slot
 * between builtin offense/defense steps.
 */
package dev.willram.ramrpg.api.combat

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.DamageTypeKey
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack

enum class DamageTag { CRIT, TRUE, MAGIC, MELEE, RANGED, SECONDARY, INDIRECT }

class DamageContext(
    val attacker: LivingEntity?,
    val victim: LivingEntity,
    val cause: EntityDamageEvent.DamageCause,
    var weapon: ItemStack? = null,
    var baseDamage: Double,
) {
    var finalDamage: Double = baseDamage
    val tags: MutableSet<DamageTag> = mutableSetOf()
    val components: MutableMap<DamageTypeKey, Double> = mutableMapOf()
    val metadata: MutableMap<String, Any> = mutableMapOf()
    var cancelled: Boolean = false
    var secondaryQueue: MutableList<DamageContext> = mutableListOf()
}

interface DamageStage {
    val key: ContentId
    val priority: Int
    fun apply(ctx: DamageContext)
}

interface DamagePipeline {
    fun register(stage: DamageStage)
    fun unregister(key: ContentId)
    fun stages(): List<DamageStage>
    fun process(ctx: DamageContext): DamageContext
}

object DamagePriority {
    const val WEAPON_BASE = 100
    const val STRENGTH = 200
    const val ENCHANT_OFFENSE = 300
    const val EFFECT_OFFENSE = 400
    const val CRIT_ROLL = 500
    const val ELEMENTAL_BREAKDOWN = 600
    const val ABILITY_MOD = 700
    const val ARMOR_MITIGATION = 1100
    const val TRUE_DEFENSE = 1200
    const val ENCHANT_DEFENSE = 1300
    const val SHIELDS = 1400
    const val THORNS = 1500
    const val LIFESTEAL = 1600
    const val FEROCITY = 1700
    const val INDICATOR = 1900
    const val APPLY = 2000
}
