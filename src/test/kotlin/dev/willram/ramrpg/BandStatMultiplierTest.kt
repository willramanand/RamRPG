package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.core.regions.LevelBand
import dev.willram.ramrpg.core.regions.LevelBandService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-6.2: [LevelBandService.scale] is the pure core `EntitySpawnListener` uses to scale a spawned mob's
 * HP/DEF/DAMAGE by its resolved band's `statMultiplier`. Off-server -- no entity, no Bukkit.
 */
class BandStatMultiplierTest {

    private fun band(multiplier: Double): LevelBand =
        LevelBand(id = ContentId.parse("ramrpg:test_band"), minLevel = 1, maxLevel = 10, statMultiplier = multiplier)

    @Test
    fun `a null band leaves the value unscaled`() {
        assertEquals(20.0, LevelBandService.scale(20.0, null), 0.0001)
    }

    @Test
    fun `a band's statMultiplier scales the value by the right factor`() {
        assertEquals(200.0, LevelBandService.scale(20.0, band(multiplier = 10.0)), 0.0001)
        assertEquals(0.0, LevelBandService.scale(0.0, band(multiplier = 10.0)), 0.0001)
        assertEquals(7.5, LevelBandService.scale(5.0, band(multiplier = 1.5)), 0.0001)
    }

    @Test
    fun `a 1x multiplier is a no-op, same as an absent band`() {
        assertEquals(LevelBandService.scale(42.0, null), LevelBandService.scale(42.0, band(multiplier = 1.0)), 0.0001)
    }

    @Test
    fun `the shipped bands' multiplier ladder scales a baseline mob stat within the power-curve target bands`() {
        // Cross-checks the REAL packaged content/regions/bands.conf (docs/design/6.2-level-bands.md's
        // ladder), not a hand-rolled stand-in -- LevelBandService.fromPackagedResource does only
        // classpath + Configurate HOCON parsing, no Bukkit, so it is safe to load off-server here.
        val service = LevelBandService.fromPackagedResource()
        val bands = service.bands()
        assertEquals(5, bands.size, "bands.conf should ship exactly 5 bands")

        // A representative low-end baseline mob stat (e.g. a zombie's 20 HP, BuiltinEntities) scaled by
        // each band's multiplier should land at or above that band's docs/design/0.1-power-curve.md
        // "Mob HP" lower bound, and multipliers must strictly increase band over band.
        val lowerBoundsByOrdinal = listOf(20.0, 60.0, 200.0, 700.0, 2500.0)
        var previousMultiplier = 0.0
        for ((index, levelBand) in bands.withIndex()) {
            assertTrue(
                levelBand.statMultiplier > previousMultiplier,
                "band ${levelBand.id} multiplier must exceed the previous band's",
            )
            previousMultiplier = levelBand.statMultiplier
            val scaled = LevelBandService.scale(20.0, levelBand)
            assertTrue(
                scaled >= lowerBoundsByOrdinal[index] * 0.9,
                "band ${levelBand.id} (x${levelBand.statMultiplier}) scaled baseline 20 HP to $scaled, " +
                    "expected roughly >= ${lowerBoundsByOrdinal[index]}",
            )
        }
    }
}
