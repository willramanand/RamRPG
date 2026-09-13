package dev.willram.ramrpg

import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.crafting.Ingredient
import dev.willram.ramrpg.api.crafting.RecipeKey
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.RecipeOutcomeKind
import dev.willram.ramrpg.api.crafting.StationKey
import dev.willram.ramrpg.builtin.reforges.BuiltinReforges
import dev.willram.ramrpg.builtin.sockets.BuiltinGems
import dev.willram.ramrpg.core.config.ContentRegistrarRpg
import dev.willram.ramrpg.core.config.RpgContentLoadResult
import dev.willram.ramrpg.core.config.RpgContentLoader
import dev.willram.ramrpg.core.modules.ContentModule
import dev.willram.ramrpg.core.services.EnchantmentRegistryImpl
import dev.willram.ramrpg.core.services.EntityProfileRegistryImpl
import dev.willram.ramrpg.core.services.GemRegistryImpl
import dev.willram.ramrpg.core.services.ItemDefinitionRegistryImpl
import dev.willram.ramrpg.core.services.RecipeRegistryImpl
import dev.willram.ramrpg.core.services.ReforgeRegistryImpl
import dev.willram.ramrpg.core.services.SkillRegistryImpl
import dev.willram.ramrpg.core.services.StationRegistryImpl
import dev.willram.ramrpg.core.services.StatServiceImpl
import org.bukkit.Material
import org.bukkit.block.Block
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * WP-3.1d: the crafting content LOADING pipeline end-to-end.
 *
 * Proves the shipped `recipes/` + `stations/` confs are now first-class content types that load through
 * [RpgContentLoader] and register, owner-scoped, into the RecipeRegistry / StationRegistry via
 * [ContentRegistrarRpg] -- the "close the loading half" mandate. It exercises the SAME packaged crafting
 * pack + extraction path [dev.willram.ramrpg.core.modules.ContentModule] runs at enable (rather than a
 * synthetic fixture) so a break in the real confs, the station id wiring, or the registrar resolution
 * fails here. Errors AGGREGATE and never throw; a `/rpg reload` unregisters recipes + stations by owner.
 *
 * Pure / off-server: the only Bukkit surfaces are the [Material] enum and a [ProxyFakes]-backed [Block]
 * (no running server), so the whole pipeline -- BlockMatcher + RamCore MenuView included -- runs in a
 * plain unit test.
 */
class CraftingContentPipelineTest {

    private val smithing = StationKey.of("ramrpg", "smithing")
    private val craftingBench = StationKey.of("ramrpg", "crafting_bench")

    /** Extracts the shipped crafting pack into [dir] exactly as ContentModule does, then loads it. */
    private fun loadPackagedCrafting(dir: Path): RpgContentLoadResult {
        RpgContentLoader.extractPackagedContent(dir, paths = RpgContentLoader.packagedCraftingContent())
        return RpgContentLoader.load(dir)
    }

    // -- Loading -----------------------------------------------------------------------------------

    @Test
    fun `packaged crafting content parses into recipes and stations`(@TempDir dir: Path) {
        val result = loadPackagedCrafting(dir)

        assertTrue(result.errors().isEmpty(), "shipped crafting pack must parse clean: ${result.errors()}")
        assertEquals(
            setOf(smithing, craftingBench), result.stations.map { it.key }.toSet(),
            "the smithing table + crafting bench stations must load",
        )
        assertTrue(result.recipes.isNotEmpty(), "recipes/ must load recipe specs")
        assertTrue(
            result.recipes.all { it.station == smithing || it.station == craftingBench },
            "every shipped recipe binds to a shipped station",
        )
        // WP-3.1e: the `new_item` refine recipes bind to the crafting bench (NEW_ITEM is DISALLOWED at smithing).
        assertTrue(
            result.recipes.filter { it.outcome is RecipeOutcome.NewItem }.all { it.station == craftingBench },
            "refine (new_item) recipes bind to the crafting bench, not smithing",
        )
        // A representative recipe of each shipped outcome family parsed.
        val outcomes = result.recipes.map { it.outcome::class }.toSet()
        assertTrue(outcomes.contains(RecipeOutcome.NewItem::class), "refine recipes (new_item) load")
        assertTrue(outcomes.contains(RecipeOutcome.UpgradeInput::class), "upgrade recipes load")
        assertTrue(outcomes.contains(RecipeOutcome.Reforge::class), "reforge recipes load")
        assertTrue(outcomes.contains(RecipeOutcome.InsertGem::class), "socket recipes load")
    }

    // -- Registration into the live registries -----------------------------------------------------

    @Test
    fun `registrar resolves the shipped station and recipes into the registries`(@TempDir dir: Path) {
        val result = loadPackagedCrafting(dir)

        val items = ItemDefinitionRegistryImpl()
        val reforges = ReforgeRegistryImpl().also { BuiltinReforges.registerAll(it) }
        val gems = GemRegistryImpl().also { BuiltinGems.registerAll(it) }
        val recipes = RecipeRegistryImpl()
        val stations = StationRegistryImpl()
        val registrar = registrar(items, reforges, gems, recipes, stations)

        val errors = registrar.registerAll(result, ContentModule.OWNER)
        assertTrue(errors.isEmpty(), "shipped crafting pack must register clean: $errors")

        // The station resolved to a live Station: a BlockMatcher that actually matches its vanilla block
        // (and rejects others) and a RamCore MenuView carrying the authored row count + permitted kinds.
        val station = stations.get(smithing)
        assertNotNull(station, "the smithing station must be registered")
        station!!
        val smithingTable = ProxyFakes.proxy(Block::class.java, mapOf("getType" to Material.SMITHING_TABLE))
        val stone = ProxyFakes.proxy(Block::class.java, mapOf("getType" to Material.STONE))
        assertTrue(station.blockMatcher.matches(smithingTable), "matcher must match SMITHING_TABLE")
        assertFalse(station.blockMatcher.matches(stone), "matcher must reject a non-station block")
        assertEquals(5, station.menuLayout.rows(), "menu view carries the authored row count")
        assertEquals(
            setOf(
                RecipeOutcomeKind.UPGRADE_INPUT, RecipeOutcomeKind.REPAIR, RecipeOutcomeKind.REFORGE,
                RecipeOutcomeKind.INSERT_GEM, RecipeOutcomeKind.ADD_SOCKET, RecipeOutcomeKind.REMOVE_GEM,
            ),
            station.permittedKinds,
        )

        // Recipes registered, resolve their ingredients + outcome, and reference the registered station.
        assertTrue(recipes.all().isNotEmpty(), "recipes must be registered")
        assertTrue(recipes.forStation(smithing).isNotEmpty(), "forStation must find the shipped recipes")

        // WP-3.1e: the crafting bench resolved too, permitting exactly the item-creating kinds.
        val bench = stations.get(craftingBench)
        assertNotNull(bench, "the crafting bench station must be registered")
        bench!!
        val craftingTable = ProxyFakes.proxy(Block::class.java, mapOf("getType" to Material.CRAFTING_TABLE))
        assertTrue(bench.blockMatcher.matches(craftingTable), "bench matcher must match CRAFTING_TABLE")
        assertEquals(setOf(RecipeOutcomeKind.NEW_ITEM, RecipeOutcomeKind.TRANSMUTE), bench.permittedKinds)

        val refine = recipes.get(RecipeKey.of("ramrpg", "refine_copper_ore"))
        assertNotNull(refine, "a shipped refine recipe must be registered")
        refine!!
        assertEquals(craftingBench, refine.station, "WP-3.1e: refine recipes are re-pointed to the crafting bench")
        assertNotNull(stations.get(refine.station), "the recipe's station must be a registered Station")
        assertTrue(refine.inputs.any { it is Ingredient.Item }, "the tiered ore input resolves to Ingredient.Item")
        assertTrue(refine.inputs.any { it is Ingredient.MaterialTag }, "the fuel catalyst resolves to a MaterialTag")
        val tag = refine.inputs.filterIsInstance<Ingredient.MaterialTag>().first()
        assertTrue(tag.materials.contains(Material.COAL), "material names resolved to real Materials")
        assertTrue(refine.outcome is RecipeOutcome.NewItem, "outcome parsed through unchanged")

        // A reforge recipe resolved its (builtin) reforge and a socket recipe its (builtin) gem.
        assertNotNull(recipes.get(RecipeKey.of("ramrpg", "reforge_sharp")), "reforge recipe registered")
        assertNotNull(recipes.get(RecipeKey.of("ramrpg", "socket_ruby")), "socket recipe registered")
    }

    // -- Error aggregation (never throws) ----------------------------------------------------------

    @Test
    fun `unknown materials and unresolved references aggregate errors without throwing`(@TempDir dir: Path) {
        // A good station + a valid recipe alongside four broken entries: an unknown block material, an
        // unknown ingredient material, a missing station reference, and a missing output item.
        writeConf(dir, "stations", "good", """id = "ramrpg:good_station"${'\n'}blocks = ["SMITHING_TABLE"]${'\n'}rows = 3""")
        writeConf(dir, "stations", "bad_block", """id = "ramrpg:bad_station"${'\n'}blocks = ["NOT_A_REAL_MATERIAL"]${'\n'}rows = 3""")
        writeConf(
            dir, "recipes", "good",
            """id = "ramrpg:good_recipe"${'\n'}station = "ramrpg:good_station"${'\n'}""" +
                """inputs = [ { type = material, materials = ["DIAMOND"], count = 1 } ]${'\n'}""" +
                """outcome { type = upgrade_input, upgrade-levels = 1 }""",
        )
        writeConf(
            dir, "recipes", "bad_material",
            """id = "ramrpg:bad_material"${'\n'}station = "ramrpg:good_station"${'\n'}""" +
                """inputs = [ { type = material, materials = ["NOT_A_MATERIAL"], count = 1 } ]${'\n'}""" +
                """outcome { type = upgrade_input, upgrade-levels = 1 }""",
        )
        writeConf(
            dir, "recipes", "bad_station",
            """id = "ramrpg:bad_station_ref"${'\n'}station = "ramrpg:nonexistent"${'\n'}""" +
                """outcome { type = upgrade_input, upgrade-levels = 1 }""",
        )
        writeConf(
            dir, "recipes", "bad_item",
            """id = "ramrpg:bad_item"${'\n'}station = "ramrpg:good_station"${'\n'}""" +
                """outcome { type = new_item, item = "ramrpg:ghost", count = 1 }""",
        )

        val result = RpgContentLoader.load(dir)
        assertTrue(result.errors().isEmpty(), "these confs are syntactically valid; errors are registration-time")

        val items = ItemDefinitionRegistryImpl()
        val recipes = RecipeRegistryImpl()
        val stations = StationRegistryImpl()
        val registrar = registrar(items, ReforgeRegistryImpl(), GemRegistryImpl(), recipes, stations)

        // Never throws: it returns aggregated errors -- one per broken entry.
        val errors = registrar.registerAll(result, ContentModule.OWNER)
        assertEquals(4, errors.size, "one aggregated error per broken entry: $errors")

        // The good entries still registered despite the broken ones (aggregation, not fail-fast).
        assertNotNull(stations.get(StationKey.of("ramrpg", "good_station")), "the valid station registered")
        assertNotNull(recipes.get(RecipeKey.of("ramrpg", "good_recipe")), "the valid recipe registered")
        // The broken ones did NOT register.
        assertTrue(stations.get(StationKey.of("ramrpg", "bad_station")) == null, "the bad-block station was skipped")
        assertTrue(recipes.get(RecipeKey.of("ramrpg", "bad_material")) == null, "the bad-material recipe was skipped")
        assertTrue(recipes.get(RecipeKey.of("ramrpg", "bad_station_ref")) == null, "the missing-station recipe was skipped")
        assertTrue(recipes.get(RecipeKey.of("ramrpg", "bad_item")) == null, "the missing-item recipe was skipped")
    }

    // -- Reload owner-unregister -------------------------------------------------------------------

    @Test
    fun `reload unregisters recipes and stations by owner`(@TempDir dir: Path, @TempDir empty: Path) {
        val result = loadPackagedCrafting(dir)

        val items = ItemDefinitionRegistryImpl()
        val reforges = ReforgeRegistryImpl().also { BuiltinReforges.registerAll(it) }
        val gems = GemRegistryImpl().also { BuiltinGems.registerAll(it) }
        val recipes = RecipeRegistryImpl()
        val stations = StationRegistryImpl()
        val registrar = registrar(items, reforges, gems, recipes, stations)

        registrar.registerAll(result, ContentModule.OWNER)
        assertTrue(recipes.all().isNotEmpty(), "recipes seeded before reload")
        assertTrue(stations.all().isNotEmpty(), "stations seeded before reload")

        // A reload against empty content must unregister the owner from BOTH new registries (the
        // WP-3.1d additions to applyToRegistries), leaving nothing behind.
        val emptyResult = RpgContentLoader.load(empty)
        ContentModule.applyToRegistries(
            registrar, emptyResult, items, SkillRegistryImpl(), EnchantmentRegistryImpl(),
            EntityProfileRegistryImpl(), reforges, gems, recipes, stations,
        )
        assertTrue(recipes.all().isEmpty(), "recipes must be owner-unregistered on reload")
        assertTrue(stations.all().isEmpty(), "stations must be owner-unregistered on reload")
    }

    // -- Helpers -----------------------------------------------------------------------------------

    private fun registrar(
        items: ItemDefinitionRegistryImpl,
        reforges: ReforgeRegistryImpl,
        gems: GemRegistryImpl,
        recipes: RecipeRegistryImpl,
        stations: StationRegistryImpl,
    ) = ContentRegistrarRpg(
        stats = StatServiceImpl(),
        items = items,
        skills = SkillRegistryImpl(),
        enchants = EnchantmentRegistryImpl(),
        entities = EntityProfileRegistryImpl(),
        reforges = reforges,
        gems = gems,
        recipes = recipes,
        stations = stations,
    )

    private fun writeConf(root: Path, type: String, name: String, body: String) {
        val d = root.resolve(type)
        Files.createDirectories(d)
        d.resolve("$name.conf").toFile().writeText(body)
    }
}
