/** Starter daily quests. */
package dev.willram.ramrpg.builtin.quests

import dev.willram.ramrpg.api.quests.QuestDefinition
import dev.willram.ramrpg.api.quests.QuestGoal
import dev.willram.ramrpg.api.quests.QuestKey
import dev.willram.ramrpg.api.quests.QuestRegistry
import dev.willram.ramrpg.api.quests.QuestReward
import dev.willram.ramrpg.builtin.identity.RamSkills
import net.kyori.adventure.text.Component
import org.bukkit.Material

object BuiltinQuests {
    fun registerAll(reg: QuestRegistry) {
        val owner = "ramrpg-builtin"
        reg.register(owner, QuestDefinition(
            key = QuestKey.of("ramrpg", "daily_kill_zombies"),
            displayName = Component.text("Cull the Undead"),
            description = Component.text("Slay 20 zombies."),
            goal = QuestGoal.KillEntityProfile("zombie", 20),
            rewards = listOf(QuestReward.Currency(250.0), QuestReward.Xp(RamSkills.COMBAT, 100.0)),
            daily = true,
            category = "combat",
        ))
        reg.register(owner, QuestDefinition(
            key = QuestKey.of("ramrpg", "daily_mine_iron"),
            displayName = Component.text("Iron Veins"),
            description = Component.text("Mine 30 iron ore."),
            goal = QuestGoal.BreakBlocks(Material.IRON_ORE, 30),
            rewards = listOf(QuestReward.Currency(200.0), QuestReward.Xp(RamSkills.MINING, 150.0)),
            daily = true,
            category = "gathering",
        ))
        reg.register(owner, QuestDefinition(
            key = QuestKey.of("ramrpg", "daily_chop_logs"),
            displayName = Component.text("Lumberjack"),
            description = Component.text("Chop 50 oak logs."),
            goal = QuestGoal.BreakBlocks(Material.OAK_LOG, 50),
            rewards = listOf(QuestReward.Currency(150.0), QuestReward.Xp(RamSkills.WOODCUTTING, 120.0)),
            daily = true,
            category = "gathering",
        ))
        reg.register(owner, QuestDefinition(
            key = QuestKey.of("ramrpg", "daily_combat_grind"),
            displayName = Component.text("Battle-Hardened"),
            description = Component.text("Gain 500 Combat XP."),
            goal = QuestGoal.GainSkillXp(RamSkills.COMBAT, 500),
            rewards = listOf(QuestReward.Currency(300.0)),
            daily = true,
            category = "combat",
        ))
    }
}
