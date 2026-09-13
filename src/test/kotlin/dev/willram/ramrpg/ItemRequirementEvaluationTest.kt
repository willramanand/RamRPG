package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemRequirement
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.isMet
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-2.1a: [ItemRequirement.isMet] evaluated against a fake, pure [ItemRequirementState] -- no live
 * Bukkit [org.bukkit.entity.Player] needed. [ItemRequirement.PerkOwned]'s stub behavior (fail-closed --
 * always unmet until WP-5.1a ships PerkService) is asserted explicitly so it can never regress into a
 * silent no-op "met".
 */
class ItemRequirementEvaluationTest {

    private class FakeState(
        private val skillLevels: Map<SkillKey, Int> = emptyMap(),
        private val statValues: Map<StatKey, Double> = emptyMap(),
    ) : ItemRequirementState {
        override fun skillLevel(skill: SkillKey): Int = skillLevels[skill] ?: 0
        override fun statValue(stat: StatKey): Double = statValues[stat] ?: 0.0
    }

    private val combat = SkillKey.of("ramrpg", "combat")
    private val strength = StatKey.of("ramrpg", "strength")

    @Test
    fun `skill level requirement is met at or above the required level`() {
        val req = ItemRequirement.SkillLevel(combat, 10)
        assertTrue(req.isMet(FakeState(skillLevels = mapOf(combat to 10))))
        assertTrue(req.isMet(FakeState(skillLevels = mapOf(combat to 15))))
    }

    @Test
    fun `skill level requirement is unmet below the required level`() {
        val req = ItemRequirement.SkillLevel(combat, 10)
        assertFalse(req.isMet(FakeState(skillLevels = mapOf(combat to 9))))
        assertFalse(req.isMet(FakeState()))
    }

    @Test
    fun `stat threshold requirement is met at or above the minimum`() {
        val req = ItemRequirement.StatThreshold(strength, 20.0)
        assertTrue(req.isMet(FakeState(statValues = mapOf(strength to 20.0))))
        assertTrue(req.isMet(FakeState(statValues = mapOf(strength to 25.0))))
    }

    @Test
    fun `stat threshold requirement is unmet below the minimum`() {
        val req = ItemRequirement.StatThreshold(strength, 20.0)
        assertFalse(req.isMet(FakeState(statValues = mapOf(strength to 19.999))))
        assertFalse(req.isMet(FakeState()))
    }

    @Test
    fun `perk owned is a documented fail-closed stub until WP-5_1a -- always unmet`() {
        val req = ItemRequirement.PerkOwned(ContentId.of("ramrpg", "dragon_slayer"))
        // No PerkService (and no PerkKey) exists yet; evaluating this arm must NOT silently report
        // "met" just because there is nothing real to check against yet -- see ItemRequirement.PerkOwned
        // and ItemRequirement.isMet's KDoc for the fail-closed decision.
        assertFalse(req.isMet(FakeState()))

        // Even a state that (hypothetically) "has everything" cannot make it met -- the stub does not
        // consult the state at all, by design.
        val generousState = FakeState(
            skillLevels = mapOf(combat to Int.MAX_VALUE),
            statValues = mapOf(strength to Double.MAX_VALUE),
        )
        assertFalse(req.isMet(generousState))
    }
}
