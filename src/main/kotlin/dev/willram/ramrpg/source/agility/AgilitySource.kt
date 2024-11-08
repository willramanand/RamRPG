package dev.willram.ramrpg.source.agility

import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source


enum class AgilitySource(override val defaultXp: Double) : Source {
    FALL_DAMAGE(5.0),
    MOVE_PER_BLOCK(2.5),
    ;

    override val skill: Skill
        get() = Skill.AGILITY
}