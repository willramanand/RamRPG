package dev.willram.ramrpg.source.mining

import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source

enum class MiningSource(override val defaultXp: Double = 5.0, val requiresSilkTouch: Boolean = false) : Source {
    STONE(2.0),
    COBBLESTONE(2.0),
    GRANITE(2.5),
    DIORITE(2.5),
    ANDESITE(2.5),
    COAL_ORE(10.0),
    IRON_ORE(18.0),
    NETHER_QUARTZ_ORE(19.0),
    REDSTONE_ORE(35.9),
    GOLD_ORE(17.8),
    LAPIS_ORE(20.6),
    DIAMOND_ORE(57.3),
    EMERALD_ORE(100.0),
    TERRACOTTA(0.5),
    WHITE_TERRACOTTA(0.5),
    ORANGE_TERRACOTTA(0.5),
    YELLOW_TERRACOTTA(0.5),
    LIGHT_GRAY_TERRACOTTA(0.5),
    BROWN_TERRACOTTA(0.5),
    RED_TERRACOTTA(0.5),
    NETHERRACK(1.0),
    BLACKSTONE(3.0),
    BASALT(4.0),
    MAGMA_BLOCK(7.0),
    NETHER_GOLD_ORE(10.0),
    ANCIENT_DEBRIS(500.0),
    END_STONE(5.0),
    OBSIDIAN(15.0),
    DEEPSLATE(4.9),
    COPPER_ORE(13.5),
    TUFF(0.5),
    CALCITE(0.8),
    SMOOTH_BASALT(0.8),
    AMETHYST_BLOCK(4.0),
    AMETHYST_CLUSTER(6.0),
    DEEPSLATE_COAL_ORE(12.0),
    DEEPSLATE_IRON_ORE(32.0),
    DEEPSLATE_COPPER_ORE(52.0),
    DEEPSLATE_GOLD_ORE(82.0),
    DEEPSLATE_REDSTONE_ORE(47.1),
    DEEPSLATE_EMERALD_ORE(130.0),
    DEEPSLATE_LAPIS_ORE(46.7),
    DEEPSLATE_DIAMOND_ORE(66.8),
    DRIPSTONE_BLOCK(3.2),
    ICE(5.0, true),
    PACKED_ICE(5.0, true),
    BLUE_ICE(5.0, true),
    SCULK(5.0),
    SCULK_VEIN(10.0),
    SCULK_CATALYST(20.0),
    SCULK_SHRIEKER(100.0);

    override val skill: Skill
        get() = Skill.MINING

}