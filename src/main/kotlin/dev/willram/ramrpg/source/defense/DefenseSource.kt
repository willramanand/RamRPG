package dev.willram.ramrpg.source.defense


import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source

enum class DefenseSource(override val defaultXp: Double) : Source {
    MOB_DAMAGE(2.5),
    PLAYER_DAMAGE(5.0);

    override val skill: Skill
        get() = Skill.DEFENSE
}