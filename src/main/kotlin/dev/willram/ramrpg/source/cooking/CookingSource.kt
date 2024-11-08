package dev.willram.ramrpg.source.cooking

import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source

enum class CookingSource(override val defaultXp: Double) : Source {
    ENCHANTED_GOLDEN_APPLE(250.0),
    GOLDEN_APPLE(75.0),
    APPLE(2.5),
    GOLDEN_CARROT(50.0),
    CARROT(5.0),
    COOKED_MUTTON(10.0),
    MUTTON(2.5),
    COOKED_PORKCHOP(10.0),
    PORKCHOP(2.5),
    COOKED_COD(5.0),
    COD(2.5),
    COOKED_SALMON(10.0),
    SALMON(1.0),
    PUFFERFISH(1.0),
    TROPICAL_FISH(1.0),
    COOKED_BEEF(10.0),
    BEEF(2.5),
    COOKED_CHICKEN(5.0),
    CHICKEN(2.5),
    COOKED_RABBIT(5.0),
    RABBIT(2.5),
    RABBIT_STEW(10.0),
    MUSHROOM_STEW(5.0),
    SUSPICIOUS_STEW(5.0),
    SWEET_BERRIES(2.5),
    COOKIE(15.0),
    CAKE(25.0),
    BREAD(10.0),
    POTATO(5.0),
    BAKED_POTATO(10.0),
    BEETROOT(2.5),
    BEETROOT_STEW(10.0),
    MELON(5.0),
    DRIED_KELP(1.0),
    GLOW_BERRIES(1.0),
    PUMPKIN_PIE(25.0),
    ;


    override val skill: Skill
        get() = Skill.COOKING
}