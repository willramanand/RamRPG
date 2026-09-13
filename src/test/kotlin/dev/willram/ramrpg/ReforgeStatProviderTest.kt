package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.builtin.identity.RamStats
import dev.willram.ramrpg.builtin.reforges.BuiltinReforges
import dev.willram.ramrpg.core.services.ReforgeRegistryImpl
import dev.willram.ramrpg.core.services.reforgeStatsFor
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-3.3c: reforge-as-a-recipe MUST NOT change reforge stat output. [reforgeStatsFor] is the extracted
 * heart of [dev.willram.ramrpg.core.services.ReforgeStatProvider.provideStats] (its per-stack
 * contribution), so pinning its exact output for every shipped builtin reforge locks the provider's
 * behaviour: a reforge set by the new recipe path yields byte-identical stats to before. If a later WP
 * ever grew a bespoke reforge stat path, these golden values would drift and this test would fail.
 *
 * Pure: no live server -- [reforgeStatsFor] takes a plain [ItemDefinition]/[ItemInstanceData]/state and
 * the real [ReforgeRegistry] populated by [BuiltinReforges].
 */
class ReforgeStatProviderTest {

    private val reforges: ReforgeRegistry = ReforgeRegistryImpl().also { BuiltinReforges.registerAll(it) }

    private val activeState = object : ItemRequirementState {
        override fun skillLevel(skill: SkillKey): Int = 0
        override fun statValue(stat: StatKey): Double = 0.0
    }

    private fun def(vararg categories: ItemCategory): ItemDefinition = ItemDefinition(
        key = ItemKey.of("ramrpg", "probe"),
        displayName = Component.text("Probe"),
        material = Material.IRON_SWORD,
        rarity = Rarity.UNCOMMON,
        categories = categories.toSet(),
        requirements = emptyList(), // no requirements + full durability => never inert
    )

    private fun reforge(id: String) = ReforgeKey(dev.willram.ramcore.content.ContentId.of("ramrpg", id))

    /** The exact stat->amount map [reforgeStatsFor] produces, asserting every modifier is a REFORGE source. */
    private fun statsFor(def: ItemDefinition, reforgeId: String): Map<StatKey, Double> {
        val rk = reforge(reforgeId)
        val data = ItemInstanceData(identity = ItemIdentity(def.key), reforge = rk)
        val mods = reforgeStatsFor(def, data, activeState, reforges)
        for (m in mods) {
            assertEquals(ModifierOperation.ADD, m.operation, "reforge modifiers are additive")
            assertEquals(SourceType.REFORGE, m.source.type, "must be attributed to the REFORGE source")
            assertEquals(rk.id, m.source.ref, "source ref must be the reforge key")
        }
        // No reforge duplicates a stat, so a plain map is a faithful representation.
        assertEquals(mods.size, mods.map { it.stat }.toSet().size, "no duplicate stat modifiers")
        return mods.associate { it.stat to it.amount }
    }

    @Test
    fun `fierce on a weapon is unchanged`() {
        assertEquals(mapOf(RamStats.DAMAGE to 8.0, RamStats.STRENGTH to 6.0), statsFor(def(ItemCategory.SWORD), "fierce"))
    }

    @Test
    fun `sharp on a weapon is unchanged`() {
        assertEquals(mapOf(RamStats.CRIT_CHANCE to 5.0, RamStats.CRIT_DAMAGE to 8.0), statsFor(def(ItemCategory.SWORD), "sharp"))
    }

    @Test
    fun `heavy contributes the weapon arm on a weapon and the armor arm on armor`() {
        assertEquals(mapOf(RamStats.DAMAGE to 12.0), statsFor(def(ItemCategory.SWORD), "heavy"))
        assertEquals(mapOf(RamStats.DEFENSE to 8.0), statsFor(def(ItemCategory.HELMET), "heavy"))
    }

    @Test
    fun `pure on armor is unchanged`() {
        assertEquals(mapOf(RamStats.HEALTH to 12.0, RamStats.DEFENSE to 4.0), statsFor(def(ItemCategory.HELMET), "pure"))
    }

    @Test
    fun `wise on armor is unchanged`() {
        assertEquals(mapOf(RamStats.WISDOM to 30.0), statsFor(def(ItemCategory.HELMET), "wise"))
    }

    @Test
    fun `a reforge only contributes to its own category family`() {
        // Fierce is a weapon reforge: on a HELMET it contributes nothing (no wrong-family bleed-through).
        assertTrue(statsFor(def(ItemCategory.HELMET), "fierce").isEmpty())
        // Pure is an armor reforge: on a SWORD it contributes nothing.
        assertTrue(statsFor(def(ItemCategory.SWORD), "pure").isEmpty())
    }
}
