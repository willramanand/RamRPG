package dev.willram.ramrpg.stats

enum class Stat(val displayName: String, val modifierName: String, val symbol: String, val prefix: String, val base: Double, val perLvl: Double) {
    BREAKING_POWER("Breaking Power", "breaking_power", "ⓟ", "<dark_green>", 0.0, 0.0),
    HEALTH( "Health", "health_points", "❤", "<red>", 20.0, 1.0),
    HEALTH_REGEN( "Health Regen", "health_regen_points", "❣", "<red>", 100.0, 0.0),
    DAMAGE( "Base Damage", "dmg_points", "❁", "<dark_red>", 1.0, 0.0),
    DEFENSE( "Defense", "defense_points", "❈", "<green>", 0.0, 0.5),
    TRUE_DEFENSE( "True Defense", "true_defense_points", "❈", "<white>", 0.0, 0.5),
    STRENGTH( "Strength", "str_points", "❁", "<dark_red>", 0.0, 2.5),
    FORTUNE( "Fortune", "fortune_points", "♠", "<gold>", 0.0, 2.0),
    FEROCITY("Ferocity", "ferocity_points", "⫽", "<red>", 0.0, 0.0),
    CRIT_CHANCE("Critical Chance", "critchance_points", "☣", "<dark_blue>", 15.0, 1.25),
    CRIT_DAMAGE("Critical Damage", "critdamage_points", "❉", "<blue>", 50.0, 1.0),
    ATTACK_SPEED( "Attack Speed", "attackspeed_points", "♦", "<dark_aqua>", 0.0, 0.35),
    SPEED("Speed", "movespeed_points", "✦", "<white>", 0.0, 2.0),
    MINING_SPEED("Mining Speed", "minespeed_points", "⸕", "<gold>", 0.0, 0.00),
    WISDOM( "Wisdom", "wisdom_points", "❃", "<aqua>", 100.0, 50.0),
}