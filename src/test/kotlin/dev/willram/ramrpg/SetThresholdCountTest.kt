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
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.sets.SetDefinition
import dev.willram.ramrpg.api.sets.SetKey
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.core.services.activeThresholds
import dev.willram.ramrpg.core.services.equippedSetCount
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * WP-5.3: [equippedSetCount] counts only the wearer's NON-INERT set members (2.1c's shared
 * [dev.willram.ramrpg.api.items.isInert] check -- an unmet-requirement or zero-durability piece must
 * NOT count), and [activeThresholds] activates exactly the thresholds `<= activeCount` -- thresholds
 * stack (2/4 equipped activates only the 2-piece bonus; 4/4 activates both the 2-piece AND 4-piece
 * bonus). Pure -- no live server.
 */
class SetThresholdCountTest {

    private val combat = SkillKey.of("ramrpg", "combat")
    private val strength = StatKey.of("ramrpg", "strength")

    private class FakeState(
        private val skillLevels: Map<SkillKey, Int> = emptyMap(),
        private val statValues: Map<StatKey, Double> = emptyMap(),
    ) : ItemRequirementState {
        override fun skillLevel(skill: SkillKey): Int = skillLevels[skill] ?: 0
        override fun statValue(stat: StatKey): Double = statValues[stat] ?: 0.0
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

    private fun instance(def: ItemDefinition, durability: Int = 500): ItemInstanceData =
        ItemInstanceData(identity = ItemIdentity(key = def.key), durability = durability)

    private val helmet = memberDef("test_helmet")
    private val chestplate = memberDef("test_chestplate")
    private val leggings = memberDef("test_leggings", requirements = listOf(ItemRequirement.SkillLevel(combat, 10)))
    private val boots = memberDef("test_boots")
    private val outsider = memberDef("not_a_member")

    private val set = SetDefinition(
        key = SetKey.of("ramrpg", "test_set"),
        displayName = Component.text("Test Set"),
        members = setOf(helmet.key, chestplate.key, leggings.key, boots.key),
        thresholds = mapOf(
            2 to listOf(StatEffect(EffectKey.of("ramrpg", "t2"), strength, Scaling.flat(5.0), ModifierOperation.ADD)),
            4 to listOf(StatEffect(EffectKey.of("ramrpg", "t4"), strength, Scaling.flat(15.0), ModifierOperation.ADD)),
        ),
    )

    @Test
    fun `counts every equipped member that is a set member and not inert`() {
        val equipped = listOf(
            helmet to instance(helmet),
            chestplate to instance(chestplate),
            leggings to instance(leggings), // requirement met below
            boots to instance(boots),
        )
        assertEquals(4, equippedSetCount(equipped, met, set))
    }

    @Test
    fun `an item not in the set's members does not count even if equipped`() {
        val equipped = listOf(helmet to instance(helmet), outsider to instance(outsider))
        assertEquals(1, equippedSetCount(equipped, met, set))
    }

    @Test
    fun `an unmet-requirement piece does not count toward the set`() {
        val equipped = listOf(
            helmet to instance(helmet),
            chestplate to instance(chestplate),
            leggings to instance(leggings), // requirement UNMET against `unmet`
            boots to instance(boots),
        )
        // 3 non-inert members + 1 inert (unmet requirement) leggings -> only 3 count.
        assertEquals(3, equippedSetCount(equipped, unmet, set))
    }

    @Test
    fun `a zero-durability piece does not count toward the set even if requirements are met`() {
        val equipped = listOf(
            helmet to instance(helmet, durability = 0),
            chestplate to instance(chestplate),
            leggings to instance(leggings),
            boots to instance(boots),
        )
        assertEquals(3, equippedSetCount(equipped, met, set))
    }

    @Test
    fun `no equipped members counts zero`() {
        assertEquals(0, equippedSetCount(emptyList(), met, set))
    }

    @Test
    fun `2 of 4 activates only the 2-piece threshold, not the 4-piece`() {
        assertEquals(listOf(2), activeThresholds(set, activeCount = 2))
    }

    @Test
    fun `1 active member activates no threshold`() {
        assertEquals(emptyList<Int>(), activeThresholds(set, activeCount = 1))
    }

    @Test
    fun `4 of 4 activates both the 2-piece and the 4-piece threshold (thresholds stack)`() {
        assertEquals(listOf(2, 4), activeThresholds(set, activeCount = 4))
    }

    @Test
    fun `an inert piece prevents reaching a threshold it would otherwise unlock`() {
        // 2 active (helmet, chestplate) + 2 inert (leggings unmet, boots would-be but not equipped
        // here) -- only 2 count, so only the 2-piece threshold is active, never the 4-piece.
        val equipped = listOf(
            helmet to instance(helmet),
            chestplate to instance(chestplate),
            leggings to instance(leggings), // unmet
        )
        val count = equippedSetCount(equipped, unmet, set)
        assertEquals(2, count)
        assertEquals(listOf(2), activeThresholds(set, count))
    }
}
