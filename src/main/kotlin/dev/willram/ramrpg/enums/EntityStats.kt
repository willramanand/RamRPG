package dev.willram.ramrpg.enums

import kotlin.random.Random

enum class EntityStats(val health: Double = 20.0, val defense: Double = 2.0, val damage: Double = 2.0) {
    ALLAY(20.0),
    ARMADILLO(12.0, 10.0),
    AXOLOTL(14.0, 1.0, 2.0),
    BAT(6.0, 0.0),
    BEE(10.0, 2.0, 3.0),
    BLAZE,
    BOGGED,
    BREEZE,
    CAMEL,
    CAT,
    CAVE_SPIDER(16.0, 4.0, 4.0),
    CHICKEN,
    COD,
    COW,
    CREEPER(20.0, 5.0, 20.0),
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
    HORSE(Random.nextInt(14, 24).toDouble()),
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
    POLAR_BEAR,
    PUFFERFISH,
    RABBIT,
    RAVAGER,
    SALMON,
    SHEEP,
    SHULKER,
    SILVERFISH,
    SKELETON(20.0, 5.0, 5.0),
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
    WARDEN(500.0, 200.0, 50.0),
    WITCH,
    WITHER,
    WITHER_SKELETON,
    WOLF,
    ZOGLIN,
    ZOMBIE(20.0, 5.0, 8.0),
    ZOMBIE_HORSE,
    ZOMBIE_VILLAGER,
    ZOMBIFIED_PIGLIN;
}