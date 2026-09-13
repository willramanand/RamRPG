package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.core.config.RpgContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * WP-1.5a: `extends:` deep-merge (provided by RamCore's ContentLoader, not re-implemented here) works
 * end-to-end -- the child overrides one scalar, inherits the rest, and map fields merge key-by-key.
 */
class ContentLoaderExtendsTest {

    @Test
    fun `child overrides parent scalar and inherits the rest`(@TempDir root: Path) {
        writeConf(
            root, "items", "base_sword",
            """
            id = "ramrpg:base_sword"
            name = "<white>Base Sword"
            material = "IRON_SWORD"
            rarity = "COMMON"
            categories = ["SWORD"]
            base-stats { "ramrpg:damage" = 10.0 }
            description = ["<gray>Base."]
            """,
        )
        writeConf(
            root, "items", "fancy_sword",
            """
            id = "ramrpg:fancy_sword"
            extends = "ramrpg:base_sword"
            rarity = "RARE"
            base-stats { "ramrpg:strength" = 5.0 }
            """,
        )

        val result = RpgContentLoader.load(root)
        assertTrue(result.errors().isEmpty(), "no load errors: ${result.errors()}")
        assertEquals(2, result.items.size)

        val child = result.items.first { it.key == ItemKey.of("ramrpg", "fancy_sword") }

        // Overridden scalar.
        assertEquals(Rarity.RARE, child.rarity)
        // Inherited scalars / lists.
        assertEquals("IRON_SWORD", child.material)
        assertEquals(setOf(ItemCategory.SWORD), child.categories)
        // Map fields merge: the child's strength plus the parent's damage.
        assertEquals(10.0, child.baseStats[StatKey.of("ramrpg", "damage")])
        assertEquals(5.0, child.baseStats[StatKey.of("ramrpg", "strength")])
    }

    private fun writeConf(root: Path, type: String, name: String, body: String) {
        val dir = root.resolve(type)
        Files.createDirectories(dir)
        dir.resolve("$name.conf").toFile().writeText(body.trimIndent())
    }
}
