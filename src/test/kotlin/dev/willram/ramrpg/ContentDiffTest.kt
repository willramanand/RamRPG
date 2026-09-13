package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.content.ContentLoader
import dev.willram.ramcore.reload.ContentDiff
import dev.willram.ramcore.reload.ContentSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * WP-1.5c: `/rpg reload` diffs two loads of `content/` using RamCore's [ContentSnapshot]/[ContentDiff]
 * directly (never a hand-rolled diff, per rule 1) -- exactly the calls `ContentModule.reload` makes
 * (`ContentLoader.load` -> `ContentSnapshot.of` -> `ContentDiff.data`). This exercises that exact pattern
 * against real RamRPG content directories, off-server and without any Bukkit/ContentModule wiring.
 */
class ContentDiffTest {

    @Test
    fun `diff between two snapshots reports added, changed and removed`(@TempDir before: Path, @TempDir after: Path) {
        // before: sword (removed later), axe (changed later), bow (unchanged)
        writeConf(before, "items", "sword", "id = \"ramrpg:sword\"\nmaterial = \"DIAMOND_SWORD\"")
        writeConf(before, "items", "axe", "id = \"ramrpg:axe\"\nmaterial = \"DIAMOND_AXE\"")
        writeConf(before, "items", "bow", "id = \"ramrpg:bow\"\nmaterial = \"BOW\"")

        // after: sword removed, axe changed (different material), bow unchanged, staff added
        writeConf(after, "items", "axe", "id = \"ramrpg:axe\"\nmaterial = \"NETHERITE_AXE\"")
        writeConf(after, "items", "bow", "id = \"ramrpg:bow\"\nmaterial = \"BOW\"")
        writeConf(after, "items", "staff", "id = \"ramrpg:staff\"\nmaterial = \"STICK\"")

        val beforeSnapshot = ContentSnapshot.of(ContentLoader.load(before))
        val afterResult = ContentLoader.load(after)
        val afterSnapshot = ContentSnapshot.of(afterResult)

        val diff = ContentDiff.data(beforeSnapshot, afterSnapshot, afterResult)

        assertEquals(setOf(id("staff")), diff.added().toSet())
        assertEquals(setOf(id("sword")), diff.removed().toSet())
        assertEquals(setOf(id("axe")), diff.changed().toSet())
        assertTrue(diff.dirty())
        assertTrue(diff.brokenReferences().isEmpty(), "no extends: relationships in this fixture")
        assertTrue(diff.errors().isEmpty())
    }

    @Test
    fun `reloading identical content is not dirty`(@TempDir dir: Path) {
        writeConf(dir, "items", "bow", "id = \"ramrpg:bow\"\nmaterial = \"BOW\"")

        val firstSnapshot = ContentSnapshot.of(ContentLoader.load(dir))
        val secondResult = ContentLoader.load(dir)
        val secondSnapshot = ContentSnapshot.of(secondResult)

        val diff = ContentDiff.data(firstSnapshot, secondSnapshot, secondResult)

        assertFalse(diff.dirty())
        assertTrue(diff.added().isEmpty())
        assertTrue(diff.changed().isEmpty())
        assertTrue(diff.removed().isEmpty())
    }

    private fun id(value: String) = ContentId.of("ramrpg", value)

    private fun writeConf(root: Path, type: String, name: String, body: String) {
        val dir = root.resolve(type)
        Files.createDirectories(dir)
        dir.resolve("$name.conf").toFile().writeText(body)
    }
}
