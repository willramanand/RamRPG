/**
 * Skill definitions, XP curves, rewards, and per-source XP grants.
 * [SkillService.addXp] resolves the curve, levels up to [SkillDefinition.maxLevel],
 * and fires registered onLevelUp / onXpGain callbacks.
 */
package dev.willram.ramrpg.api.skills

import dev.willram.ramrpg.api.identity.AbilityKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

fun interface XpCurve { fun xpToReach(level: Int): Double }

object XpCurves {
    fun polynomial(base: Double, mul: Double) = XpCurve { lvl -> base + mul * lvl * lvl }
    fun linear(base: Double, mul: Double) = XpCurve { lvl -> base + mul * lvl }
}

sealed interface SkillReward
data class StatPerLevelReward(val stat: StatKey, val amountPerLevel: Double) : SkillReward
data class UnlockAbilityReward(val ability: AbilityKey, val atLevel: Int) : SkillReward

data class SkillDefinition(
    val key: SkillKey,
    val displayName: Component,
    val description: Component,
    val maxLevel: Int,
    val xpCurve: XpCurve,
    val rewards: List<SkillReward> = emptyList(),
    val barColor: BossBar.Color = BossBar.Color.GREEN,
)

interface XpContext {
    val player: Player
    val source: XpSource
    val target: LivingEntity?
    val multiplier: Double
}

interface XpSource {
    val key: XpSourceKey
    val skill: SkillKey
    fun xp(ctx: XpContext): Double
}

interface SkillRegistry {
    fun register(owner: String, def: SkillDefinition)
    fun unregisterOwner(owner: String): Int
    fun get(key: SkillKey): SkillDefinition?
    fun all(): Collection<SkillDefinition>
}

interface SkillService {
    fun addXp(p: Player, src: XpSource, target: LivingEntity? = null, multiplier: Double = 1.0)
    fun level(p: Player, skill: SkillKey): Int
    fun xp(p: Player, skill: SkillKey): Double
    fun setLevel(p: Player, skill: SkillKey, level: Int)
}
