package dev.willram.ramrpg.source.alchemy

import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source


enum class AlchemySource(override val defaultXp: Double) : Source {
    AWKWARD(10.0),
    REGULAR(15.0),
    EXTENDED(25.0),
    UPGRADED(25.0),
    SPLASH(35.0),
    LINGERING(50.0),
    ;

    override val skill: Skill
        get() = Skill.ALCHEMY
}