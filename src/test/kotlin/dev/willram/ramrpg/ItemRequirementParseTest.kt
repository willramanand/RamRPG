package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemRequirement
import dev.willram.ramrpg.core.config.specs.ItemSpec
import org.bukkit.inventory.EquipmentSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.StringReader

/**
 * WP-2.1a: each `requirements = [...]` form parses into the right [ItemRequirement] subtype, and
 * `item-level` / `equip-slots` parse (with the right absent-vs-explicit default behavior).
 */
class ItemRequirementParseTest {

    private fun hocon(text: String): ConfigurationNode =
        HoconConfigurationLoader.builder()
            .source { BufferedReader(StringReader(text.trimIndent())) }
            .build()
            .load()

    private fun spec(text: String): ItemSpec = ItemSpec.deserialize(hocon(text))

    @Test
    fun `skill_level requirement parses`() {
        val s = spec(
            """
            id = "ramrpg:test_item"
            material = "STICK"
            requirements = [ { type = skill_level, skill = "ramrpg:combat", level = 10 } ]
            """,
        )
        val req = s.requirements.single()
        assertTrue(req is ItemRequirement.SkillLevel)
        req as ItemRequirement.SkillLevel
        assertEquals(SkillKey.of("ramrpg", "combat"), req.skill)
        assertEquals(10, req.level)
    }

    @Test
    fun `stat_threshold requirement parses`() {
        val s = spec(
            """
            id = "ramrpg:test_item"
            material = "STICK"
            requirements = [ { type = stat_threshold, stat = "ramrpg:strength", min = 20.0 } ]
            """,
        )
        val req = s.requirements.single()
        assertTrue(req is ItemRequirement.StatThreshold)
        req as ItemRequirement.StatThreshold
        assertEquals(StatKey.of("ramrpg", "strength"), req.stat)
        assertEquals(20.0, req.min)
    }

    @Test
    fun `perk_owned requirement parses its raw content id (PerkKey does not exist yet)`() {
        val s = spec(
            """
            id = "ramrpg:test_item"
            material = "STICK"
            requirements = [ { type = perk_owned, perk = "ramrpg:dragon_slayer" } ]
            """,
        )
        val req = s.requirements.single()
        assertTrue(req is ItemRequirement.PerkOwned)
        req as ItemRequirement.PerkOwned
        assertEquals(ContentId.of("ramrpg", "dragon_slayer"), req.perk)
    }

    @Test
    fun `multiple requirement forms parse together in declared order`() {
        val s = spec(
            """
            id = "ramrpg:test_item"
            material = "STICK"
            requirements = [
              { type = skill_level, skill = "ramrpg:combat", level = 5 }
              { type = stat_threshold, stat = "ramrpg:health", min = 50.0 }
              { type = perk_owned, perk = "ramrpg:example" }
            ]
            """,
        )
        assertEquals(3, s.requirements.size)
        assertTrue(s.requirements[0] is ItemRequirement.SkillLevel)
        assertTrue(s.requirements[1] is ItemRequirement.StatThreshold)
        assertTrue(s.requirements[2] is ItemRequirement.PerkOwned)
    }

    @Test
    fun `unknown requirement type fails with a named error`() {
        val ex = assertThrows(ContentDeserializeException::class.java) {
            spec(
                """
                id = "ramrpg:test_item"
                material = "STICK"
                requirements = [ { type = nonsense } ]
                """,
            )
        }
        assertTrue(ex.message!!.contains("nonsense"))
    }

    @Test
    fun `requirements is empty by default`() {
        val s = spec("id = \"ramrpg:test_item\"\nmaterial = \"STICK\"")
        assertTrue(s.requirements.isEmpty())
    }

    @Test
    fun `item-level defaults to 1 and parses when present`() {
        val default = spec("id = \"ramrpg:test_item\"\nmaterial = \"STICK\"")
        assertEquals(1, default.itemLevel)

        val explicit = spec(
            """
            id = "ramrpg:test_item"
            material = "STICK"
            item-level = 42
            """,
        )
        assertEquals(42, explicit.itemLevel)
    }

    @Test
    fun `equip-slots is null by default and parses an explicit override`() {
        val default = spec("id = \"ramrpg:test_item\"\nmaterial = \"STICK\"")
        assertNull(default.equipSlots)

        val explicit = spec(
            """
            id = "ramrpg:test_item"
            material = "STICK"
            equip-slots = ["OFF_HAND"]
            """,
        )
        assertEquals(setOf(EquipmentSlot.OFF_HAND), explicit.equipSlots)
    }
}
