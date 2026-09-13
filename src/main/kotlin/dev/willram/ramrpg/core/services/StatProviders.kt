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
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.isInert
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.StatPerLevelReward
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatContext
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.api.stats.StatProvider
import dev.willram.ramrpg.core.listeners.ItemRequirementServices
import dev.willram.ramrpg.core.listeners.requirementStateFor
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

private const val EQUIPMENT_PER_UPGRADE_FACTOR = 0.05

/**
 * WP-2.1c: [def]'s base-stat + custom-roll contribution, or empty when [def]/[data] [isInert] against
 * [state] -- the SAME shared check every item-based provider consults (see
 * `docs/design/2.1c-inert-items.md`). Extracted from [EquipmentStatProvider.provideStats] so it is
 * off-server testable without a live `Player`/`ItemStack`.
 */
internal fun equipmentStatsFor(def: ItemDefinition, data: ItemInstanceData, state: ItemRequirementState): List<StatModifier> {
    if (def.isInert(data, state)) return emptyList()
    val upgradeMul = 1.0 + data.upgradeLevel * EQUIPMENT_PER_UPGRADE_FACTOR
    val out = ArrayList<StatModifier>(def.baseStats.size + data.customRolls.size)
    for (m in def.baseStats) {
        out += if (upgradeMul == 1.0) m else m.copy(amount = m.amount * upgradeMul)
    }
    for ((statId, amt) in data.customRolls) {
        out += StatModifier(statId, amt * upgradeMul, ModifierOperation.ADD, ModifierSource(SourceType.ITEM, def.key.id))
    }
    return out
}

class EquipmentStatProvider(
    private val items: ItemInstanceService,
    private val defs: ItemDefinitionRegistry,
    /** WP-2.1c: optional -- see [ItemRequirementServices]'s KDoc for why this can't be required yet. */
    private val requirementServices: ItemRequirementServices? = null,
) : StatProvider {
    override fun provideStats(ctx: StatContext, output: MutableList<StatModifier>) {
        for (s in equippedStacks(ctx.player)) {
            if (s.type.isAir) continue
            val data = items.identify(s) ?: continue
            val def = defs.get(data.identity.key) ?: continue
            output += equipmentStatsFor(def, data, requirementStateFor(ctx.player, requirementServices))
        }
    }
}

/**
 * WP-2.1c: [data]'s enchantment StatEffect contribution, or empty when [def] is known and [isInert]
 * against [state]. [def] is nullable because [EnchantmentStatProvider] may run without an
 * [ItemDefinitionRegistry] wired yet (see [ItemRequirementServices]'s KDoc) -- when it's unavailable the
 * inert check cannot be evaluated (there is no `requirements` list to check without the definition), so
 * this fails OPEN for that specific structural gap only, not for an evaluated-and-unmet requirement.
 */
internal fun enchantmentStatsFor(
    def: ItemDefinition?,
    data: ItemInstanceData,
    state: ItemRequirementState,
    enchants: EnchantmentRegistry,
): List<StatModifier> {
    if (def != null && def.isInert(data, state)) return emptyList()
    val out = ArrayList<StatModifier>()
    for ((ek, lvl) in data.enchantments) {
        val ench = enchants.get(ek) ?: continue
        val scaleCtx = LeveledScalingContext(lvl)
        for (eff in ench.effects(lvl)) {
            if (eff !is StatEffect) continue
            val amount = eff.amount.eval(scaleCtx)
            out += StatModifier(eff.stat, amount, eff.operation, ModifierSource(SourceType.ENCHANT, ek.id))
        }
    }
    return out
}

class EnchantmentStatProvider(
    private val items: ItemInstanceService,
    private val enchants: EnchantmentRegistry,
    /** WP-2.1c: optional -- needed to look up the [ItemDefinition] for the inert check; see [enchantmentStatsFor]. */
    private val defs: ItemDefinitionRegistry? = null,
    /** WP-2.1c: optional -- see [ItemRequirementServices]'s KDoc for why this can't be required yet. */
    private val requirementServices: ItemRequirementServices? = null,
) : StatProvider {
    override fun provideStats(ctx: StatContext, output: MutableList<StatModifier>) {
        for (s in equippedStacks(ctx.player)) {
            if (s.type.isAir) continue
            val data = items.identify(s) ?: continue
            val def = defs?.get(data.identity.key)
            output += enchantmentStatsFor(def, data, requirementStateFor(ctx.player, requirementServices), enchants)
        }
    }
}

private class LeveledScalingContext(override val level: Int) : ScalingContext {
    override fun statValue(key: StatKey): Double = 0.0
    override fun extra(key: String): Double? = null
}
