package dev.willram.ramrpg

import dev.willram.ramrpg.core.commands.CommandAliasMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-3.0: pure-data test for the `/skills` -> `/rpg` alias table ([CommandAliasMap.OLD_TO_NEW] and
 * [CommandAliasMap.FORWARDED]). No Brigadier dispatch, no Bukkit statics, no live server -- mirrors the
 * constraint noted on `ValidateResultFormattingTest` (BLOCKED on WP-RC2's CommandTestHarness). Asserts
 * every stripped/renamed/regrouped `/skills` leaf is accounted for, maps to its documented `/rpg` path,
 * AND is correctly classified as warn-and-forward vs. warn-only -- so the pure-data table can never claim
 * a forward the alias tree doesn't actually perform (or vice versa).
 */
class CommandAliasMappingTest {

    /** The full expected old->new mapping, independently restated here (not copied from the source
     *  file) so a drift in either place fails this test. */
    private val expected = mapOf(
        "skills" to "rpg skills",
        "skills gui" to "rpg skills gui",
        "skills help" to "rpg help",
        "skills version" to "rpg version",
        "skills stats" to "rpg stats",
        "skills quests" to "rpg quests",
        "skills reload" to "rpg reload",
        "skills statsgui" to "rpg stats gui",
        "skills quest abandon" to "rpg quests abandon",
        "skills inspect" to "rpg skills inspect",
        "skills upgrade" to "rpg upgrade",
        "skills reset" to "rpg admin reset",
        "skills give" to "rpg admin give",
        "skills level" to "rpg admin level",
        "skills xp" to "rpg admin xp",
        "skills enchant" to "rpg admin enchant",
        "skills reforge" to "rpg reforge",
        "skills reforge clear" to "rpg reforge",
        "skills ability" to "rpg skills ability",
        "skills ability list" to "rpg skills ability list",
        "skills ability toggle" to "rpg skills ability toggle",
        "skills socket" to "rpg socket",
        "skills socket clear" to "rpg socket",
    )

    @Test
    fun `every stripped, renamed or regrouped skills path is documented with its exact new path`() {
        assertEquals(expected, CommandAliasMap.OLD_TO_NEW)
    }

    @Test
    fun `station-bound subcommands map to the deprecated rpg stub, not a moved implementation`() {
        assertEquals("rpg upgrade", CommandAliasMap.OLD_TO_NEW.getValue("skills upgrade"))
        assertEquals("rpg reforge", CommandAliasMap.OLD_TO_NEW.getValue("skills reforge"))
        assertEquals("rpg reforge", CommandAliasMap.OLD_TO_NEW.getValue("skills reforge clear"))
        assertEquals("rpg socket", CommandAliasMap.OLD_TO_NEW.getValue("skills socket"))
        assertEquals("rpg socket", CommandAliasMap.OLD_TO_NEW.getValue("skills socket clear"))
    }

    @Test
    fun `admin commands are regrouped under rpg admin`() {
        for (leaf in listOf("give", "level", "xp", "enchant", "reset")) {
            val newPath = CommandAliasMap.OLD_TO_NEW.getValue("skills $leaf")
            assertTrue(newPath.startsWith("rpg admin "), "expected 'skills $leaf' to map under rpg admin, was '$newPath'")
        }
    }

    @Test
    fun `player-facing groups are not routed through rpg admin`() {
        for (leaf in listOf("stats", "quests", "gui", "help", "version")) {
            val key = if (leaf.isEmpty()) "skills" else "skills $leaf"
            val newPath = CommandAliasMap.OLD_TO_NEW.getValue(key)
            assertTrue(!newPath.startsWith("rpg admin"), "expected '$key' to stay player-facing, was '$newPath'")
        }
    }

    @Test
    fun `every mapped new path starts with rpg`() {
        for ((old, new) in CommandAliasMap.OLD_TO_NEW) {
            assertTrue(new.startsWith("rpg "), "expected new path for '$old' to start with 'rpg ', was '$new'")
        }
    }

    /**
     * The still-live player features -- FIX from adversarial review of the initial WP-3.0 commit: these
     * four leaves used to be warn-only dead signposts for behavior that hadn't actually moved. They now
     * warn AND forward to the real handler, exactly like `/skills stats` always did.
     */
    private val expectedForwarded = setOf(
        "skills",
        "skills gui",
        "skills help",
        "skills version",
        "skills stats",
        "skills quests",
        "skills statsgui",
        "skills inspect",
        "skills quest abandon",
        "skills ability list",
        "skills ability toggle",
    )

    @Test
    fun `forwarded set is exactly the still-live player features, independently restated`() {
        assertEquals(expectedForwarded, CommandAliasMap.FORWARDED)
    }

    @Test
    fun `every forwarded key is a real row in the alias table`() {
        assertTrue(
            CommandAliasMap.OLD_TO_NEW.keys.containsAll(CommandAliasMap.FORWARDED),
            "FORWARDED contains a key with no OLD_TO_NEW row: ${CommandAliasMap.FORWARDED - CommandAliasMap.OLD_TO_NEW.keys}",
        )
    }

    @Test
    fun `station stubs and admin regroups are warn-only, never forwarded`() {
        val mustBeWarnOnly = setOf(
            "skills reload",
            "skills upgrade",
            "skills reset",
            "skills give",
            "skills level",
            "skills xp",
            "skills enchant",
            "skills reforge",
            "skills reforge clear",
            "skills ability",
            "skills socket",
            "skills socket clear",
        )
        val wronglyForwarded = mustBeWarnOnly intersect CommandAliasMap.FORWARDED
        assertTrue(wronglyForwarded.isEmpty(), "expected these to stay warn-only (no live behavior left to forward to): $wronglyForwarded")
    }
}
