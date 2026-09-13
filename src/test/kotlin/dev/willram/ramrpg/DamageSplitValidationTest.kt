package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramrpg.api.identity.DamageTypeKey
import dev.willram.ramrpg.core.config.RpgContentLoader
import dev.willram.ramrpg.core.config.specs.ItemSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path

/**
 * WP-2.3b: `damage-split` parses into a `Map<DamageTypeKey, Double>`, validated to sum to `1.0` (+/- a
 * small epsilon) at load time; an absent split parses to an empty map, which
 * [dev.willram.ramrpg.builtin.stats.ElementalBreakdownStage] treats as its WP-2.3a all-physical default.
 * Mirrors [ItemRequirementParseTest]'s direct-`ItemSpec.deserialize` idiom for the pure-parse cases, plus
 * one full [RpgContentLoader.load] round trip proving a bad sum surfaces as a source-tagged
 * [dev.willram.ramcore.exception.ValidationError] (see [ContentLoaderErrorAggregationTest]'s idiom).
 */
class DamageSplitValidationTest {

    private fun hocon(text: String): ConfigurationNode =
        HoconConfigurationLoader.builder()
            .source { BufferedReader(StringReader(text.trimIndent())) }
            .build()
            .load()

    private fun spec(text: String): ItemSpec = ItemSpec.deserialize(hocon(text))

    @Test
    fun `a valid damage-split parses into the right map`() {
        val s = spec(
            """
            id = "ramrpg:test_sword"
            material = "IRON_SWORD"
            damage-split { "ramrpg:physical" = 0.7, "ramrpg:fire" = 0.3 }
            """,
        )
        assertEquals(
            mapOf(
                DamageTypeKey.of("ramrpg", "physical") to 0.7,
                DamageTypeKey.of("ramrpg", "fire") to 0.3,
            ),
            s.damageSplit,
        )
    }

    @Test
    fun `a damage-split within epsilon of 1_0 is accepted`() {
        val s = spec(
            """
            id = "ramrpg:test_sword"
            material = "IRON_SWORD"
            damage-split { "ramrpg:physical" = 0.60003, "ramrpg:fire" = 0.4 }
            """,
        )
        assertEquals(2, s.damageSplit.size)
        assertEquals(0.60003, s.damageSplit.getValue(DamageTypeKey.of("ramrpg", "physical")))
    }

    @Test
    fun `absent damage-split parses to an empty map (default all-physical)`() {
        val s = spec("id = \"ramrpg:test_sword\"\nmaterial = \"IRON_SWORD\"")
        assertTrue(s.damageSplit.isEmpty())
    }

    @Test
    fun `a damage-split under 1_0 throws naming the offending item and the actual sum`() {
        val ex = assertThrows(ContentDeserializeException::class.java) {
            spec(
                """
                id = "ramrpg:broken_sword"
                material = "IRON_SWORD"
                damage-split { "ramrpg:physical" = 0.5, "ramrpg:fire" = 0.2 }
                """,
            )
        }
        assertTrue(ex.message!!.contains("ramrpg:broken_sword"), "message names the item: ${ex.message}")
        assertTrue(ex.message!!.contains("0.7"), "message names the actual sum: ${ex.message}")
    }

    @Test
    fun `a damage-split over 1_0 also throws`() {
        val ex = assertThrows(ContentDeserializeException::class.java) {
            spec(
                """
                id = "ramrpg:overfull_sword"
                material = "IRON_SWORD"
                damage-split { "ramrpg:physical" = 0.8, "ramrpg:fire" = 0.5 }
                """,
            )
        }
        assertTrue(ex.message!!.contains("ramrpg:overfull_sword"), "message names the item: ${ex.message}")
    }

    @Test
    fun `a bad-sum damage-split surfaces as a source-tagged ValidationError through the full loader`(@TempDir root: Path) {
        val dir = root.resolve("items")
        Files.createDirectories(dir)
        dir.resolve("broken.conf").toFile().writeText(
            """
            id = "ramrpg:broken_sword"
            material = "IRON_SWORD"
            damage-split { "ramrpg:physical" = 0.5 }
            """.trimIndent(),
        )

        val result = RpgContentLoader.load(root)

        assertTrue(result.items.isEmpty(), "the broken item must not have loaded")
        assertEquals(1, result.errors().size, result.errors().toString())
        val error = result.errors().single()
        assertEquals("broken.conf", error.source())
        assertEquals("ramrpg:broken_sword", error.path())
        assertTrue(error.message().contains("ramrpg:broken_sword"), error.message())
    }
}
