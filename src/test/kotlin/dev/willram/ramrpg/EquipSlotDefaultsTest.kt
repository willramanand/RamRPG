package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.EquipSlotDefaults
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.Rarity
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.EquipmentSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * WP-2.1a: the [ItemCategory] -> [EquipmentSlot] default table (see
 * `docs/design/2.1a-item-level-requirements.md`), both directly via [EquipSlotDefaults] and through
 * [ItemDefinition]'s own default parameter, and that an explicit `equipSlots` overrides the default.
 */
class EquipSlotDefaultsTest {

    private fun def(categories: Set<ItemCategory>, equipSlots: Set<EquipmentSlot>? = null): ItemDefinition {
        val base = ItemDefinition(
            key = ItemKey.of("test", "x"),
            displayName = Component.text("X"),
            material = Material.STICK,
            rarity = Rarity.COMMON,
            categories = categories,
        )
        return if (equipSlots == null) base else base.copy(equipSlots = equipSlots)
    }

    @Test
    fun `weapon and tool categories default to HAND`() {
        val handCategories = listOf(
            ItemCategory.SWORD, ItemCategory.AXE, ItemCategory.PICKAXE, ItemCategory.SHOVEL,
            ItemCategory.HOE, ItemCategory.BOW, ItemCategory.CROSSBOW, ItemCategory.TRIDENT,
            ItemCategory.MACE, ItemCategory.FISHING_ROD,
        )
        for (cat in handCategories) {
            assertEquals(setOf(EquipmentSlot.HAND), EquipSlotDefaults.forCategories(setOf(cat)), "category $cat")
            assertEquals(setOf(EquipmentSlot.HAND), def(setOf(cat)).equipSlots, "ItemDefinition default for $cat")
        }
    }

    @Test
    fun `armor categories default to their own slot`() {
        assertEquals(setOf(EquipmentSlot.HEAD), def(setOf(ItemCategory.HELMET)).equipSlots)
        assertEquals(setOf(EquipmentSlot.CHEST), def(setOf(ItemCategory.CHESTPLATE)).equipSlots)
        assertEquals(setOf(EquipmentSlot.CHEST), def(setOf(ItemCategory.ELYTRA)).equipSlots)
        assertEquals(setOf(EquipmentSlot.LEGS), def(setOf(ItemCategory.LEGGINGS)).equipSlots)
        assertEquals(setOf(EquipmentSlot.FEET), def(setOf(ItemCategory.BOOTS)).equipSlots)
        assertEquals(setOf(EquipmentSlot.OFF_HAND), def(setOf(ItemCategory.SHIELD)).equipSlots)
    }

    @Test
    fun `misc and enchanted book categories have no default slot`() {
        assertEquals(emptySet<EquipmentSlot>(), def(setOf(ItemCategory.MISC)).equipSlots)
        assertEquals(emptySet<EquipmentSlot>(), def(setOf(ItemCategory.ENCHANTED_BOOK)).equipSlots)
    }

    @Test
    fun `multiple categories union their default slots`() {
        assertEquals(
            setOf(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND),
            def(setOf(ItemCategory.SWORD, ItemCategory.SHIELD)).equipSlots,
        )
    }

    @Test
    fun `explicit equipSlots overrides the category default`() {
        assertEquals(
            setOf(EquipmentSlot.OFF_HAND),
            def(setOf(ItemCategory.SWORD), equipSlots = setOf(EquipmentSlot.OFF_HAND)).equipSlots,
        )
        // An explicit EMPTY set is itself a valid override (e.g. a decorative sword that equips nowhere).
        assertEquals(
            emptySet<EquipmentSlot>(),
            def(setOf(ItemCategory.HELMET), equipSlots = emptySet()).equipSlots,
        )
    }
}
