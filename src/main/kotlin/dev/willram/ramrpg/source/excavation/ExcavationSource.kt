package dev.willram.ramrpg.source.excavation


import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source

enum class ExcavationSource(override val defaultXp: Double) : Source {
    DIRT(3.0),
    GRASS_BLOCK(7.0),
    SAND(4.0),
    GRAVEL(1.5),
    MYCELIUM(3.7),
    CLAY(2.4),
    SOUL_SAND(2.7),
    COARSE_DIRT(2.3),
    PODZOL(2.5),
    SOUL_SOIL(3.0),
    RED_SAND(0.7),
    ROOTED_DIRT(5.6),
    MUD(3.0);

    override val skill: Skill
        get() = Skill.EXCAVATION
}