package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceInit
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.StatRoll
import dev.willram.ramrpg.core.services.ItemInstanceServiceImpl
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-2.1b: pins the quality-scales-rolls formula `qualityScaledRoll(min, max, q) = min + q*(max-min)`
 * at the roll layer (no live server). Also proves `rollStats` positions rolls by quality
 * deterministically when [ItemInstanceInit.quality] is set, while the seeded RNG path is untouched.
 */
class QualityScalesRollsTest {

    private fun stubRegistry(def: ItemDefinition): ItemDefinitionRegistry = object : ItemDefinitionRegistry {
        override fun get(key: ItemKey) = if (key == def.key) def else null
        override fun register(owner: String, def: ItemDefinition) {}
        override fun unregisterOwner(owner: String) = 0
        override fun all() = listOf(def)
        override fun revision() = 0
    }

    @Test
    fun `formula lands a roll between min and max by quality`() {
        // rolled = min + quality*(max-min): endpoints exact, 0.5 is the midpoint.
        assertEquals(10.0, ItemInstanceServiceImpl.qualityScaledRoll(10.0, 20.0, 0.0))
        assertEquals(20.0, ItemInstanceServiceImpl.qualityScaledRoll(10.0, 20.0, 1.0))
        assertEquals(15.0, ItemInstanceServiceImpl.qualityScaledRoll(10.0, 20.0, 0.5))
        assertEquals(17.5, ItemInstanceServiceImpl.qualityScaledRoll(10.0, 20.0, 0.75))
    }

    @Test
    fun `quality 0_5 and 1_0 differ as expected`() {
        val mid = ItemInstanceServiceImpl.qualityScaledRoll(0.0, 100.0, 0.5)
        val top = ItemInstanceServiceImpl.qualityScaledRoll(0.0, 100.0, 1.0)
        assertNotEquals(mid, top)
        assertTrue(top > mid, "higher quality must yield the larger effective roll")
        assertEquals(50.0, mid)
        assertEquals(100.0, top)
    }

    @Test
    fun `formula is deterministic`() {
        val a = ItemInstanceServiceImpl.qualityScaledRoll(3.0, 9.0, 0.42)
        val b = ItemInstanceServiceImpl.qualityScaledRoll(3.0, 9.0, 0.42)
        assertEquals(a, b)
    }

    @Test
    fun `quality is coerced into 0 to 1`() {
        assertEquals(10.0, ItemInstanceServiceImpl.qualityScaledRoll(10.0, 20.0, -5.0))
        assertEquals(20.0, ItemInstanceServiceImpl.qualityScaledRoll(10.0, 20.0, 9.0))
    }

    @Test
    fun `fixed range collapses regardless of quality`() {
        assertEquals(7.0, ItemInstanceServiceImpl.qualityScaledRoll(7.0, 7.0, 0.0))
        assertEquals(7.0, ItemInstanceServiceImpl.qualityScaledRoll(7.0, 7.0, 1.0))
    }

    @Test
    fun `rollStats positions rolls by quality deterministically`() {
        val s1 = StatKey.of("test", "a")
        val s2 = StatKey.of("test", "b")
        val def = ItemDefinition(
            key = ItemKey.of("test", "q"),
            displayName = Component.text("Q"),
            material = Material.STICK,
            rarity = Rarity.COMMON,
            categories = setOf(ItemCategory.MISC),
            statRolls = listOf(StatRoll(s1, 1.0, 11.0), StatRoll(s2, 0.0, 20.0)),
        )
        val svc = ItemInstanceServiceImpl(stubRegistry(def))

        val rolls = svc.rollStats(def, ItemInstanceInit(quality = 0.5))
        assertEquals(6.0, rolls[s1])   // 1 + 0.5*(11-1)
        assertEquals(10.0, rolls[s2])  // 0 + 0.5*(20-0)

        // Deterministic: same quality -> identical rolls (no RNG involved).
        assertEquals(rolls, svc.rollStats(def, ItemInstanceInit(quality = 0.5)))

        // Higher quality rolls strictly higher within the band.
        val top = svc.rollStats(def, ItemInstanceInit(quality = 1.0))
        assertEquals(11.0, top[s1])
        assertEquals(20.0, top[s2])
    }
}
