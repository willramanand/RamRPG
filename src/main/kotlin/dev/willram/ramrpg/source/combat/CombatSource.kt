package dev.willram.ramrpg.source.combat

import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.bukkit.adapters.BukkitEntity
import io.lumine.mythic.core.mobs.ActiveMob
import org.bukkit.entity.LivingEntity

enum class CombatSource(override val defaultXp: Double = 5.0) : Source {
    ALLAY(5.0),
    ARMADILLO(5.0),
    AXOLOTL(3.5),
    BAT(1.0),
    BEE(3.0),
    BLAZE(15.0),
    BOGGED(15.0),
    BREEZE(25.0),
    CAMEL(5.0),
    CAT(1.0),
    CAVE_SPIDER(5.0),
    CHICKEN(1.0),
    COD(1.0),
    COW(2.0),
    CREEPER(15.0),
    DOLPHIN(3.0),
    DONKEY(2.0),
    DROWNED(7.0),
    ELDER_GUARDIAN(100.0),
    ENDERMAN(18.0),
    ENDERMITE(7.0),
    ENDER_DRAGON(15000.0),
    EVOKER(35.0),
    FOX(3.0),
    FROG(2.0),
    GHAST(17.0),
    GIANT(20.0),
    GLOW_SQUID(2.5),
    GOAT(3.0),
    GUARDIAN(15.0),
    HOGLIN(12.0),
    HORSE(2.0),
    HUSK(7.0),
    ILLUSIONER(17.0),
    IRON_GOLEM(17.0),
    LLAMA(4.0),
    MAGMA_CUBE(12.0),
    MOOSHROOM(2.0),
    MULE(2.0),
    OCELOT(1.0),
    PANDA(5.0),
    PARROT(1.0),
    PHANTOM(10.0),
    PIG(2.0),
    PIGLIN(5.0),
    PIGLIN_BRUTE(35.0),
    PILLAGER(12.0),
    PLAYER(50.0),
    POLAR_BEAR(7.0),
    PUFFERFISH(1.0),
    RABBIT(1.0),
    RAVAGER(100.0),
    SALMON(1.0),
    SHEEP(2.0),
    SHULKER(15.0),
    SILVERFISH(5.0),
    SKELETON(7.0),
    SKELETON_HORSE(3.0),
    SLIME(5.0),
    SNIFFER(5.0),
    SNOW_GOLEM(4.0),
    SPIDER(5.0),
    SQUID(2.0),
    STRAY(8.0),
    STRIDER(3.0),
    TADPOLE(0.2),
    TRADER_LLAMA(12.0),
    TROPICAL_FISH(1.0),
    TURTLE(2.0),
    VEX(10.0),
    VILLAGER(2.0),
    VINDICATOR(12.0),
    WANDERING_TRADER(2.0),
    WARDEN(15000.0),
    WITCH(9.0),
    WITHER(15000.0),
    WITHER_SKELETON(17.0),
    WOLF(4.0),
    ZOGLIN(12.0),
    ZOMBIE(7.0),
    ZOMBIE_HORSE(12.0),
    ZOMBIE_VILLAGER(7.0),
    ZOMBIFIED_PIGLIN(4.0),

    UNCOMMON_ZOMBIE(35.0),
    RARE_ZOMBIE(70.0),
    EPIC_ZOMBIE(140.0),
    LEGENDARY_ZOMBIE(280.0),

    UNCOMMON_CREEPER(30.0),
    RARE_CREEPER(60.0),
    EPIC_CREEPER(120.0),
    LEGENDARY_CREEPER(240.0),

    UNCOMMON_SKELETON(35.0),
    RARE_SKELETON(70.0),
    EPIC_SKELETON(140.0),
    LEGENDARY_SKELETON(280.0),

    UNCOMMON_ENDERMAN(40.0),
    RARE_ENDERMAN(80.0),
    EPIC_ENDERMAN(160.0),
    LEGENDARY_ENDERMAN(320.0),
    ;

    override val skill: Skill
        get() = Skill.COMBAT

    companion object {
        fun retrieve(entity: LivingEntity): CombatSource {
            var type = entity.type.name
            if (RamRPG.get().mythicMobsEnabled && MythicBukkit.inst().mobManager.isMythicMob(entity)) {
                type = ActiveMob(BukkitEntity(entity)).mobType.uppercase()
            }
            return try {
                valueOf(type)
            } catch (e: Exception) {
                ZOMBIE
            }
        }
    }
}