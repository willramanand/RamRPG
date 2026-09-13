package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.content.ContentRegistrar
import dev.willram.ramcore.content.spec.RegionSpec
import dev.willram.ramcore.region.RegionRuleEngine
import dev.willram.ramcore.region.RegionRule
import dev.willram.ramcore.region.RegionAction
import dev.willram.ramcore.region.RegionRuleResult
import dev.willram.ramcore.region.RegionShapes
import dev.willram.ramcore.serialize.Position
import dev.willram.ramcore.serialize.Region
import dev.willram.ramrpg.core.regions.LevelBand
import dev.willram.ramrpg.core.regions.LevelBandService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * WP-6.2: pure resolution-core tests for [LevelBandService.resolve] against the REAL RamCore region
 * engine (`RegionRuleEngine`/`RuleRegion`/`RegionSpec`/`ContentRegistrar.toRuleRegion`) -- not a fake.
 * `dev.willram.ramcore.serialize.Position.of(x, y, z, worldName: String)` and everything the region
 * engine touches from it (`RegionShapes`, `RuleRegion.contains`, `RegionRuleEngine.regionsAt`) never
 * reach Bukkit when built from a raw world-name string rather than a `Location`, so this genuinely
 * exercises the shipped bands.conf machinery off-server, not a hand-rolled stand-in.
 */
class LevelBandResolutionTest {

    private fun band(id: String, minLevel: Int): LevelBand =
        LevelBand(id = ContentId.parse(id), minLevel = minLevel, maxLevel = minLevel + 9, statMultiplier = 1.0)

    private fun cuboidSpec(priority: Int, minX: Double, minZ: Double, maxX: Double, maxZ: Double): RegionSpec {
        val min = Position.of(minX, 0.0, minZ, "world")
        val max = Position.of(maxX, 256.0, maxZ, "world")
        return RegionSpec(RegionShapes.cuboid(Region.of(min, max)), priority, emptyList())
    }

    private fun serviceOf(vararg entries: Pair<LevelBand, RegionSpec>): LevelBandService {
        val engine = RegionRuleEngine()
        val byId = LinkedHashMap<ContentId, LevelBand>()
        for ((levelBand, spec) in entries) {
            byId[levelBand.id] = levelBand
            engine.register("test", ContentRegistrar.toRuleRegion(levelBand.id, spec))
        }
        return LevelBandService(engine, byId, byId.values.sortedBy { it.minLevel })
    }

    @Test
    fun `a location inside a region resolves to that region's band`() {
        val inner = band("ramrpg:inner", minLevel = 1)
        val outer = band("ramrpg:outer", minLevel = 20)
        val service = serviceOf(
            inner to cuboidSpec(priority = 2, minX = -100.0, minZ = -100.0, maxX = 100.0, maxZ = 100.0),
            outer to cuboidSpec(priority = 1, minX = -1000.0, minZ = -1000.0, maxX = 1000.0, maxZ = 1000.0),
        )
        val worldSpawn = Position.of(0.0, 64.0, 0.0, "world")

        assertEquals(inner, service.resolve(Position.of(50.0, 64.0, 50.0, "world"), worldSpawn))
        assertEquals(outer, service.resolve(Position.of(500.0, 64.0, 500.0, "world"), worldSpawn))
    }

    @Test
    fun `on overlap the higher-priority region wins regardless of registration order`() {
        val low = band("ramrpg:low", minLevel = 1)
        val high = band("ramrpg:high", minLevel = 50)
        // `low`'s bigger, lower-priority region is registered FIRST; RegionRuleEngine.regionsAt must
        // still return `high` first (priority, not declaration order).
        val service = serviceOf(
            low to cuboidSpec(priority = 1, minX = -100.0, minZ = -100.0, maxX = 100.0, maxZ = 100.0),
            high to cuboidSpec(priority = 5, minX = -50.0, minZ = -50.0, maxX = 50.0, maxZ = 50.0),
        )
        val worldSpawn = Position.of(0.0, 64.0, 0.0, "world")

        assertEquals(high, service.resolve(Position.of(0.0, 64.0, 0.0, "world"), worldSpawn))
        // Outside the inner (high-priority) region but still inside the outer one: falls back to `low`.
        assertEquals(low, service.resolve(Position.of(75.0, 64.0, 0.0, "world"), worldSpawn))
    }

    @Test
    fun `an unrecognised higher-priority region is skipped in favour of the next matching band`() {
        val real = band("ramrpg:real", minLevel = 30)
        val engine = RegionRuleEngine()
        val byId = linkedMapOf(real.id to real)
        // A decoy region registered directly into the engine (as e.g. a non-band RamCore region system
        // might) that LevelBandService's bandsById does not recognise, at HIGHER priority than `real`'s
        // region and containing the same point.
        val decoyId = ContentId.of("ramrpg", "not_a_band")
        val decoySpec = cuboidSpec(priority = 10, minX = -100.0, minZ = -100.0, maxX = 100.0, maxZ = 100.0)
        // Registered at higher priority than the real band's region below, so RegionRuleEngine.regionsAt
        // returns this decoy FIRST -- resolve() must skip it (bandsById has no entry for it) and keep
        // looking, rather than stopping at the first result or returning null.
        engine.register("test", ContentRegistrar.toRuleRegion(decoyId, decoySpec))
        engine.register("test", ContentRegistrar.toRuleRegion(real.id, cuboidSpec(priority = 1, minX = -100.0, minZ = -100.0, maxX = 100.0, maxZ = 100.0)))
        val service = LevelBandService(engine, byId, byId.values.sortedBy { it.minLevel })

        val worldSpawn = Position.of(0.0, 64.0, 0.0, "world")
        assertEquals(real, service.resolve(Position.of(0.0, 64.0, 0.0, "world"), worldSpawn))
    }

    @Test
    fun `region evaluation via rules is unused -- containment alone decides the band`() {
        // LevelBandService uses RegionRuleEngine.regionsAt (shape containment + priority only), never
        // RegionRuleEngine.evaluate/RuleRegion.evaluate (action rules). A DENY rule on the region must
        // have no effect on band resolution.
        val id = ContentId.parse("ramrpg:denied_but_still_a_band")
        val denyRule = RegionRule.of("deny-all", RegionAction.BLOCK, 0, RegionRuleResult.DENY)
        val spec = RegionSpec(
            RegionShapes.cuboid(Region.of(Position.of(-10.0, 0.0, -10.0, "world"), Position.of(10.0, 200.0, 10.0, "world"))),
            1,
            listOf(denyRule),
        )
        val levelBand = LevelBand(id = id, minLevel = 5, maxLevel = 14, statMultiplier = 1.0)
        val engine = RegionRuleEngine()
        engine.register("test", ContentRegistrar.toRuleRegion(id, spec))
        val service = LevelBandService(engine, mapOf(id to levelBand), listOf(levelBand))

        val worldSpawn = Position.of(0.0, 64.0, 0.0, "world")
        assertEquals(levelBand, service.resolve(Position.of(0.0, 64.0, 0.0, "world"), worldSpawn))
    }
}
