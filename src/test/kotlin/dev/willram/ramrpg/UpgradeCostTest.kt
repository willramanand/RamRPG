package dev.willram.ramrpg

import dev.willram.ramrpg.api.items.RarityRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpgradeCostTest {
    @Test
    fun `0 to 1 costs 3`() = assertEquals(3, RarityRules.upgradeXpCost(0, 1))

    @Test
    fun `0 to 3 costs 3+4+5 = 12`() = assertEquals(12, RarityRules.upgradeXpCost(0, 3))

    @Test
    fun `5 to 8 costs 8+9+10 = 27`() = assertEquals(27, RarityRules.upgradeXpCost(5, 8))

    @Test
    fun `same level zero cost`() = assertEquals(0, RarityRules.upgradeXpCost(5, 5))

    @Test
    fun `downgrade zero cost`() = assertEquals(0, RarityRules.upgradeXpCost(7, 3))
}
