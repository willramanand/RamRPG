package dev.willram.ramrpg.source.sorcery


import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source

enum class SorcerySource(override val defaultXp: Double) : Source {
    MANA_ABILITY_USE(2.5);

    override val skill: Skill
        get() = Skill.SORCERY
}