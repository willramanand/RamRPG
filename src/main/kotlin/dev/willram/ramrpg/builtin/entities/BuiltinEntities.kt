/** Default 27 vanilla mob profiles + 4 tier overlays each. */
package dev.willram.ramrpg.builtin.entities

import dev.willram.ramrpg.api.entities.EntityProfile
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.entities.LootEntry
import dev.willram.ramrpg.api.identity.EntityProfileKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.builtin.identity.RamSkills
import dev.willram.ramrpg.builtin.identity.RamStats

private fun pk(v: String) = EntityProfileKey.of("ramrpg", v)
private fun xk(v: String) = XpSourceKey.of("ramrpg", v)

private data class Spec(val id: String, val hp: Double, val def: Double, val dmg: Double, val xp: Double)

private val SPECS = listOf(
    Spec("zombie", 20.0, 5.0, 8.0, 7.0),
    Spec("skeleton", 20.0, 5.0, 5.0, 7.0),
    Spec("creeper", 20.0, 5.0, 20.0, 15.0),
    Spec("spider", 16.0, 5.0, 4.0, 5.0),
    Spec("cave_spider", 16.0, 4.0, 4.0, 5.0),
    Spec("enderman", 40.0, 7.0, 11.0, 18.0),
    Spec("blaze", 20.0, 10.0, 7.0, 15.0),
    Spec("witch", 26.0, 0.0, 2.0, 9.0),
    Spec("husk", 20.0, 5.0, 4.5, 7.0),
    Spec("drowned", 20.0, 5.0, 4.5, 7.0),
    Spec("wither_skeleton", 20.0, 5.0, 12.0, 17.0),
    Spec("phantom", 20.0, 2.0, 4.0, 10.0),
    Spec("ghast", 10.0, 7.0, 25.0, 17.0),
    Spec("piglin", 16.0, 2.0, 6.0, 5.0),
    Spec("piglin_brute", 50.0, 5.0, 10.5, 35.0),
    Spec("hoglin", 40.0, 7.0, 12.0, 12.0),
    Spec("zoglin", 40.0, 5.0, 12.0, 12.0),
    Spec("magma_cube", 16.0, 5.0, 9.0, 12.0),
    Spec("slime", 16.0, 5.0, 6.0, 5.0),
    Spec("ravager", 100.0, 25.0, 20.0, 100.0),
    Spec("vindicator", 24.0, 0.0, 13.0, 12.0),
    Spec("evoker", 24.0, 8.0, 24.0, 35.0),
    Spec("pillager", 24.0, 5.0, 4.5, 12.0),
    Spec("vex", 14.0, 10.0, 13.5, 10.0),
    Spec("warden", 500.0, 200.0, 50.0, 15000.0),
    Spec("ender_dragon", 250.0, 50.0, 20.0, 15000.0),
    Spec("wither", 600.0, 200.0, 25.0, 15000.0),
)

private val TIERS = listOf(
    "uncommon" to 2.0, "rare" to 4.0, "epic" to 8.0, "legendary" to 16.0,
)

private fun ik(v: String) = ItemKey.of("ramrpg", v)

/** Loot per tier. Base mobs drop nothing custom; elites/legendaries roll rare drops. */
private fun lootForTier(tier: String?, mob: String): List<LootEntry> = when (tier) {
    null -> emptyList()
    "uncommon" -> listOf(LootEntry(ik("ember_charm"), chance = 0.05))
    "rare" -> listOf(LootEntry(ik("ember_charm"), chance = 0.20))
    "epic" -> listOf(
        LootEntry(ik("ember_charm"), chance = 0.50),
        LootEntry(ik("rogue_blade"), chance = 0.05),
    )
    "legendary" -> listOf(
        LootEntry(ik("rogue_blade"), chance = 0.15),
        LootEntry(ik("warden_husk_chest"), chance = 0.05),
    )
    else -> emptyList()
}

private fun bossLoot(mob: String): List<LootEntry> = when (mob) {
    "warden" -> listOf(LootEntry(ik("warden_husk_chest"), chance = 0.50), LootEntry(ik("dragon_fang"), chance = 0.05))
    "ender_dragon" -> listOf(LootEntry(ik("dragon_fang"), chance = 0.80))
    "wither" -> listOf(LootEntry(ik("dragon_fang"), chance = 0.40), LootEntry(ik("warden_husk_chest"), chance = 0.30))
    else -> emptyList()
}

private val COMMON_POOL = listOf(
    LootEntry(ik("ember_charm"), weight = 60.0),
    LootEntry(ik("rogue_blade"), weight = 25.0),
    LootEntry(ik("warden_husk_chest"), weight = 10.0),
    LootEntry(ik("dragon_fang"), weight = 5.0),
)

private fun poolFor(tier: String?, mob: String): Pair<List<LootEntry>, Int> = when {
    mob in setOf("warden", "ender_dragon", "wither", "elder_guardian") -> COMMON_POOL to 3
    tier == "legendary" -> COMMON_POOL to 2
    tier == "epic" -> COMMON_POOL to 1
    else -> emptyList<LootEntry>() to 0
}

object BuiltinEntities {
    fun registerAll(reg: EntityProfileRegistry) {
        val owner = "ramrpg-builtin"
        for (s in SPECS) {
            val boss = s.id in setOf("warden", "ender_dragon", "wither", "elder_guardian")
            val (bossPool, bossRolls) = poolFor(null, s.id)
            reg.register(owner, EntityProfile(
                key = pk(s.id),
                baseStats = mapOf(
                    RamStats.HEALTH to s.hp,
                    RamStats.DEFENSE to s.def,
                    RamStats.DAMAGE to s.dmg,
                ),
                xpSourceKey = xk("kill_${s.id}"),
                xpAmount = s.xp,
                skill = RamSkills.COMBAT,
                loot = bossLoot(s.id),
                lootPool = bossPool,
                lootRolls = bossRolls,
                isBoss = boss,
            ))
            for ((tier, mult) in TIERS) {
                val (pool, rolls) = poolFor(tier, s.id)
                reg.register(owner, EntityProfile(
                    key = pk("${tier}_${s.id}"),
                    baseStats = mapOf(
                        RamStats.HEALTH to s.hp * mult,
                        RamStats.DEFENSE to s.def * mult,
                        RamStats.DAMAGE to s.dmg * mult,
                    ),
                    xpSourceKey = xk("kill_${tier}_${s.id}"),
                    xpAmount = s.xp * mult * 2.0,
                    skill = RamSkills.COMBAT,
                    loot = lootForTier(tier, s.id),
                    lootPool = pool,
                    lootRolls = rolls,
                ))
            }
        }
    }
}
