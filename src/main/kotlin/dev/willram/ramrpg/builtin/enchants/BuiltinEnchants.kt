/** Default RPGEnchantments mapping classic vanilla names onto Effect lists. */
package dev.willram.ramrpg.builtin.enchants

import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.combat.DamageTag
import dev.willram.ramrpg.api.effects.DamagePipelineEffect
import dev.willram.ramrpg.api.effects.DamageStageHook
import dev.willram.ramrpg.api.effects.Effect
import dev.willram.ramrpg.api.effects.Scaling
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.enchants.RPGEnchantment
import dev.willram.ramrpg.api.identity.EffectKey
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.builtin.identity.RamStats
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

private val WEAPON_CATS = setOf(ItemCategory.SWORD, ItemCategory.AXE, ItemCategory.MACE, ItemCategory.TRIDENT)
private val ARMOR_CATS = setOf(ItemCategory.HELMET, ItemCategory.CHESTPLATE, ItemCategory.LEGGINGS, ItemCategory.BOOTS)
private val MINING_CATS = setOf(ItemCategory.PICKAXE, ItemCategory.AXE, ItemCategory.SHOVEL)
private val BOW_CATS = setOf(ItemCategory.BOW, ItemCategory.CROSSBOW)

private fun ek(v: String) = EnchantmentKey.of("ramrpg", v)
private fun fk(v: String) = EffectKey.of("ramrpg", v)

open class StatPerLevel(
    override val key: EnchantmentKey,
    override val displayName: Component,
    override val maxLevel: Int,
    override val targets: Set<ItemCategory>,
    private val effectKey: EffectKey,
    private val stat: dev.willram.ramrpg.api.identity.StatKey,
    private val perLevel: Double,
    private val op: ModifierOperation = ModifierOperation.ADD,
) : RPGEnchantment {
    override fun effects(level: Int): List<Effect> =
        listOf(StatEffect(effectKey, stat, Scaling.linear(perLevel), op))
}

class Sharpness : RPGEnchantment {
    override val key = ek("sharpness")
    override val displayName = Component.text("Sharpness")
    override val maxLevel = 5
    override val targets = WEAPON_CATS
    override fun effects(level: Int): List<Effect> = listOf(
        DamagePipelineEffect(fk("sharpness"), DamagePriority.ENCHANT_OFFENSE, object : DamageStageHook {
            override fun apply(ctx: DamageContext) {
                ctx.finalDamage += level * 1.25
            }
        })
    )
}

class Critical : StatPerLevel(ek("critical"), Component.text("Critical"), 5, WEAPON_CATS, fk("critical_stat"), RamStats.CRIT_DAMAGE, 10.0)
class TrueStrike : StatPerLevel(ek("true_strike"), Component.text("True Strike"), 5, WEAPON_CATS, fk("true_strike_stat"), RamStats.CRIT_CHANCE, 5.0)
class Ferocious : StatPerLevel(ek("ferocious"), Component.text("Ferocious"), 5, WEAPON_CATS, fk("ferocious_stat"), RamStats.FEROCITY, 20.0)
class Fortune : StatPerLevel(ek("fortune"), Component.text("Fortune"), 5, MINING_CATS, fk("fortune_stat"), RamStats.FORTUNE, 1.0)
class Protection : StatPerLevel(ek("protection"), Component.text("Protection"), 5, ARMOR_CATS, fk("protection_stat"), RamStats.DEFENSE, 5.0)
class Growth : StatPerLevel(ek("growth"), Component.text("Growth"), 5, ARMOR_CATS, fk("growth_stat"), RamStats.HEALTH, 10.0)
class MendingHR : StatPerLevel(ek("mending"), Component.text("Mending"), 3, ARMOR_CATS, fk("mending_stat"), RamStats.HEALTH_REGEN, 1.0)

class Lifesteal : RPGEnchantment {
    override val key = ek("lifesteal")
    override val displayName = Component.text("Lifesteal")
    override val maxLevel = 3
    override val targets = WEAPON_CATS
    override fun effects(level: Int): List<Effect> = listOf(
        DamagePipelineEffect(fk("lifesteal_proc"), DamagePriority.LIFESTEAL + 1, object : DamageStageHook {
            override fun apply(ctx: DamageContext) {
                if (DamageTag.SECONDARY in ctx.tags) return
                val p = ctx.attacker as? Player ?: return
                val pct = level * 1.0
                val heal = ctx.finalDamage * pct / 100.0
                p.health = (p.health + heal).coerceAtMost(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: p.health)
            }
        })
    )
}

class Power : RPGEnchantment {
    override val key = ek("power")
    override val displayName = Component.text("Power")
    override val maxLevel = 5
    override val targets = BOW_CATS
    override fun effects(level: Int): List<Effect> = listOf(
        DamagePipelineEffect(fk("power"), DamagePriority.ENCHANT_OFFENSE, object : DamageStageHook {
            override fun apply(ctx: DamageContext) {
                ctx.finalDamage += level * 1.5
            }
        })
    )
}

object BuiltinEnchants {
    fun registerAll(reg: EnchantmentRegistry) {
        val owner = "ramrpg-builtin"
        for (e in listOf(
            Sharpness(), Critical(), TrueStrike(), Ferocious(), Fortune(),
            Protection(), Growth(), MendingHR(), Lifesteal(), Power(),
        )) reg.register(owner, e)
    }
}
