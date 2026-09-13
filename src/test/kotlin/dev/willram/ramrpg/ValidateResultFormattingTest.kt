package dev.willram.ramrpg

import dev.willram.ramcore.exception.ValidationError
import dev.willram.ramrpg.core.config.RpgContentLoader
import dev.willram.ramrpg.core.modules.ContentModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * WP-1.5c: `/rpg validate` prints `file:path: message` per [ValidationError] -- NOT
 * [ValidationError.describe]'s `[file] path: message` (RamCore's own diagnostics-log form). This asserts
 * [ContentModule.formatValidationError]'s exact string output, both against hand-built [ValidationError]s
 * and against errors a real broken content directory actually produces via [RpgContentLoader] -- never a
 * dispatched command (BLOCKED on WP-RC2's CommandTestHarness, per the WP contract).
 */
class ValidateResultFormattingTest {

    @Test
    fun `formats file, path and message with colons`() {
        val error = ValidationError.at("items/broken.conf", "ramrpg:sword", "unknown material 'NOT_A_MATERIAL'")
        assertEquals("items/broken.conf:ramrpg:sword: unknown material 'NOT_A_MATERIAL'", ContentModule.formatValidationError(error))
    }

    @Test
    fun `omits the path segment when the error has none`() {
        val error = ValidationError.at("bad_type", "", "unknown content type 'bad_type'; expected a directory named one of items, ...")
        val formatted = ContentModule.formatValidationError(error)
        assertEquals("bad_type: unknown content type 'bad_type'; expected a directory named one of items, ...", formatted)
    }

    @Test
    fun `renders a real ValidationError list from a broken content directory`(@TempDir root: Path) {
        writeConf(root, "items", "no_material", "id = \"ramrpg:no_mat\"")

        val result = RpgContentLoader.load(root)
        assertEquals(1, result.errors().size)

        val formatted = result.errors().map { ContentModule.formatValidationError(it) }
        assertEquals(listOf("no_material.conf:ramrpg:no_mat: missing 'material'"), formatted)
    }

    private fun writeConf(root: Path, type: String, name: String, body: String) {
        val dir = root.resolve(type)
        Files.createDirectories(dir)
        dir.resolve("$name.conf").toFile().writeText(body)
    }
}
