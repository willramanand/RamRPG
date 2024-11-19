package dev.willram.ramrpg.skills

import dev.willram.ramrpg.stats.Stat
import org.bukkit.boss.BarColor

enum class Skill(val displayName: String, val description: String, val barColor: BarColor, val maxLvl: Int, val base: Double, val multiplier: Double, val stats: List<Stat>) {
    AGILITY("Agility", "This skill deals with movement.", BarColor.GREEN, 50, 100.0, 1000.0, listOf(Stat.SPEED)),
    ALCHEMY("Alchemy", "This skill deals with brewing of potions.", BarColor.PURPLE, 50, 50.0, 50.0, listOf(Stat.WISDOM)),
    COOKING("Cooking", "This skill deals with the cooking of food.", BarColor.GREEN, 50, 100.0, 100.0, listOf(Stat.HEALTH)),
    COMBAT("Combat", "This skill deals with the killing of creatures.", BarColor.RED, 50, 100.0, 500.0,  listOf(Stat.CRIT_CHANCE, Stat.CRIT_DAMAGE, Stat.ATTACK_SPEED)),
    DEFENSE("Defense", "This skill deals with taking damage.", BarColor.RED, 50, 100.0, 500.0, listOf(Stat.DEFENSE)),
    ENCHANTING("Enchanting", "This skill deals with the enchanting of items.", BarColor.PURPLE, 50, 50.0, 50.0, listOf(Stat.WISDOM)),
    EXCAVATION("Excavation", "This skill deals with digging.", BarColor.BLUE, 50, 100.0, 500.0, listOf(Stat.DEFENSE)),
    FARMING("Farming", "This skill deals with the harvesting of crops.", BarColor.GREEN, 50, 100.0, 100.0, listOf(Stat.HEALTH, Stat.FORTUNE)),
    FISHING("Fishing", "This skill deals with fishing.", BarColor.GREEN, 50, 100.0, 250.0, listOf(Stat.HEALTH)),
    MINING("Mining", "This skill deals with the breaking of stones and ores.", BarColor.BLUE, 50, 100.0, 500.0, listOf(Stat.DEFENSE, Stat.FORTUNE)),
    SORCERY("Sorcery", "This skill deals with mana usage and abilities.", BarColor.PURPLE, 50, 100.0, 1000.0, listOf(Stat.WISDOM)),
    WOODCUTTING("Woodcutting", "This skill deals with the chopping of trees.", BarColor.BLUE, 50, 100.0, 500.0, listOf(Stat.STRENGTH, Stat.FORTUNE))
}