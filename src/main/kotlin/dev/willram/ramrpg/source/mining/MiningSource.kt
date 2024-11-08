package dev.willram.ramrpg.source.mining

import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source

enum class MiningSource(override val defaultXp: Double = 5.0, val requiresSilkTouch: Boolean = false) : Source {
    STONE,
    COBBLESTONE,
    GRANITE,
    DIORITE,
    ANDESITE,
    COAL_ORE,
    IRON_ORE,
    NETHER_QUARTZ_ORE,
    REDSTONE_ORE,
    GOLD_ORE,
    LAPIS_ORE,
    DIAMOND_ORE,
    EMERALD_ORE,
    TERRACOTTA,
    WHITE_TERRACOTTA,
    ORANGE_TERRACOTTA,
    YELLOW_TERRACOTTA,
    LIGHT_GRAY_TERRACOTTA,
    BROWN_TERRACOTTA,
    RED_TERRACOTTA,
    NETHERRACK,
    BLACKSTONE,
    BASALT,
    MAGMA_BLOCK,
    NETHER_GOLD_ORE,
    ANCIENT_DEBRIS,
    END_STONE,
    OBSIDIAN,
    DEEPSLATE,
    COPPER_ORE,
    TUFF,
    CALCITE,
    SMOOTH_BASALT,
    AMETHYST_BLOCK,
    AMETHYST_CLUSTER,
    DEEPSLATE_COAL_ORE,
    DEEPSLATE_IRON_ORE,
    DEEPSLATE_COPPER_ORE,
    DEEPSLATE_GOLD_ORE,
    DEEPSLATE_REDSTONE_ORE,
    DEEPSLATE_EMERALD_ORE,
    DEEPSLATE_LAPIS_ORE,
    DEEPSLATE_DIAMOND_ORE,
    DRIPSTONE_BLOCK,
    ICE(5.0, true),
    PACKED_ICE(5.0, true),
    BLUE_ICE(5.0, true),
    SCULK,
    SCULK_VEIN,
    SCULK_CATALYST,
    SCULK_SHRIEKER;

    override val skill: Skill
        get() = Skill.MINING

}