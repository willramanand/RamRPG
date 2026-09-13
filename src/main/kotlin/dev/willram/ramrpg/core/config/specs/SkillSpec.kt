/**
 * WP-1.5a: PURE spec for a `skills/` content entry -> [SkillDefinition]. Bukkit-free: [XpCurve] and
 * the [SkillReward] hierarchy are pure, so [toDefinition] lives here.
 *
 * HOCON shape:
 * ```
 * id = "ramrpg:combat"
 * name = "<red>Combat"
 * description = "<gray>Slay to grow stronger."
 * max-level = 50
 * bar-color = "RED"                 # Adventure BossBar.Color; defaults GREEN
 * xp-curve { type = "polynomial", base = 50.0, mul = 25.0 }   # or type = "linear"
 * rewards = [
 *   { type = "stat_per_level", stat = "ramrpg:strength", amount = 1.0 }
 *   { type = "unlock_ability", ability = "ramrpg:cleave", at-level = 10 }
 * ]
 * ```
 */
package dev.willram.ramrpg.core.config.specs

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.AbilityKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.skills.SkillDefinition
import dev.willram.ramrpg.api.skills.SkillReward
import dev.willram.ramrpg.api.skills.StatPerLevelReward
import dev.willram.ramrpg.api.skills.UnlockAbilityReward
import dev.willram.ramrpg.api.skills.XpCurve
import dev.willram.ramrpg.api.skills.XpCurves
import dev.willram.ramrpg.core.config.RpgContentSpec
import dev.willram.ramrpg.core.config.SpecNodes
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.spongepowered.configurate.ConfigurationNode

data class SkillSpec(
    val key: SkillKey,
    val displayName: Component,
    val description: Component,
    val maxLevel: Int,
    val xpCurve: XpCurve,
    val rewards: List<SkillReward>,
    val barColor: BossBar.Color,
) : RpgContentSpec {
    override val id: ContentId get() = key.id

    fun toDefinition(): SkillDefinition = SkillDefinition(
        key = key,
        displayName = displayName,
        description = description,
        maxLevel = maxLevel,
        xpCurve = xpCurve,
        rewards = rewards,
        barColor = barColor,
    )

    companion object {
        private const val DEFAULT_MAX_LEVEL = 50
        private const val DEFAULT_XP_BASE = 50.0
        private const val DEFAULT_XP_MUL = 25.0

        fun deserialize(node: ConfigurationNode): SkillSpec {
            val id = SpecNodes.requireId(node)
            return SkillSpec(
                key = SkillKey(id),
                displayName = SpecNodes.componentOr(node, "name", Component.text(id.value())),
                description = SpecNodes.componentOr(node, "description", Component.empty()),
                maxLevel = SpecNodes.intOr(node, "max-level", DEFAULT_MAX_LEVEL),
                xpCurve = xpCurve(node.node("xp-curve")),
                rewards = rewards(node),
                barColor = SpecNodes.enumOr(node, "bar-color", BossBar.Color.GREEN, "boss bar colour"),
            )
        }

        private fun xpCurve(curve: ConfigurationNode): XpCurve {
            if (curve.virtual()) return XpCurves.polynomial(DEFAULT_XP_BASE, DEFAULT_XP_MUL)
            val base = SpecNodes.doubleOr(curve, "base", DEFAULT_XP_BASE)
            val mul = SpecNodes.doubleOr(curve, "mul", DEFAULT_XP_MUL)
            return when (val type = curve.node("type").getString("polynomial").trim().lowercase()) {
                "polynomial" -> XpCurves.polynomial(base, mul)
                "linear" -> XpCurves.linear(base, mul)
                else -> throw ContentDeserializeException("unknown xp-curve type '$type'")
            }
        }

        private fun rewards(node: ConfigurationNode): List<SkillReward> =
            node.node("rewards").childrenList().map { reward ->
                when (val type = reward.node("type").getString()?.trim()?.lowercase()) {
                    "stat_per_level" -> StatPerLevelReward(
                        stat = StatKey(SpecNodes.requireIdAt(reward, "stat")),
                        amountPerLevel = SpecNodes.doubleOr(reward, "amount", 0.0),
                    )
                    "unlock_ability" -> UnlockAbilityReward(
                        ability = AbilityKey(SpecNodes.requireIdAt(reward, "ability")),
                        atLevel = SpecNodes.intOr(reward, "at-level", 0),
                    )
                    else -> throw ContentDeserializeException("unknown skill reward type '$type'")
                }
            }
    }
}
