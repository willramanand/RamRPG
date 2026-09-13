package dev.willram.ramrpg

import dev.willram.ramrpg.core.config.RpgContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * WP-1.5a: three broken files produce three aggregated ValidationErrors -- never a thrown exception --
 * and each error carries the right SourceRef (file + path). Path detection is exercised across three
 * distinct pure-deserializer failures (missing field, bad enum, bad enum in a list).
 */
class ContentLoaderErrorAggregationTest {

    @Test
    fun `three broken files yield three errors each with its source and path`(@TempDir root: Path) {
        writeConf(
            root, "items", "no_material",
            """
            id = "ramrpg:no_mat"
            rarity = "RARE"
            categories = ["SWORD"]
            """,
        )
        writeConf(
            root, "items", "bad_rarity",
            """
            id = "ramrpg:bad_rar"
            material = "DIAMOND_SWORD"
            rarity = "SUPER_DUPER"
            """,
        )
        writeConf(
            root, "items", "bad_category",
            """
            id = "ramrpg:bad_cat"
            material = "DIAMOND_SWORD"
            categories = ["WAND"]
            """,
        )

        // Does not throw: the loader returns a result with every error collected.
        val result = RpgContentLoader.load(root)

        assertEquals(3, result.errors().size, "one error per broken file: ${result.errors()}")
        assertTrue(result.items.isEmpty(), "no broken item should have loaded")

        // Each error carries its file (source) and, since these are single-entry files, its id (path).
        val byFile = result.errors().associate { it.source() to it }
        assertEquals(setOf("no_material.conf", "bad_rarity.conf", "bad_category.conf"), byFile.keys)

        assertEquals("ramrpg:no_mat", byFile.getValue("no_material.conf").path())
        assertTrue(byFile.getValue("no_material.conf").message().contains("material"))

        assertEquals("ramrpg:bad_rar", byFile.getValue("bad_rarity.conf").path())
        assertTrue(byFile.getValue("bad_rarity.conf").message().contains("SUPER_DUPER"))

        assertEquals("ramrpg:bad_cat", byFile.getValue("bad_category.conf").path())
        assertTrue(byFile.getValue("bad_category.conf").message().contains("WAND"))
    }

    private fun writeConf(root: Path, type: String, name: String, body: String) {
        val dir = root.resolve(type)
        Files.createDirectories(dir)
        dir.resolve("$name.conf").toFile().writeText(body.trimIndent())
    }
}
