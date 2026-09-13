/** Default 27 vanilla mob profiles + 4 tier overlays each. */
package dev.willram.ramrpg.builtin.entities

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.loot.LootTable
import dev.willram.ramrpg.api.entities.EntityProfile
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.identity.EntityProfileKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.builtin.identity.RamSkills
import dev.willram.ramrpg.builtin.identity.RamStats
import dev.willram.ramrpg.core.loot.RpgLootTables

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

/** Independent-chance drops per tier. Base mobs drop nothing custom; elites/legendaries roll rares. */
private fun independentForTier(tier: String?): List<Pair<ItemKey, Double>> = when (tier) {
    "uncommon" -> listOf(ik("ember_charm") to 0.05)
    "rare" -> listOf(ik("ember_charm") to 0.20)
    "epic" -> listOf(ik("ember_charm") to 0.50, ik("rogue_blade") to 0.05)
    "legendary" -> listOf(ik("rogue_blade") to 0.15, ik("warden_husk_chest") to 0.05)
    else -> emptyList()
}

private fun bossIndependent(mob: String): List<Pair<ItemKey, Double>> = when (mob) {
    "warden" -> listOf(ik("warden_husk_chest") to 0.50, ik("dragon_fang") to 0.05)
    "ender_dragon" -> listOf(ik("dragon_fang") to 0.80)
    "wither" -> listOf(ik("dragon_fang") to 0.40, ik("warden_husk_chest") to 0.30)
    else -> emptyList()
}

private val COMMON_POOL = listOf(
    RpgLootTables.PoolItem(ik("ember_charm"), 60.0),
    RpgLootTables.PoolItem(ik("rogue_blade"), 25.0),
    RpgLootTables.PoolItem(ik("warden_husk_chest"), 10.0),
    RpgLootTables.PoolItem(ik("dragon_fang"), 5.0),
)

private fun poolFor(tier: String?, mob: String): Pair<List<RpgLootTables.PoolItem>, Int> = when {
    mob in setOf("warden", "ender_dragon", "wither", "elder_guardian") -> COMMON_POOL to 3
    tier == "legendary" -> COMMON_POOL to 2
    tier == "epic" -> COMMON_POOL to 1
    else -> emptyList<RpgLootTables.PoolItem>() to 0
}

/** Builds a LootTable from independent-chance drops plus a weighted pool, or null when there is none. */
private fun buildTable(id: String, independent: List<Pair<ItemKey, Double>>, pool: List<RpgLootTables.PoolItem>, rolls: Int): LootTable? {
    if (independent.isEmpty() && (pool.isEmpty() || rolls <= 0)) return null
    val b = RpgLootTables.builder(ContentId.of("ramrpg", "loot/$id"))
    for ((item, chance) in independent) b.independent(item, chance)
    b.weightedPool(rolls, pool)
    return b.build()
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
                lootTable = buildTable(s.id, bossIndependent(s.id), bossPool, bossRolls),
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
                    lootTable = buildTable("${tier}_${s.id}", independentForTier(tier), pool, rolls),
                ))
            }
        }
    }
}
