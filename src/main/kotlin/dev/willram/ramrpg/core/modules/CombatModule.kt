/**
 * WP-1.7b: the combat subsystem seam. Owns the damage-pipeline stage registrations and the
 * [CombatListener] bridge (EntityDamageByEntityEvent -> DamagePipeline). Collaborators are resolved
 * from the [ServiceContext] the modules were registered into in `load()`, never from a singleton.
 *
 * Later work packages register their own damage stages here (WP-2.3a: Resistances / elemental
 * breakdown; WP-6.4a: boss combat) -- in this module, never in `RamRPG.kt`.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.builtin.stats.ApplyStage
import dev.willram.ramrpg.builtin.stats.ArmorMitigationStage
import dev.willram.ramrpg.builtin.stats.CritRollStage
import dev.willram.ramrpg.builtin.stats.DamageIndicatorStage
import dev.willram.ramrpg.builtin.stats.EnchantDamageStage
import dev.willram.ramrpg.builtin.stats.EnchantPostHitStage
import dev.willram.ramrpg.builtin.stats.FerocityStage
import dev.willram.ramrpg.builtin.stats.LifestealStage
import dev.willram.ramrpg.builtin.stats.StrengthStage
import dev.willram.ramrpg.builtin.stats.TrueDefenseStage
import dev.willram.ramrpg.builtin.stats.WeaponBaseStage
import dev.willram.ramrpg.core.listeners.CombatListener
import dev.willram.ramrpg.core.services.RpgServiceKeys

class CombatModule(private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        val pipeline = ctx.service(RpgServiceKeys.DAMAGE_PIPELINE)
        val stats = ctx.service(RpgServiceKeys.STATS)
        val itemInstances = ctx.service(RpgServiceKeys.ITEM_INSTANCES)
        val enchantments = ctx.service(RpgServiceKeys.ENCHANTMENTS)

        pipeline.register(WeaponBaseStage(stats))
        pipeline.register(StrengthStage(stats))
        pipeline.register(EnchantDamageStage(itemInstances, enchantments, DamagePriority.ENCHANT_OFFENSE, true))
        pipeline.register(CritRollStage(stats))
        pipeline.register(ArmorMitigationStage(stats))
        pipeline.register(TrueDefenseStage(stats))
        pipeline.register(EnchantDamageStage(itemInstances, enchantments, DamagePriority.ENCHANT_DEFENSE, false))
        pipeline.register(LifestealStage(stats))
        pipeline.register(EnchantPostHitStage(itemInstances, enchantments))
        pipeline.register(FerocityStage(stats))
        pipeline.register(DamageIndicatorStage())
        pipeline.register(ApplyStage())

        CombatListener(pipeline).register(consumer)
    }

    companion object {
        const val OWNER = "ramrpg-combat"
    }
}
