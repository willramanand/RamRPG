package dev.willram.ramrpg

import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.RarityRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RarityRulesTest {
    @Test
    fun `socket cap monotonic increasing by rarity`() {
        val caps = Rarity.entries.map { RarityRules.socketCap(it) }
        for (i in 1 until caps.size) {
            assert(caps[i] >= caps[i - 1]) { "Cap regressed at $i: $caps" }
        }
        assertEquals(1, RarityRules.socketCap(Rarity.COMMON))
        assertEquals(6, RarityRules.socketCap(Rarity.MYTHIC))
    }

    @Test
    fun `reforge cost monotonic increasing by rarity`() {
        val costs = Rarity.entries.map { RarityRules.reforgeXpCost(it) }
        for (i in 1 until costs.size) {
            assert(costs[i] >= costs[i - 1]) { "Cost regressed at $i: $costs" }
        }
        assertEquals(3, RarityRules.reforgeXpCost(Rarity.COMMON))
        assertEquals(40, RarityRules.reforgeXpCost(Rarity.MYTHIC))
    }
}
