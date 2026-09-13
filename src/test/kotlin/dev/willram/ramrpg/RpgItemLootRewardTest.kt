package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.loot.LootContext
import dev.willram.ramcore.loot.LootGenerator
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.core.loot.RPG_ITEM_REWARD_ID
import dev.willram.ramrpg.core.loot.RpgItemPayload
import dev.willram.ramrpg.core.loot.RpgLootTables
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * The loot reward is asserted at the payload layer (ItemKey + resolved ItemInstanceInit incl. seed),
 * not as a built ItemStack — building the stack needs Bukkit's ItemFactory (M10).
 */
class RpgItemLootRewardTest {
    private val item = ItemKey.of("test", "sword")

    @Test
    fun `a generated reward carries the item and a stamped roll seed`() {
        val table = RpgLootTables.builder(ContentId.of("test", "t")).independent(item, 1.0).build()
        val reward = LootGenerator().generate(table, LootContext.of("test"), Random(7)).rewards().single()

        assertEquals(RPG_ITEM_REWARD_ID, reward.id())
        val payload = reward.payload() as RpgItemPayload
        assertEquals(item, payload.item)
        assertNotNull(payload.init.rollSeed, "SEED function stamps a concrete roll seed for reproducible stats")
    }
}
