package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.core.services.DurabilityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * WP-2.2: pins `DurabilityService.damage` -- the pure operation the durability-drain
 * `DamageStage` (`DurabilityDrainStage`, `builtin/stats/DamageStages.kt`) calls on every landed hit. No
 * live `Player`/`ItemStack`/inventory is needed to prove the arithmetic: a weapon used N times loses
 * exactly `N * DRAIN_PER_HIT`, clamped within `[0, maxDurability]`.
 */
class DurabilityDrainOnHitTest {

    private val service = DurabilityService()

    private fun instance(durability: Int = 500, max: Int = 500): ItemInstanceData = ItemInstanceData(
        identity = ItemIdentity(key = ItemKey.of("ramrpg", "test_sword")),
        durability = durability,
        maxDurability = max,
    )

    @Test
    fun `damage reduces durability by the drain amount`() {
        val data = instance()
        val after = service.damage(data, DurabilityService.DRAIN_PER_HIT)
        assertEquals(500 - DurabilityService.DRAIN_PER_HIT, after.durability)
    }

    @Test
    fun `a weapon used N times loses N times the drain rate`() {
        var data = instance(durability = 500, max = 500)
        val hits = 37
        repeat(hits) { data = service.damage(data, DurabilityService.DRAIN_PER_HIT) }
        assertEquals(500 - hits * DurabilityService.DRAIN_PER_HIT, data.durability)
    }

    @Test
    fun `armor drains at its own rate independently of the weapon rate`() {
        var data = instance(durability = 200, max = 200)
        val hits = 15
        repeat(hits) { data = service.damage(data, DurabilityService.DRAIN_PER_ARMOR_HIT) }
        assertEquals(200 - hits * DurabilityService.DRAIN_PER_ARMOR_HIT, data.durability)
    }

    @Test
    fun `damage is a no-op for non-positive amounts, returning the same instance`() {
        val data = instance()
        assertSame(data, service.damage(data, 0))
        assertSame(data, service.damage(data, -5))
    }

    @Test
    fun `damage leaves every other field untouched`() {
        val data = instance().copy(customName = "Excalibur", upgradeLevel = 4)
        val after = service.damage(data, 10)
        assertEquals(data.identity, after.identity)
        assertEquals(data.customName, after.customName)
        assertEquals(data.upgradeLevel, after.upgradeLevel)
        assertEquals(data.maxDurability, after.maxDurability)
        assertEquals(490, after.durability)
    }
}
