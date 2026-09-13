package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.region.RegionRuleEngine
import dev.willram.ramcore.serialize.Position
import dev.willram.ramrpg.core.regions.LevelBand
import dev.willram.ramrpg.core.regions.LevelBandService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * WP-6.2: pure math tests for [LevelBandService]'s distance-from-world-spawn fallback, used when no
 * registered region contains the query position (an open-world point with no hand-placed region, or a
 * world other than the one bands.conf's shapes name). The curve: bands ordered ascending by
 * [LevelBand.minLevel] form concentric [LevelBandService.RING_WIDTH]-block (1000) rings from world
 * spawn -- ring 0 is `[0, 1000)`, ring 1 is `[1000, 2000)`, etc. -- clamped to the last band past the
 * outermost ring. No RegionRuleEngine registration at all in these tests (an empty engine never matches
 * anything), so every assertion here exercises the fallback curve alone.
 */
class DistanceFallbackTest {

    private fun band(id: String, minLevel: Int): LevelBand =
        LevelBand(id = ContentId.parse(id), minLevel = minLevel, maxLevel = minLevel + 9, statMultiplier = 1.0)

    private fun serviceOf(vararg bands: LevelBand): LevelBandService {
        val byId = bands.associateBy { it.id }
        return LevelBandService(RegionRuleEngine(), byId, byId.values.sortedBy { it.minLevel })
    }

    private val b1 = band("ramrpg:band_1", 1)
    private val b2 = band("ramrpg:band_2", 11)
    private val b3 = band("ramrpg:band_3", 26)

    @Test
    fun `distance under one ring width stays in the first band`() {
        val service = serviceOf(b1, b2, b3)
        val spawn = Position.of(0.0, 64.0, 0.0, "world")

        assertEquals(b1, service.resolve(Position.of(0.0, 64.0, 0.0, "world"), spawn))
        assertEquals(b1, service.resolve(Position.of(999.0, 64.0, 0.0, "world"), spawn))
    }

    @Test
    fun `crossing a ring boundary advances exactly one band`() {
        val service = serviceOf(b1, b2, b3)
        val spawn = Position.of(0.0, 64.0, 0.0, "world")

        assertEquals(b2, service.resolve(Position.of(1000.0, 64.0, 0.0, "world"), spawn))
        assertEquals(b2, service.resolve(Position.of(1999.0, 64.0, 0.0, "world"), spawn))
        assertEquals(b3, service.resolve(Position.of(2000.0, 64.0, 0.0, "world"), spawn))
    }

    @Test
    fun `distance beyond the outermost ring clamps to the last band, never overshoots or throws`() {
        val service = serviceOf(b1, b2, b3)
        val spawn = Position.of(0.0, 64.0, 0.0, "world")

        assertEquals(b3, service.resolve(Position.of(1_000_000.0, 64.0, 0.0, "world"), spawn))
    }

    @Test
    fun `distance is horizontal only -- Y difference from spawn is ignored`() {
        val service = serviceOf(b1, b2, b3)
        val spawn = Position.of(0.0, 64.0, 0.0, "world")

        // Large vertical offset, tiny horizontal offset: still band_1.
        assertEquals(b1, service.resolve(Position.of(1.0, 500.0, 0.0, "world"), spawn))
    }

    @Test
    fun `diagonal XZ distance uses straight-line distance, not axis sum`() {
        val service = serviceOf(b1, b2, b3)
        val spawn = Position.of(0.0, 64.0, 0.0, "world")

        // (800, 800) is straight-line distance ~1131 (ring 1 = band_2), even though each axis alone
        // (800) is still under the 1000 ring width.
        assertEquals(b2, service.resolve(Position.of(800.0, 64.0, 800.0, "world"), spawn))
    }

    @Test
    fun `no loaded bands resolves to null rather than throwing`() {
        val service = LevelBandService(RegionRuleEngine(), emptyMap(), emptyList())
        val spawn = Position.of(0.0, 64.0, 0.0, "world")

        assertNull(service.resolve(Position.of(5000.0, 64.0, 0.0, "world"), spawn))
    }

    @Test
    fun `spawn is measured per-world -- distance is relative to the given worldSpawn, not the origin`() {
        val service = serviceOf(b1, b2, b3)
        val spawn = Position.of(5000.0, 64.0, 0.0, "world")

        // Right at this world's spawn point (which is far from the map origin) is still band_1.
        assertEquals(b1, service.resolve(Position.of(5000.0, 64.0, 0.0, "world"), spawn))
        assertEquals(b2, service.resolve(Position.of(6500.0, 64.0, 0.0, "world"), spawn))
    }
}
