package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.InertReason
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.inertReason
import dev.willram.ramrpg.api.items.isInert
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.core.services.DurabilityService
import dev.willram.ramrpg.core.services.equipmentStatsFor
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-2.2: proves that draining an instance to 0 via `DurabilityService.damage` is what makes WP-2.1c's
 * existing `InertReason.ZERO_DURABILITY` path fire -- it does NOT re-implement or duplicate that check
 * (`ItemDefinition.inertReason`, `api/items/Items.kt`). `InertItemContributesNoStatsTest` already covers
 * every item-based provider withholding on an inert item; this test deliberately does not repeat that
 * matrix, it only pins the drain -> inert transition itself, plus one representative provider call to
 * confirm "contributes no stats" per the WP's DONE criteria.
 */
class DurabilityZeroIsInertTest {

    private val service = DurabilityService()
    private val strength = StatKey.of("ramrpg", "strength")

    /** A requirement-state that always reports "met" -- isolates this test from WP-2.1c's requirement gate. */
    private object AlwaysMetState : ItemRequirementState {
        override fun skillLevel(skill: SkillKey): Int = Int.MAX_VALUE
        override fun statValue(stat: StatKey): Double = Double.MAX_VALUE
    }

    private val def: ItemDefinition = ItemDefinition(
        key = ItemKey.of("ramrpg", "test_sword"),
        displayName = Component.text("Test Sword"),
        material = Material.IRON_SWORD,
        rarity = Rarity.UNCOMMON,
        categories = setOf(ItemCategory.SWORD),
        baseStats = listOf(
            StatModifier(strength, 50.0, ModifierOperation.ADD, ModifierSource(SourceType.ITEM, ContentId.of("ramrpg", "test_sword")))
        ),
    )

    private fun instance(durability: Int, max: Int = 10): ItemInstanceData =
        ItemInstanceData(identity = ItemIdentity(def.key), durability = durability, maxDurability = max)

    @Test
    fun `full durability is active, not inert`() {
        val data = instance(durability = 10)
        assertNull(def.inertReason(data, AlwaysMetState))
        assertFalse(def.isInert(data, AlwaysMetState))
    }

    @Test
    fun `draining to zero makes the item inert via ZERO_DURABILITY`() {
        var data = instance(durability = 3)
        repeat(3) { data = service.damage(data, 1) }

        assertEquals(0, data.durability)
        assertEquals(InertReason.ZERO_DURABILITY, def.inertReason(data, AlwaysMetState))
        assertTrue(def.isInert(data, AlwaysMetState))
    }

    @Test
    fun `zero durability contributes no equipment stats`() {
        val data = instance(durability = 0)
        assertTrue(equipmentStatsFor(def, data, AlwaysMetState).isEmpty())
    }
}
