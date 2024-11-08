package dev.willram.ramrpg.source.farming

import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source

enum class FarmingSource(
    override val defaultXp: Double,
    val requiresFullyGrown: Boolean,
    val isRightClickHarvestable: Boolean,
    val isMultiblock: Boolean
) : Source {
    WHEAT(2.0,true, false, false),
    POTATOES(2.5,true, false, false),
    CARROTS(2.7,true, false, false),
    BEETROOTS(3.0,true, false, false),
    NETHER_WART(3.0,true, false, false),
    PUMPKIN(3.4,false, false, false),
    MELON(3.4,false, false, false),
    SUGAR_CANE(1.7,false, false, true),
    BAMBOO(0.25,false, false, true),
    COCOA(4.0,true, false, false),
    CACTUS(6.0,false, false, true),
    BROWN_MUSHROOM(7.0,false, false, false),
    RED_MUSHROOM(7.0,false, false, false),
    KELP(0.5,false, false, true),
    SEA_PICKLE(4.0,true, false, false),
    SWEET_BERRY_BUSH(2.5,true, true, false),
    GLOW_BERRIES(4.5,true, true, false),
    ;

    override val skill: Skill
        get() = Skill.FARMING
}