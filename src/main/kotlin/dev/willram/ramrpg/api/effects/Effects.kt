/**
 * Effect model used by enchants, items, reforges. [StatEffect] feeds
 * [StatService] via providers. [DamagePipelineEffect] hooks the combat
 * pipeline at a given priority. [TriggeredEffect] runs an action on a
 * specific [EffectTrigger] (OnHit/OnKill/Tick/etc).
 */
package dev.willram.ramrpg.api.effects

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.identity.EffectKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.stats.ModifierOperation
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

interface ScalingContext {
    val level: Int
    fun statValue(key: StatKey): Double
    fun extra(key: String): Double?
}

fun interface ScalingFormula { fun eval(ctx: ScalingContext): Double }

object Scaling {
    fun flat(v: Double) = ScalingFormula { v }
    fun linear(perLevel: Double) = ScalingFormula { it.level * perLevel }
    fun linearWithBase(base: Double, perLevel: Double) = ScalingFormula { base + it.level * perLevel }
}

enum class InteractType { LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT }

fun interface BlockMatcher { fun matches(block: Block): Boolean }

object BlockMatchers {
    fun ofMaterials(vararg mats: Material): BlockMatcher {
        val set = mats.toSet()
        return BlockMatcher { it.type in set }
    }
    val ANY: BlockMatcher = BlockMatcher { true }
}

sealed interface EffectTrigger {
    data object OnEquip : EffectTrigger
    data object OnHit : EffectTrigger
    data object OnHurt : EffectTrigger
    data object OnKill : EffectTrigger
    data class OnInteract(val type: InteractType) : EffectTrigger
    data class OnBlockBreak(val matcher: BlockMatcher) : EffectTrigger
    data object Tick : EffectTrigger
    data class Custom(val key: ContentId) : EffectTrigger
}

interface EffectContext {
    val source: LivingEntity?
    val target: LivingEntity?
    val player: Player?
    val level: Int
    val damage: DamageContext?
    val extra: MutableMap<String, Any>
}

fun interface EffectAction { fun execute(ctx: EffectContext) }
fun interface Condition { fun test(ctx: EffectContext): Boolean }

sealed interface Effect { val key: EffectKey }

data class StatEffect(
    override val key: EffectKey,
    val stat: StatKey,
    val amount: ScalingFormula,
    val operation: ModifierOperation,
) : Effect

interface DamageStageHook { fun apply(ctx: DamageContext) }

data class DamagePipelineEffect(
    override val key: EffectKey,
    val priority: Int,
    val hook: DamageStageHook,
) : Effect

data class TriggeredEffect(
    override val key: EffectKey,
    val trigger: EffectTrigger,
    val conditions: List<Condition> = emptyList(),
    val action: EffectAction,
) : Effect
