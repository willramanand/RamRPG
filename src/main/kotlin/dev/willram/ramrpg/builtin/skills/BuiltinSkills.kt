/** Default 13 SkillDefinitions: combat, mining, woodcutting, etc. */
package dev.willram.ramrpg.builtin.skills

import dev.willram.ramrpg.api.skills.SkillDefinition
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.StatPerLevelReward
import dev.willram.ramrpg.api.skills.XpCurves
import dev.willram.ramrpg.builtin.identity.RamSkills
import dev.willram.ramrpg.builtin.identity.RamStats
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component

object BuiltinSkills {
    fun registerAll(reg: SkillRegistry) {
        fun s(key: dev.willram.ramrpg.api.identity.SkillKey, name: String, perLvl: dev.willram.ramrpg.api.identity.StatKey, amount: Double, color: BossBar.Color) {
            reg.register("ramrpg-builtin", SkillDefinition(
                key = key, displayName = Component.text(name), description = Component.text(""),
                maxLevel = 50, xpCurve = XpCurves.polynomial(50.0, 25.0),
                rewards = listOf(StatPerLevelReward(perLvl, amount)),
                barColor = color
            ))
        }
        s(RamSkills.COMBAT, "Combat", RamStats.STRENGTH, 1.0, BossBar.Color.RED)
        s(RamSkills.MINING, "Mining", RamStats.DEFENSE, 1.0, BossBar.Color.WHITE)
        s(RamSkills.WOODCUTTING, "Woodcutting", RamStats.HEALTH, 2.0, BossBar.Color.GREEN)
        s(RamSkills.FARMING, "Farming", RamStats.HEALTH, 2.0, BossBar.Color.YELLOW)
        s(RamSkills.FISHING, "Fishing", RamStats.HEALTH_REGEN, 0.5, BossBar.Color.BLUE)
        s(RamSkills.EXCAVATION, "Excavation", RamStats.FORTUNE, 0.5, BossBar.Color.YELLOW)
        s(RamSkills.FORAGING, "Foraging", RamStats.HEALTH, 2.0, BossBar.Color.GREEN)
        s(RamSkills.ENCHANTING, "Enchanting", RamStats.WISDOM, 1.0, BossBar.Color.PURPLE)
        s(RamSkills.ALCHEMY, "Alchemy", RamStats.WISDOM, 1.0, BossBar.Color.PINK)
        s(RamSkills.COOKING, "Cooking", RamStats.HEALTH_REGEN, 0.5, BossBar.Color.RED)
        s(RamSkills.DEFENSE, "Defense", RamStats.DEFENSE, 2.0, BossBar.Color.WHITE)
        s(RamSkills.AGILITY, "Agility", RamStats.SPEED, 1.0, BossBar.Color.GREEN)
        s(RamSkills.SORCERY, "Sorcery", RamStats.WISDOM, 2.0, BossBar.Color.PURPLE)
    }
}
