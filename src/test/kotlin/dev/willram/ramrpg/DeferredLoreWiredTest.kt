package dev.willram.ramrpg

import dev.willram.ramrpg.api.effects.Scaling
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.identity.EffectKey
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
import dev.willram.ramrpg.api.items.LoreTemplate
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
 * WP-lore (wire-deferred-lore): proves that when a [LoreContext] is populated the way
 * `PacketItemRendererImpl.renderUncached` now populates it -- `requirementState` + `setBonus` +
 * `skillNameLookup` all wired -- the full [LoreTemplate.DEFAULT] surfaces every previously-deferred
 * section together in ONE render: requirement (met green / unmet red for THAT viewer), item level,
 * durability, quality (in the rarity line), and the set-bonus block.
 *
 * Pure, off-server: the renderer's own path clones an [org.bukkit.inventory.ItemStack] and writes
 * `ItemMeta`, which needs a live server, so this asserts at the [LoreContext] / [LoreTemplate] layer
 * (the exact context the renderer builds) rather than driving the packet path -- see the WP note. The
 * per-viewer CACHING guarantee (approach B) is covered separately by `RenderCacheTest`.
 */
class DeferredLoreWiredTest {

    private val combat = SkillKey.of("ramrpg", "combat")
    private val critChance = StatKey.of("ramrpg", "crit_chance")
    private val critDamage = StatKey.of("ramrpg", "crit_damage")

    private class FakeState(private val skillLevels: Map<SkillKey, Int> = emptyMap()) : ItemRequirementState {
        override fun skillLevel(skill: SkillKey): Int = skillLevels[skill] ?: 0
        override fun statValue(stat: StatKey): Double = 0.0
    }

    private val def = ItemDefinition(
        key = ItemKey.of("ramrpg", "crit_helmet"),
        displayName = Component.text("Crit Helmet"),
        material = Material.DIAMOND_HELMET,
        rarity = Rarity.EPIC,
        categories = setOf(ItemCategory.HELMET),
        itemLevel = 42,
        requirements = listOf(ItemRequirement.SkillLevel(combat, 10)),
    )

    private val instance = ItemInstanceData(identity = ItemIdentity(key = def.key), quality = 0.85, durability = 500)

    private val setInfo = SetLoreInfo(
        displayName = Component.text("Crit Set"),
        totalMembers = 4,
        activeCount = 2,
        thresholds = mapOf(
            2 to listOf(StatEffect(EffectKey.of("ramrpg", "t2"), critChance, Scaling.flat(8.0), ModifierOperation.ADD)),
            4 to listOf(StatEffect(EffectKey.of("ramrpg", "t4"), critDamage, Scaling.flat(25.0), ModifierOperation.ADD)),
        ),
    )

    /** Builds the LoreContext exactly as PacketItemRendererImpl.renderUncached does for [state]. */
    private fun contextFor(state: ItemRequirementState?) = LoreContext(
        definition = def,
        instance = instance,
        requirementState = state,
        setBonus = setInfo,
        skillNameLookup = { key -> if (key == combat) Component.text("Combat") else null },
    )

    private fun firstTranslatable(c: Component): TranslatableComponent? =
        (c as? TranslatableComponent) ?: c.children().firstNotNullOfOrNull(::firstTranslatable)

    private fun keyOf(c: Component): String? = firstTranslatable(c)?.key()

    private fun argsPlain(c: Component): String =
        firstTranslatable(c)?.arguments()?.joinToString(" ") {
            PlainTextComponentSerializer.plainText().serialize(it.asComponent())
        } ?: ""

    private fun plain(c: Component): String = PlainTextComponentSerializer.plainText().serialize(c)

    @Test
    fun `a viewer who meets the requirement sees a green requirement line in the full template`() {
        val lines = LoreTemplate.DEFAULT.render(contextFor(FakeState(mapOf(combat to 10))))
        val req = lines.single { keyOf(it) == "ramrpg.item.requirement.skill_level" }
        assertEquals(NamedTextColor.GREEN, req.color(), "met requirement is green")
        assertTrue(argsPlain(req).contains("Combat"), "skillNameLookup wired the display name: ${argsPlain(req)}")
        assertTrue(argsPlain(req).contains("10"), argsPlain(req))
        assertTrue(lines.none { keyOf(it) == "ramrpg.item.inert" }, "a met requirement adds no inert banner")
    }

    @Test
    fun `a viewer who fails the requirement sees a red requirement line plus the inert banner`() {
        val lines = LoreTemplate.DEFAULT.render(contextFor(FakeState())) // combat level 0 < 10
        val req = lines.single { keyOf(it) == "ramrpg.item.requirement.skill_level" }
        assertEquals(NamedTextColor.RED, req.color(), "unmet requirement is red")
        assertTrue(lines.any { keyOf(it) == "ramrpg.item.inert" }, "an unmet requirement surfaces the inert banner")
    }

    @Test
    fun `item level, durability and quality all render from item data in the full template`() {
        val lines = LoreTemplate.DEFAULT.render(contextFor(FakeState(mapOf(combat to 10))))

        val level = lines.single { keyOf(it) == "ramrpg.item.level" }
        assertTrue(argsPlain(level).contains("42"), argsPlain(level))

        val durability = lines.single { keyOf(it) == "ramrpg.item.durability" }
        assertTrue(argsPlain(durability).contains("500"), argsPlain(durability))

        // Quality band rides the rarity line: rarity name (plain) + a quality-band translatable. 0.85 -> superior.
        val rarity = lines.single { plain(it).contains("EPIC") }
        assertEquals("ramrpg.item.quality.superior", keyOf(rarity), "quality band shows in the rarity line")
    }

    @Test
    fun `the set-bonus block shows the viewer's active-over-total progress in the full template`() {
        val lines = LoreTemplate.DEFAULT.render(contextFor(FakeState(mapOf(combat to 10))))
        val header = lines.single { keyOf(it) == "ramrpg.set.progress" }
        assertEquals(NamedTextColor.YELLOW, header.color())
        val args = argsPlain(header)
        assertTrue(args.contains("Crit Set"), args)
        assertTrue(args.contains("2"), args)
        assertTrue(args.contains("4"), args)
    }
}
