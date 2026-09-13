package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.loot.LootContext
import dev.willram.ramcore.loot.LootGenerator
import dev.willram.ramcore.loot.LootReward
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.core.loot.RpgItemPayload
import dev.willram.ramrpg.core.loot.RpgLootTables
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class RpgLootTableBuilderTest {
    private val a = ItemKey.of("test", "a")
    private val b = ItemKey.of("test", "b")

    private fun gen(table: dev.willram.ramcore.loot.LootTable, seed: Long = 42): List<LootReward> =
        LootGenerator().generate(table, LootContext.of("test"), Random(seed)).rewards()

    @Test
    fun `an independent entry with chance 1 always yields its item`() {
        val table = RpgLootTables.builder(ContentId.of("test", "t")).independent(a, 1.0).build()
        val rewards = gen(table)
        assertEquals(1, rewards.size)
        assertEquals(a, (rewards[0].payload() as RpgItemPayload).item)
    }

    @Test
    fun `a weighted pool yields exactly rolls picks from its entries`() {
        val table = RpgLootTables.builder(ContentId.of("test", "t"))
            .weightedPool(3, listOf(RpgLootTables.PoolItem(a, 10.0), RpgLootTables.PoolItem(b, 10.0)))
            .build()
        val rewards = gen(table)
        assertEquals(3, rewards.size)
        assertTrue(rewards.all { (it.payload() as RpgItemPayload).item in setOf(a, b) })
    }

    @Test
    fun `an empty builder produces a table that drops nothing`() {
        val builder = RpgLootTables.builder(ContentId.of("test", "t"))
        assertTrue(builder.isEmpty())
        assertTrue(gen(builder.build()).isEmpty())
    }
}
