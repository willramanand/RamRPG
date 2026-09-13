package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.core.services.DurabilityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-2.2: durability drains but the item is never destroyed -- draining arbitrarily far past 0 clamps at
 * 0 (never negative), and draining an already-zero instance is a stable no-op that never "removes" it.
 *
 * The live guarantee this backs -- an RPG weapon actually staying in a player's hand at 0 durability, and
 * the vanilla `PlayerItemDamageEvent` path never breaking/deleting it -- needs a live
 * `Player`/`ItemStack`/inventory (`DurabilityListener`, `DurabilityDrainStage`) and is covered instead by
 * `docs/SMOKE_TEST.md` row 9. Per the WP's off-server test requirement, this test pins the pure
 * `DurabilityService`/`ItemInstanceData` layer only.
 */
class DurabilityNeverBreaksTest {

    private val service = DurabilityService()

    private fun instance(durability: Int, max: Int = 10): ItemInstanceData = ItemInstanceData(
        identity = ItemIdentity(key = ItemKey.of("ramrpg", "test_sword")),
        durability = durability,
        maxDurability = max,
    )

    @Test
    fun `draining past zero clamps at zero, never negative`() {
        val data = instance(durability = 2)
        val after = service.damage(data, 999)
        assertEquals(0, after.durability)
        assertTrue(after.durability >= 0)
    }

    @Test
    fun `draining an already-zero instance stays at zero`() {
        val data = instance(durability = 0)
        val after = service.damage(data, 50)
        assertEquals(0, after.durability)
    }

    @Test
    fun `repeated draining beyond zero never goes negative across many hits`() {
        var data = instance(durability = 5)
        repeat(100) { data = service.damage(data, DurabilityService.DRAIN_PER_HIT) }
        assertEquals(0, data.durability)
    }

    @Test
    fun `repair never exceeds maxDurability`() {
        val data = instance(durability = 8, max = 10)
        val after = service.repair(data, 999)
        assertEquals(10, after.durability)
    }

    @Test
    fun `identity and every other field survive draining to zero unchanged -- the instance is never destroyed`() {
        val data = instance(durability = 1).copy(upgradeLevel = 3, customName = "Doombringer")
        val after = service.damage(data, 100)
        assertEquals(0, after.durability)
        assertEquals(data.identity, after.identity)
        assertEquals(data.upgradeLevel, after.upgradeLevel)
        assertEquals(data.customName, after.customName)
        assertEquals(data.maxDurability, after.maxDurability)
    }
}
