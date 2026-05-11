/** Stage that scans equipped enchantments and runs their DamagePipelineEffect hooks. */
package dev.willram.ramrpg.builtin.stats

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.combat.DamageStage
import dev.willram.ramrpg.api.effects.DamagePipelineEffect
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class EnchantDamageStage(
    private val items: ItemInstanceService,
    private val enchants: EnchantmentRegistry,
    override val priority: Int,
    private val attackerSide: Boolean,
) : DamageStage {
    override val key: ContentId = ContentId.of("ramrpg", if (attackerSide) "enchant_offense" else "enchant_defense")

    override fun apply(ctx: DamageContext) {
        val sources: List<ItemStack> = if (attackerSide) {
            val a = ctx.attacker as? Player ?: return
            listOf(a.inventory.itemInMainHand, a.inventory.itemInOffHand).filterNotNull()
        } else {
            val v = ctx.victim as? Player ?: return
            val eq = v.equipment ?: return
            listOf(eq.helmet, eq.chestplate, eq.leggings, eq.boots).filterNotNull()
        }
        for (stack in sources) {
            val data = items.identify(stack) ?: continue
            for ((ek, lvl) in data.enchantments) {
                val ench = enchants.get(ek) ?: continue
                for (eff in ench.effects(lvl)) {
                    if (eff is DamagePipelineEffect && eff.priority in priorityWindow()) {
                        eff.hook.apply(ctx)
                    }
                }
            }
        }
    }

    private fun priorityWindow(): IntRange = if (attackerSide) {
        DamagePriority.ENCHANT_OFFENSE..(DamagePriority.ENCHANT_OFFENSE + 99)
    } else {
        DamagePriority.ENCHANT_DEFENSE..(DamagePriority.ENCHANT_DEFENSE + 99)
    }
}

class EnchantPostHitStage(
    private val items: ItemInstanceService,
    private val enchants: EnchantmentRegistry,
) : DamageStage {
    override val key: ContentId = ContentId.of("ramrpg", "enchant_posthit")
    override val priority: Int = DamagePriority.LIFESTEAL + 1

    override fun apply(ctx: DamageContext) {
        val a = ctx.attacker as? Player ?: return
        val sources = listOf(a.inventory.itemInMainHand, a.inventory.itemInOffHand).filterNotNull()
        for (stack in sources) {
            val data = items.identify(stack) ?: continue
            for ((ek, lvl) in data.enchantments) {
                val ench = enchants.get(ek) ?: continue
                for (eff in ench.effects(lvl)) {
                    if (eff is DamagePipelineEffect && eff.priority >= DamagePriority.LIFESTEAL && eff.priority < DamagePriority.FEROCITY) {
                        eff.hook.apply(ctx)
                    }
                }
            }
        }
    }
}
