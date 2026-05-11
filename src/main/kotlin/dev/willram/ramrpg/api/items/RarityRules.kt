package dev.willram.ramrpg.api.items

object RarityRules {
    val socketCap: Map<Rarity, Int> = mapOf(
        Rarity.COMMON to 1,
        Rarity.UNCOMMON to 2,
        Rarity.RARE to 3,
        Rarity.EPIC to 4,
        Rarity.LEGENDARY to 5,
        Rarity.MYTHIC to 6,
    )

    val reforgeXpCost: Map<Rarity, Int> = mapOf(
        Rarity.COMMON to 3,
        Rarity.UNCOMMON to 6,
        Rarity.RARE to 10,
        Rarity.EPIC to 15,
        Rarity.LEGENDARY to 25,
        Rarity.MYTHIC to 40,
    )

    val upgradeCap: Map<Rarity, Int> = mapOf(
        Rarity.COMMON to 5,
        Rarity.UNCOMMON to 7,
        Rarity.RARE to 10,
        Rarity.EPIC to 12,
        Rarity.LEGENDARY to 15,
        Rarity.MYTHIC to 20,
    )

    fun socketCap(r: Rarity): Int = socketCap[r] ?: 1
    fun reforgeXpCost(r: Rarity): Int = reforgeXpCost[r] ?: 5
    fun upgradeCap(r: Rarity): Int = upgradeCap[r] ?: 5

    val upgradeCostMultiplier: Map<Rarity, Double> = mapOf(
        Rarity.COMMON to 1.0,
        Rarity.UNCOMMON to 1.5,
        Rarity.RARE to 2.0,
        Rarity.EPIC to 3.0,
        Rarity.LEGENDARY to 5.0,
        Rarity.MYTHIC to 8.0,
    )

    fun upgradeCostMultiplier(r: Rarity): Double = upgradeCostMultiplier[r] ?: 1.0

    /** Total xp levels required to upgrade from [from] to [to]. */
    fun upgradeXpCost(from: Int, to: Int): Int {
        if (to <= from) return 0
        var total = 0
        for (n in (from + 1)..to) total += 2 + n
        return total
    }

    /** Rarity-weighted version of [upgradeXpCost]. */
    fun upgradeXpCost(from: Int, to: Int, rarity: Rarity): Int =
        Math.ceil(upgradeXpCost(from, to) * upgradeCostMultiplier(rarity)).toInt()
}
