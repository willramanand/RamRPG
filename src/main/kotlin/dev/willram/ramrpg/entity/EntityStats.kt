package dev.willram.ramrpg.entity

import dev.willram.ramrpg.RamRPG
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.bukkit.adapters.BukkitEntity
import io.lumine.mythic.core.mobs.ActiveMob
import org.bukkit.entity.LivingEntity
import kotlin.random.Random

enum class EntityStats(val health: Double = 20.0, val defense: Double = 2.0, val damage: Double = 2.0) {
    ALLAY       (20.0),
    ARMADILLO   (12.0, 10.0),
    AXOLOTL     (14.0, 1.0, 2.0),
    BAT         (6.0, 0.0),
    BEE         (10.0, 2.0, 3.0),
    BLAZE       (20.0, 10.0, 7.0),
    BOGGED      (16.0, 5.0, 4.0),
    BREEZE      (30.0, 10.0, 2.0),
    CAMEL       (32.0),
    CAT         (10.0, 2.0, 3.0),
    CAVE_SPIDER (16.0, 4.0, 4.0),
    CHICKEN     (4.0),
    COD         (3.0),
    COW         (10.0),
    CREEPER     (20.0, 5.0, 20.0),
    DOLPHIN     (10.0, 2.0, 4.5),
    DONKEY      (Random.nextInt(15, 30).toDouble()),
    DROWNED     (20.0, 5.0, 4.5),
    ELDER_GUARDIAN(80.0, 10.0, 12.0),
    ENDERMAN    (40.0, 7.0, 11.0),
    ENDERMITE   (8.0, 2.0, 4.0),
    ENDER_DRAGON(250.0, 50.0, 20.0),
    EVOKER      (24.0, 8.0, 24.0),
    FOX         (10.0, 2.0, 3.0),
    FROG        (10.0, 3.0),
    GHAST       (10.0, 7.0, 25.0),
    GIANT       (100.0, 15.0, 75.0),
    GLOW_SQUID  (10.0),
    GOAT        (10.0, 2.0, 3.0),
    GUARDIAN    (30.0, 10.0, 9.0),
    HOGLIN      (40.0, 7.0, 12.0),
    HORSE       (Random.nextInt(14, 24).toDouble()),
    HUSK        (20.0, 5.0, 4.5),
    ILLUSIONER  (32.0, 10.0, 5.0),
    IRON_GOLEM  (100.0, 20.0, 32.0),
    LLAMA       (Random.nextInt(15, 30).toDouble(), 1.0, 0.5),
    MAGMA_CUBE  (16.0, 5.0, 9.0),
    MOOSHROOM   (10.0),
    MULE        (Random.nextInt(15, 30).toDouble()),
    OCELOT      (10.0, 2.0, 3.0),
    PANDA       (20.0, 2.0, 9.0),
    PARROT      (6.0),
    PHANTOM     (20.0, 2.0, 4.0),
    PIG         (10.0),
    PIGLIN      (16.0, 2.0, 6.0),
    PIGLIN_BRUTE(50.0, 5.0, 10.5),
    PILLAGER    (24.0, 5.0, 4.5),
    POLAR_BEAR  (30.0, 5.0, 9.0),
    PUFFERFISH  (3.0, 0.0, 4.5),
    RABBIT      (3.0, 0.0, 12.0),
    RAVAGER     (100.0, 25.0, 20.0),
    SALMON      (3.0),
    SHEEP       (8.0),
    SHULKER     (30.0, 5.0, 5.0),
    SILVERFISH  (8.0, 2.0, 1.5),
    SKELETON    (20.0, 5.0, 5.0),
    SKELETON_HORSE (15.0),
    SLIME          (16.0, 5.0, 6.0),
    SNIFFER        (14.0),
    SNOW_GOLEM     (4.0, 2.0, 3.0),
    SPIDER         (16.0, 5.0, 4.0),
    SQUID          (10.0),
    STRAY          (20.0, 5.0, 3.0),
    STRIDER        (20.0),
    TADPOLE        (6.0),
    TRADER_LLAMA   (Random.nextInt(15, 30).toDouble()),
    TROPICAL_FISH  (3.0),
    TURTLE         (30.0),
    VEX            (14.0, 10.0, 13.5),
    VILLAGER       (20.0),
    VINDICATOR     (24.0),
    WANDERING_TRADER (20.0),
    WARDEN(500.0, 200.0, 50.0),
    WITCH          (26.0),
    WITHER         (600.0, 200.0, 25.0),
    WITHER_SKELETON(20.0, 5.0, 12.0),
    WOLF           (10.0, 2.0, 6.0),
    ZOGLIN         (40.0, 5.0, 12.0),
    ZOMBIE         (20.0, 5.0, 8.0),
    ZOMBIE_HORSE   (15.0),
    ZOMBIE_VILLAGER (20.0, 5.0, 8.0),
    ZOMBIFIED_PIGLIN (20.0, 5.0, 8.0),

    UNCOMMON_ZOMBIE(20.0, 15.0, 16.0),
    RARE_ZOMBIE(20.0, 15.0, 24.0),
    EPIC_ZOMBIE(20.0, 15.0, 32.0),
    LEGENDARY_ZOMBIE(20.0, 15.0, 48.0),

    UNCOMMON_CREEPER(20.0, 5.0, 30.0),
    RARE_CREEPER(20.0, 5.0, 50.0),
    EPIC_CREEPER(20.0, 5.0, 75.0),
    LEGENDARY_CREEPER(20.0, 5.0, 100.0),

    UNCOMMON_SKELETON(20.0, 15.0, 10.0),
    RARE_SKELETON(20.0, 15.0, 15.0),
    EPIC_SKELETON(20.0, 15.0, 25.0),
    LEGENDARY_SKELETON(20.0, 15.0, 35.0),

    UNCOMMON_ENDERMAN(40.0, 17.0, 22.0),
    RARE_ENDERMAN(40.0, 17.0, 33.0),
    EPIC_ENDERMAN(40.0, 17.0, 44.0),
    LEGENDARY_ENDERMAN(40.0, 17.0, 55.0),
    ;

    companion object {
        fun retrieve(entity: LivingEntity): EntityStats {
            var type = entity.type.name
            if (RamRPG.get().mythicMobsEnabled && MythicBukkit.inst().mobManager.isMythicMob(entity)) {
                type = ActiveMob(BukkitEntity(entity)).mobType.uppercase()
            }
            return try {
                EntityStats.valueOf(type)
            } catch (e: Exception) {
                ZOMBIE
            }
        }
    }
}