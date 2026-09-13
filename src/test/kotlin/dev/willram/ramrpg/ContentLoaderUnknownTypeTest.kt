package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.core.config.RpgContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * WP-1.5a: an unknown content directory yields a ValidationError that NAMES the directory, rather than
 * crashing or being silently dropped (RamCore's SpecLoader ignores unregistered types; RamRPG layers
 * this policy on top). Known-type entries alongside it still load.
 */
class ContentLoaderUnknownTypeTest {

    @Test
    fun `unknown type directory yields a naming error and does not crash`(@TempDir root: Path) {
        // A valid known-type entry, to prove aggregation continues past the unknown type.
        writeConf(
            root, "items", "ok_sword",
            """
            id = "ramrpg:ok_sword"
            material = "DIAMOND_SWORD"
            rarity = "COMMON"
            categories = ["SWORD"]
            """,
        )
        // An unrecognised directory / type.
        writeConf(
            root, "widgets", "thing",
            """
            id = "ramrpg:thing"
            foo = "bar"
            """,
        )

        val result = RpgContentLoader.load(root)

        // The known item still loaded.
        assertEquals(1, result.items.size)
        assertEquals(ItemKey.of("ramrpg", "ok_sword"), result.items.single().key)

        // Exactly one error, naming the unknown type and carrying its file + id.
        assertEquals(1, result.errors().size, "only the unknown type is an error: ${result.errors()}")
        val error = result.errors().single()
        assertTrue(error.message().contains("widgets"), "error names the type: ${error.message()}")
        assertTrue(error.message().contains("unknown content type"), error.message())
        assertEquals("thing.conf", error.source())
        assertEquals("ramrpg:thing", error.path())
    }

    private fun writeConf(root: Path, type: String, name: String, body: String) {
        val dir = root.resolve(type)
        Files.createDirectories(dir)
        dir.resolve("$name.conf").toFile().writeText(body.trimIndent())
    }
}
