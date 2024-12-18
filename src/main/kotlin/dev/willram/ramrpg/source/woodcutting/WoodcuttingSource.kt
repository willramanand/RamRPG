package dev.willram.ramrpg.source.woodcutting

import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source

enum class WoodcuttingSource(override val defaultXp: Double = 5.0) : Source {
    OAK_LOG,
    OAK_WOOD,
    SPRUCE_LOG,
    SPRUCE_WOOD,
    BIRCH_LOG,
    BIRCH_WOOD,
    JUNGLE_LOG,
    JUNGLE_WOOD,
    ACACIA_LOG,
    ACACIA_WOOD,
    DARK_OAK_LOG,
    DARK_OAK_WOOD,
    CHERRY_LOG,
    CHERRY_WOOD,
    STRIPPED_OAK_LOG,
    STRIPPED_OAK_WOOD,
    STRIPPED_SPRUCE_LOG,
    STRIPPED_SPRUCE_WOOD,
    STRIPPED_BIRCH_LOG,
    STRIPPED_BIRCH_WOOD,
    STRIPPED_JUNGLE_LOG,
    STRIPPED_JUNGLE_WOOD,
    STRIPPED_ACACIA_LOG,
    STRIPPED_ACACIA_WOOD,
    STRIPPED_DARK_OAK_LOG,
    STRIPPED_DARK_OAK_WOOD,
    STRIPPED_CHERRY_LOG,
    STRIPPED_CHERRY_WOOD,
    OAK_LEAVES,
    SPRUCE_LEAVES,
    BIRCH_LEAVES,
    JUNGLE_LEAVES,
    ACACIA_LEAVES,
    DARK_OAK_LEAVES,
    CRIMSON_STEM,
    CRIMSON_HYPHAE,
    STRIPPED_CRIMSON_STEM,
    STRIPPED_CRIMSON_HYPHAE,
    WARPED_STEM,
    WARPED_HYPHAE,
    STRIPPED_WARPED_STEM,
    STRIPPED_WARPED_HYPHAE,
    NETHER_WART_BLOCK,
    WARPED_WART_BLOCK,
    MOSS_BLOCK,
    MOSS_CARPET,
    AZALEA,
    FLOWERING_AZALEA,
    AZALEA_LEAVES,
    FLOWERING_AZALEA_LEAVES,
    MANGROVE_LOG,
    MANGROVE_ROOTS,
    MUDDY_MANGROVE_ROOTS,
    MANGROVE_LEAVES;

    override val skill: Skill
        get() = Skill.WOODCUTTING
}