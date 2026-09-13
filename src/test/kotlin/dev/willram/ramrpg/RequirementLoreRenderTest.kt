package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemRequirement
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.LoreContext
import dev.willram.ramrpg.api.items.LoreSection
import dev.willram.ramrpg.api.items.Rarity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-2.1c: [LoreSection.Requirements], [LoreSection.ItemLevel] and [LoreSection.Durability] render
 * correctly (pure -- a fake [ItemRequirementState], no live server), including met vs. unmet
 * requirement styling (green vs. red) and the shared inert banner.
 */
class RequirementLoreRenderTest {

    private val combat = SkillKey.of("ramrpg", "combat")
    private val strength = StatKey.of("ramrpg", "strength")

    private class FakeState(
        private val skillLevels: Map<SkillKey, Int> = emptyMap(),
        private val statValues: Map<StatKey, Double> = emptyMap(),
    ) : ItemRequirementState {
        override fun skillLevel(skill: SkillKey): Int = skillLevels[skill] ?: 0
        override fun statValue(stat: StatKey): Double = statValues[stat] ?: 0.0
    }

    private fun def(itemLevel: Int = 15, requirements: List<ItemRequirement> = listOf(ItemRequirement.SkillLevel(combat, 10))) =
        ItemDefinition(
            key = ItemKey.of("ramrpg", "test_item"),
            displayName = Component.text("Test Item"),
            material = Material.IRON_SWORD,
            rarity = Rarity.UNCOMMON,
            categories = setOf(ItemCategory.SWORD),
            itemLevel = itemLevel,
            requirements = requirements,
        )

    private fun instance(d: ItemDefinition, durability: Int = 500) =
        ItemInstanceData(identity = ItemIdentity(key = d.key), durability = durability)

    /**
     * No [LoreContext.viewer] means [LoreContext.localize] never resolves the translation key (that's
     * [net.kyori.adventure.translation.GlobalTranslator]'s job at render time, out of scope here) -- find
     * the line's [TranslatableComponent] and plain-serialize its ARGS (which are always plain
     * `Component.text(...)`, never nested translatables) to check the interpolated values directly.
     */
    private fun argsPlainText(c: Component): String {
        fun find(x: Component): TranslatableComponent? =
            (x as? TranslatableComponent) ?: x.children().firstNotNullOfOrNull(::find)
        val t = find(c) ?: error("no TranslatableComponent found in $c")
        return t.arguments().joinToString(" ") { PlainTextComponentSerializer.plainText().serialize(it.asComponent()) }
    }

    @Test
    fun `a met requirement renders green with no inert banner`() {
        val d = def()
        val ctx = LoreContext(definition = d, instance = instance(d), requirementState = FakeState(skillLevels = mapOf(combat to 10)))
        val lines = LoreSection.Requirements.render(ctx)
        assertEquals(1, lines.size, "no unmet requirement means no inert banner line")
        assertEquals(NamedTextColor.GREEN, lines[0].color())
        assertTrue(argsPlainText(lines[0]).contains("10"), argsPlainText(lines[0]))
    }

    @Test
    fun `an unmet requirement renders red plus an inert banner`() {
        val d = def()
        val ctx = LoreContext(definition = d, instance = instance(d), requirementState = FakeState())
        val lines = LoreSection.Requirements.render(ctx)
        assertEquals(2, lines.size, "an unmet requirement adds the inert banner line")
        assertEquals(NamedTextColor.RED, lines[0].color())
        assertEquals(NamedTextColor.DARK_RED, lines[1].color())
    }

    @Test
    fun `no requirement state wired renders as unmet (fail closed), never silently satisfied`() {
        val d = def()
        val ctx = LoreContext(definition = d, instance = instance(d)) // requirementState defaults to null
        val lines = LoreSection.Requirements.render(ctx)
        assertEquals(NamedTextColor.RED, lines[0].color())
    }

    @Test
    fun `an item with no requirements renders nothing`() {
        val d = def(requirements = emptyList())
        val ctx = LoreContext(definition = d, instance = instance(d))
        assertTrue(LoreSection.Requirements.render(ctx).isEmpty())
    }

    @Test
    fun `a stat threshold requirement label includes the threshold value`() {
        val d = def(requirements = listOf(ItemRequirement.StatThreshold(strength, 20.0)))
        val ctx = LoreContext(definition = d, instance = instance(d), requirementState = FakeState(statValues = mapOf(strength to 25.0)))
        val lines = LoreSection.Requirements.render(ctx)
        assertEquals(NamedTextColor.GREEN, lines[0].color())
        assertTrue(argsPlainText(lines[0]).contains("20"), argsPlainText(lines[0]))
    }

    @Test
    fun `item level line shows the definition's item level`() {
        val d = def(itemLevel = 42)
        val ctx = LoreContext(definition = d, instance = instance(d))
        val lines = LoreSection.ItemLevel.render(ctx)
        assertEquals(1, lines.size)
        assertTrue(argsPlainText(lines[0]).contains("42"), argsPlainText(lines[0]))
    }

    @Test
    fun `durability line shows current over max with no inert banner while durability remains`() {
        val d = def()
        val ctx = LoreContext(definition = d, instance = instance(d, durability = 500))
        val lines = LoreSection.Durability.render(ctx)
        assertEquals(1, lines.size)
        assertEquals(NamedTextColor.GRAY, lines[0].color())
        assertTrue(argsPlainText(lines[0]).contains("500"), argsPlainText(lines[0]))
    }

    @Test
    fun `durability line adds the inert banner at zero durability`() {
        val d = def()
        val ctx = LoreContext(definition = d, instance = instance(d, durability = 0))
        val lines = LoreSection.Durability.render(ctx)
        assertEquals(2, lines.size)
        assertEquals(NamedTextColor.RED, lines[0].color())
        assertEquals(NamedTextColor.DARK_RED, lines[1].color())
    }
}
