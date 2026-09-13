package dev.willram.ramrpg

import dev.willram.ramrpg.api.effects.Scaling
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.identity.EffectKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.LoreContext
import dev.willram.ramrpg.api.items.LoreSection
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.SetLoreInfo
import dev.willram.ramrpg.api.stats.ModifierOperation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-5.3: [LoreSection.SetBonus] renders "Name (active/total)" with each threshold line lit (GREEN)
 * when active (`count <= activeCount`) and dimmed (DARK_GRAY) when not -- including its stat sub-lines,
 * which follow the SAME color. Pure -- a hand-built [SetLoreInfo] fixture, no live server (mirrors
 * `RequirementLoreRenderTest`'s convention for the other WP-2.1c lore sections).
 */
class SetLoreRenderTest {

    private val critChance = StatKey.of("ramrpg", "crit_chance")
    private val critDamage = StatKey.of("ramrpg", "crit_damage")

    private fun def(): ItemDefinition = ItemDefinition(
        key = ItemKey.of("ramrpg", "test_helmet"),
        displayName = Component.text("Test Helmet"),
        material = Material.DIAMOND_HELMET,
        rarity = Rarity.RARE,
        categories = setOf(ItemCategory.HELMET),
    )

    private fun instance(d: ItemDefinition): ItemInstanceData = ItemInstanceData(identity = ItemIdentity(key = d.key))

    /** Same technique `RequirementLoreRenderTest` uses: no [LoreContext.viewer] means [LoreContext.localize]
     *  never resolves the key, so find the [TranslatableComponent] and plain-serialize its args. */
    private fun argsPlainText(c: Component): String {
        fun find(x: Component): TranslatableComponent? =
            (x as? TranslatableComponent) ?: x.children().firstNotNullOfOrNull(::find)
        val t = find(c) ?: error("no TranslatableComponent found in $c")
        return t.arguments().joinToString(" ") { PlainTextComponentSerializer.plainText().serialize(it.asComponent()) }
    }

    private val setInfo = SetLoreInfo(
        displayName = Component.text("Crit Set"),
        totalMembers = 4,
        activeCount = 2,
        thresholds = mapOf(
            2 to listOf(StatEffect(EffectKey.of("ramrpg", "t2"), critChance, Scaling.flat(8.0), ModifierOperation.ADD)),
            4 to listOf(StatEffect(EffectKey.of("ramrpg", "t4"), critDamage, Scaling.flat(25.0), ModifierOperation.ADD)),
        ),
    )

    @Test
    fun `no set data wired renders nothing`() {
        val d = def()
        val ctx = LoreContext(definition = d, instance = instance(d)) // setBonus defaults to null
        assertTrue(LoreSection.SetBonus.render(ctx).isEmpty())
    }

    @Test
    fun `header shows the set name and active-over-total progress`() {
        val d = def()
        val ctx = LoreContext(definition = d, instance = instance(d), setBonus = setInfo)
        val lines = LoreSection.SetBonus.render(ctx)
        assertEquals(NamedTextColor.YELLOW, lines[0].color())
        val header = argsPlainText(lines[0])
        assertTrue(header.contains("Crit Set"), header)
        assertTrue(header.contains("2"), header)
        assertTrue(header.contains("4"), header)
    }

    @Test
    fun `an active threshold (2 of 4) renders lit (green), an inactive one (4 of 4) renders dimmed`() {
        val d = def()
        val ctx = LoreContext(definition = d, instance = instance(d), setBonus = setInfo)
        val lines = LoreSection.SetBonus.render(ctx)
        // [0] header, [1] "2 Pieces" label (active), [2] its stat line, [3] "4 Pieces" label (inactive), [4] its stat line.
        assertEquals(5, lines.size)
        assertEquals(NamedTextColor.GREEN, lines[1].color())
        assertTrue(argsPlainText(lines[1]).contains("2"))
        assertEquals(NamedTextColor.GREEN, lines[2].color())
        assertEquals(NamedTextColor.DARK_GRAY, lines[3].color())
        assertTrue(argsPlainText(lines[3]).contains("4"))
        assertEquals(NamedTextColor.DARK_GRAY, lines[4].color())
    }

    @Test
    fun `stat sub-lines show the threshold's stat value`() {
        val d = def()
        val ctx = LoreContext(definition = d, instance = instance(d), setBonus = setInfo)
        val lines = LoreSection.SetBonus.render(ctx)
        val statLine = PlainTextComponentSerializer.plainText().serialize(lines[2])
        assertTrue(statLine.contains("8"), statLine)
    }

    @Test
    fun `4 of 4 active renders every threshold lit`() {
        val d = def()
        val fullyActive = setInfo.copy(activeCount = 4)
        val ctx = LoreContext(definition = d, instance = instance(d), setBonus = fullyActive)
        val lines = LoreSection.SetBonus.render(ctx)
        assertEquals(NamedTextColor.GREEN, lines[1].color())
        assertEquals(NamedTextColor.GREEN, lines[3].color())
    }

    @Test
    fun `0 active renders every threshold dimmed`() {
        val d = def()
        val noneActive = setInfo.copy(activeCount = 0)
        val ctx = LoreContext(definition = d, instance = instance(d), setBonus = noneActive)
        val lines = LoreSection.SetBonus.render(ctx)
        assertEquals(NamedTextColor.DARK_GRAY, lines[1].color())
        assertEquals(NamedTextColor.DARK_GRAY, lines[3].color())
    }
}
