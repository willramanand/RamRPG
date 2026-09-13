package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.loot.LootContext
import dev.willram.ramcore.loot.LootGenerator
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.core.loot.RpgItemPayload
import dev.willram.ramrpg.core.loot.RpgLootTables
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class LootIndependentChanceTest {
    private val always = ItemKey.of("test", "always")
    private val never = ItemKey.of("test", "never")

    @Test
    fun `independent chances resolve per-entry`() {
        val table = RpgLootTables.builder(ContentId.of("test", "t"))
            .independent(always, 1.0)
            .independent(never, 0.0)
            .build()
        val generator = LootGenerator()
        repeat(25) { seed ->
            val items = generator.generate(table, LootContext.of("test"), Random(seed.toLong()))
                .rewards().map { (it.payload() as RpgItemPayload).item }
            assertTrue(always in items, "chance 1.0 entry always drops")
            assertTrue(never !in items, "chance 0.0 entry never drops")
        }
    }
}
