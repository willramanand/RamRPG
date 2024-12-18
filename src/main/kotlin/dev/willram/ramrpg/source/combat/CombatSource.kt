package dev.willram.ramrpg.source.combat

import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source

enum class CombatSource(override val defaultXp: Double = 5.0) : Source {
    ALLAY,
    ARMADILLO,
    AXOLOTL,
    BAT,
    BEE,
    BLAZE,
    BOGGED,
    BREEZE,
    CAMEL,
    CAT,
    CAVE_SPIDER,
    CHICKEN,
    COD,
    COW,
    CREEPER,
    DOLPHIN,
    DONKEY,
    DROWNED,
    ELDER_GUARDIAN,
    ENDERMAN,
    ENDERMITE,
    ENDER_DRAGON,
    EVOKER,
    FOX,
    FROG,
    GHAST,
    GIANT,
    GLOW_SQUID,
    GOAT,
    GUARDIAN,
    HOGLIN,
    HORSE,
    HUSK,
    ILLUSIONER,
    IRON_GOLEM,
    LLAMA,
    MAGMA_CUBE,
    MOOSHROOM,
    MULE,
    OCELOT,
    PANDA,
    PARROT,
    PHANTOM,
    PIG,
    PIGLIN,
    PIGLIN_BRUTE,
    PILLAGER,
    PLAYER,
    POLAR_BEAR,
    PUFFERFISH,
    RABBIT,
    RAVAGER,
    SALMON,
    SHEEP,
    SHULKER,
    SILVERFISH,
    SKELETON,
    SKELETON_HORSE,
    SLIME,
    SNIFFER,
    SNOW_GOLEM,
    SPIDER,
    SQUID,
    STRAY,
    STRIDER,
    TADPOLE,
    TRADER_LLAMA,
    TROPICAL_FISH,
    TURTLE,
    VEX,
    VILLAGER,
    VINDICATOR,
    WANDERING_TRADER,
    WARDEN,
    WITCH,
    WITHER,
    WITHER_SKELETON,
    WOLF,
    ZOGLIN,
    ZOMBIE,
    ZOMBIE_HORSE,
    ZOMBIE_VILLAGER,
    ZOMBIFIED_PIGLIN;

    override val skill: Skill
        get() = Skill.COMBAT
}