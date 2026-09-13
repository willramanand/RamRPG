package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.LoreContext
import dev.willram.ramrpg.api.items.LoreSection
import dev.willram.ramrpg.api.items.Rarity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-2.1c: [LoreSection.RarityLine] renders [dev.willram.ramrpg.api.items.ItemInstanceData.quality] next
 * to the rarity name, using the WP-2.1b placeholder quality bands
 * (`docs/design/2.1b-quality-durability.md`): Crude/Rough/Standard/Fine/Superior/Masterwork.
 */
class QualityInRarityLineTest {

    private val def = ItemDefinition(
        key = ItemKey.of("ramrpg", "test_item"),
        displayName = Component.text("Test Item"),
        material = Material.IRON_SWORD,
        rarity = Rarity.RARE,
        categories = setOf(ItemCategory.SWORD),
    )

    private fun renderLine(quality: Double): Component {
        val instance = ItemInstanceData(identity = ItemIdentity(key = def.key), quality = quality)
        val ctx = LoreContext(definition = def, instance = instance)
        return LoreSection.RarityLine.render(ctx).single()
    }

    /**
     * The rarity line has no [LoreContext.viewer], so [LoreContext.localize] never resolves the
     * translation key (that's [net.kyori.adventure.translation.GlobalTranslator]'s job at render time,
     * out of scope for this pure test) -- find the raw quality-band [TranslatableComponent] and assert on
     * its key instead of the English text it would eventually resolve to.
     */
    private fun qualityBandKey(quality: Double): String {
        fun find(c: Component): TranslatableComponent? =
            (c as? TranslatableComponent) ?: c.children().firstNotNullOfOrNull(::find)
        return find(renderLine(quality))?.key() ?: error("no quality-band TranslatableComponent found")
    }

    @Test
    fun `rarity name is always present alongside the quality band`() {
        val text = PlainTextComponentSerializer.plainText().serialize(renderLine(0.5))
        assertTrue(text.contains("RARE"), text)
    }

    @Test
    fun `quality bands match the WP-2_1b placeholder table`() {
        assertEquals("ramrpg.item.quality.crude", qualityBandKey(0.0))
        assertEquals("ramrpg.item.quality.crude", qualityBandKey(0.19))
        assertEquals("ramrpg.item.quality.rough", qualityBandKey(0.20))
        assertEquals("ramrpg.item.quality.rough", qualityBandKey(0.39))
        assertEquals("ramrpg.item.quality.standard", qualityBandKey(0.40))
        assertEquals("ramrpg.item.quality.standard", qualityBandKey(0.5), "0.5 is the DEFAULT_QUALITY midpoint")
        assertEquals("ramrpg.item.quality.standard", qualityBandKey(0.59))
        assertEquals("ramrpg.item.quality.fine", qualityBandKey(0.60))
        assertEquals("ramrpg.item.quality.fine", qualityBandKey(0.79))
        assertEquals("ramrpg.item.quality.superior", qualityBandKey(0.80))
        assertEquals("ramrpg.item.quality.superior", qualityBandKey(0.94))
        assertEquals("ramrpg.item.quality.masterwork", qualityBandKey(0.95))
        assertEquals("ramrpg.item.quality.masterwork", qualityBandKey(1.0))
    }

    @Test
    fun `higher quality yields a different band than lower quality`() {
        assertNotEquals(qualityBandKey(0.05), qualityBandKey(0.99))
    }
}
