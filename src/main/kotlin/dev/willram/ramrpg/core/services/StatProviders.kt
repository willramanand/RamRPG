/**
 * Builtin StatProvider implementations: skill levels, equipment base stats
 * (with upgrade multiplier), enchantment StatEffects, reforge bonuses,
 * gem socket contributions.
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramrpg.api.effects.ScalingContext
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.StatPerLevelReward
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatContext
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.api.stats.StatProvider
import org.bukkit.entity.Player

private fun equippedStacks(player: Player) = with(player.equipment) {
    listOf(itemInMainHand, itemInOffHand, helmet, chestplate, leggings, boots)
}

class SkillStatProvider(
    private val skillRegistry: SkillRegistry,
    private val skillService: SkillService,
) : StatProvider {
    override fun provideStats(ctx: StatContext, output: MutableList<StatModifier>) {
        for (def in skillRegistry.all()) {
            val lvl = skillService.level(ctx.player, def.key) - 1
            if (lvl <= 0) continue
            for (r in def.rewards) {
                if (r is StatPerLevelReward) {
                    output += StatModifier(
                        stat = r.stat,
                        amount = lvl * r.amountPerLevel,
                        operation = ModifierOperation.ADD,
                        source = ModifierSource(SourceType.SKILL, def.key.id),
                    )
                }
            }
        }
    }
}

class EquipmentStatProvider(
    private val items: ItemInstanceService,
    private val defs: ItemDefinitionRegistry,
) : StatProvider {
    private val perUpgradeFactor = 0.05

    override fun provideStats(ctx: StatContext, output: MutableList<StatModifier>) {
        for (s in equippedStacks(ctx.player)) {
            if (s == null) continue
            val data = items.identify(s) ?: continue
            val def = defs.get(data.identity.key) ?: continue
            val upgradeMul = 1.0 + data.upgradeLevel * perUpgradeFactor
            for (m in def.baseStats) {
                output += if (upgradeMul == 1.0) m else m.copy(amount = m.amount * upgradeMul)
            }
            for ((statId, amt) in data.customRolls) {
                output += StatModifier(statId, amt * upgradeMul, ModifierOperation.ADD, ModifierSource(SourceType.ITEM, def.key.id))
            }
        }
    }
}

class EnchantmentStatProvider(
    private val items: ItemInstanceService,
    private val enchants: EnchantmentRegistry,
) : StatProvider {
    override fun provideStats(ctx: StatContext, output: MutableList<StatModifier>) {
        for (s in equippedStacks(ctx.player)) {
            if (s == null) continue
            val data = items.identify(s) ?: continue
            for ((ek, lvl) in data.enchantments) {
                val ench = enchants.get(ek) ?: continue
                val scaleCtx = LeveledScalingContext(lvl)
                for (eff in ench.effects(lvl)) {
                    if (eff !is StatEffect) continue
                    val amount = eff.amount.eval(scaleCtx)
                    output += StatModifier(eff.stat, amount, eff.operation, ModifierSource(SourceType.ENCHANT, ek.id))
                }
            }
        }
    }
}

private class LeveledScalingContext(override val level: Int) : ScalingContext {
    override fun statValue(key: StatKey): Double = 0.0
    override fun extra(key: String): Double? = null
}
