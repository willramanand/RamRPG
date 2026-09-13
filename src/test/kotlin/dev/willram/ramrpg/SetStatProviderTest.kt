package dev.willram.ramrpg

import dev.willram.ramrpg.api.effects.EffectAction
import dev.willram.ramrpg.api.effects.EffectTrigger
import dev.willram.ramrpg.api.effects.Scaling
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.effects.TriggeredEffect
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
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.sets.SetDefinition
import dev.willram.ramrpg.api.sets.SetKey
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.core.services.equippedSetCount
import dev.willram.ramrpg.core.services.setStatsFor
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-5.3: [setStatsFor] emits the active thresholds' [StatEffect] stats (as ordinary `StatModifier`s,
 * the exact same currency every other item-based provider emits) -- zero below the lowest threshold,
 * the union of every satisfied threshold once several are active, and it silently skips any non-stat
 * [dev.willram.ramrpg.api.effects.Effect] on a threshold (this provider only ever emits stats). Combined
 * with [equippedSetCount] (WP-2.1c inert exclusion), this is `SetStatProvider`'s complete pure core.
 * Pure -- no live server, no live `Player`/`ItemStack` (mirrors `InertItemContributesNoStatsTest`'s
 * `xStatsFor` convention for the four WP-2.1c providers).
 */
class SetStatProviderTest {

    private val combat = SkillKey.of("ramrpg", "combat")
    private val strength = StatKey.of("ramrpg", "strength")
    private val critChance = StatKey.of("ramrpg", "crit_chance")
    private val critDamage = StatKey.of("ramrpg", "crit_damage")

    private class FakeState(
        private val skillLevels: Map<SkillKey, Int> = emptyMap(),
    ) : ItemRequirementState {
        override fun skillLevel(skill: SkillKey): Int = skillLevels[skill] ?: 0
        override fun statValue(stat: StatKey): Double = 0.0
    }

    private val met = FakeState(skillLevels = mapOf(combat to 10))
    private val unmet = FakeState()

    private fun memberDef(slug: String, requirements: List<ItemRequirement> = emptyList()): ItemDefinition =
        ItemDefinition(
            key = ItemKey.of("ramrpg", slug),
            displayName = Component.text(slug),
            material = Material.DIAMOND_HELMET,
            rarity = Rarity.RARE,
            categories = setOf(ItemCategory.HELMET),
            requirements = requirements,
        )

    private fun instance(def: ItemDefinition): ItemInstanceData = ItemInstanceData(identity = ItemIdentity(key = def.key))

    private val helmet = memberDef("crit_helmet")
    private val chestplate = memberDef("crit_chestplate")
    private val leggings = memberDef("crit_leggings", requirements = listOf(ItemRequirement.SkillLevel(combat, 10)))
    private val boots = memberDef("crit_boots")

    // A threshold with a non-stat effect mixed in, to prove non-stat effects are silently skipped.
    private val nonStatEffect = TriggeredEffect(
        key = EffectKey.of("ramrpg", "t2_trigger"),
        trigger = EffectTrigger.OnHit,
        action = EffectAction { },
    )

    private val set = SetDefinition(
        key = SetKey.of("ramrpg", "crit_set"),
        displayName = Component.text("Crit Set"),
        members = setOf(helmet.key, chestplate.key, leggings.key, boots.key),
        thresholds = mapOf(
            2 to listOf(
                StatEffect(EffectKey.of("ramrpg", "t2_stat"), critChance, Scaling.flat(8.0), ModifierOperation.ADD),
                nonStatEffect,
            ),
            4 to listOf(
                StatEffect(EffectKey.of("ramrpg", "t4_stat"), critDamage, Scaling.flat(25.0), ModifierOperation.ADD),
            ),
        ),
    )

    @Test
    fun `below the lowest threshold emits nothing`() {
        assertTrue(setStatsFor(set, activeCount = 1).isEmpty())
        assertTrue(setStatsFor(set, activeCount = 0).isEmpty())
    }

    @Test
    fun `at the 2-piece threshold emits only the 2-piece stat, never the 4-piece stat`() {
        val mods = setStatsFor(set, activeCount = 2)
        assertEquals(1, mods.size)
        assertEquals(critChance, mods[0].stat)
        assertEquals(8.0, mods[0].amount)
        assertEquals(ModifierOperation.ADD, mods[0].operation)
        assertEquals(SourceType.ITEM, mods[0].source.type)
    }

    @Test
    fun `at the 4-piece threshold emits the union of the 2-piece AND 4-piece stats (thresholds stack)`() {
        val mods = setStatsFor(set, activeCount = 4)
        assertEquals(2, mods.size)
        assertTrue(mods.any { it.stat == critChance && it.amount == 8.0 })
        assertTrue(mods.any { it.stat == critDamage && it.amount == 25.0 })
    }

    @Test
    fun `a non-stat effect on an active threshold is silently skipped, never emitted as a StatModifier`() {
        val mods = setStatsFor(set, activeCount = 2)
        assertTrue(mods.none { it.stat == critChance && it.amount != 8.0 })
        assertEquals(1, mods.size, "only the StatEffect entry should have produced a modifier")
    }

    @Test
    fun `an inert piece keeps the count below threshold so the provider emits nothing`() {
        val equipped = listOf(
            helmet to instance(helmet),
            leggings to instance(leggings), // requirement UNMET against `unmet`
        )
        val count = equippedSetCount(equipped, unmet, set)
        assertEquals(1, count)
        assertTrue(setStatsFor(set, count).isEmpty())
    }

    @Test
    fun `full end-to-end - 4 equipped, none inert, yields both thresholds' stats`() {
        val equipped = listOf(
            helmet to instance(helmet),
            chestplate to instance(chestplate),
            leggings to instance(leggings),
            boots to instance(boots),
        )
        val count = equippedSetCount(equipped, met, set)
        assertEquals(4, count)
        val mods = setStatsFor(set, count)
        assertEquals(2, mods.size)
    }
}
