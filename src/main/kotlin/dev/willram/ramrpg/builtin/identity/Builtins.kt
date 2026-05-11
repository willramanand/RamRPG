/** Builtin StatKey + SkillKey constants under namespace `ramrpg`. */
package dev.willram.ramrpg.builtin.identity

import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey

object RamStats {
    val DAMAGE = StatKey.of("ramrpg", "damage")
    val STRENGTH = StatKey.of("ramrpg", "strength")
    val HEALTH = StatKey.of("ramrpg", "health")
    val HEALTH_REGEN = StatKey.of("ramrpg", "health_regen")
    val DEFENSE = StatKey.of("ramrpg", "defense")
    val TRUE_DEFENSE = StatKey.of("ramrpg", "true_defense")
    val SPEED = StatKey.of("ramrpg", "speed")
    val ATTACK_SPEED = StatKey.of("ramrpg", "attack_speed")
    val CRIT_CHANCE = StatKey.of("ramrpg", "crit_chance")
    val CRIT_DAMAGE = StatKey.of("ramrpg", "crit_damage")
    val FEROCITY = StatKey.of("ramrpg", "ferocity")
    val LIFESTEAL = StatKey.of("ramrpg", "lifesteal")
    val FORTUNE = StatKey.of("ramrpg", "fortune")
    val WISDOM = StatKey.of("ramrpg", "wisdom")
}

object RamSkills {
    val COMBAT = SkillKey.of("ramrpg", "combat")
    val MINING = SkillKey.of("ramrpg", "mining")
    val WOODCUTTING = SkillKey.of("ramrpg", "woodcutting")
    val FARMING = SkillKey.of("ramrpg", "farming")
    val FISHING = SkillKey.of("ramrpg", "fishing")
    val EXCAVATION = SkillKey.of("ramrpg", "excavation")
    val FORAGING = SkillKey.of("ramrpg", "foraging")
    val ENCHANTING = SkillKey.of("ramrpg", "enchanting")
    val ALCHEMY = SkillKey.of("ramrpg", "alchemy")
    val COOKING = SkillKey.of("ramrpg", "cooking")
    val DEFENSE = SkillKey.of("ramrpg", "defense")
    val AGILITY = SkillKey.of("ramrpg", "agility")
    val SORCERY = SkillKey.of("ramrpg", "sorcery")
}
