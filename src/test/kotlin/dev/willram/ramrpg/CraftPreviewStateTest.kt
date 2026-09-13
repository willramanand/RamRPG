package dev.willram.ramrpg

import dev.willram.ramrpg.api.crafting.CraftContext
import dev.willram.ramrpg.api.crafting.CraftFailure
import dev.willram.ramrpg.api.crafting.CraftResult
import dev.willram.ramrpg.api.crafting.OutcomePlan
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeKey
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.StationKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.core.services.CraftEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * WP-3.1b: the PURE preview state the station menu renders -- computed by [CraftEngine.preview] (the same
 * [dev.willram.ramrpg.api.crafting.CraftPlanner] a real craft gates on) and mapped to a generic
 * [CraftEngine.Application] by [CraftEngine.applicationFor]. Asserts valid vs invalid inputs, and that a
 * preview NEVER mutates the input instance (data-class copy semantics). No server, no ItemStack.
 */
class CraftPreviewStateTest {

    private val station = StationKey.of("ramrpg", "smithing")
    private val steel = ItemKey.of("ramrpg", "steel_sword")
    private val blade = ItemKey.of("ramrpg", "blade")
    private val crafter = UUID.randomUUID()

    private class FakeState : ItemRequirementState {
        override fun skillLevel(skill: SkillKey) = 0
        override fun statValue(stat: StatKey) = 0.0
    }

    private fun recipe(outcome: RecipeOutcome) =
        Recipe(RecipeKey.of("ramrpg", "r"), station, inputs = emptyList(), requirements = emptyList(), outcome = outcome)

    @Test
    fun `a valid new-item craft previews as a Create application`() {
        val result = CraftEngine.preview(recipe(RecipeOutcome.NewItem(steel, count = 2)), CraftContext(FakeState(), skillLevel = 40, seed = 7L))
        result as CraftResult.Success

        val app = CraftEngine.applicationFor(result.outcome, crafter)
        app as CraftEngine.Application.CreateItem
        assertEquals(steel, app.item)
        assertEquals(2, app.count)
        assertEquals(crafter, app.craftedBy)
        assertEquals((result.outcome as OutcomePlan.Create).quality, app.quality)
    }

    @Test
    fun `a valid upgrade previews as a Modify application without touching the input instance`() {
        val target = ItemInstanceData(ItemIdentity(blade), upgradeLevel = 1)
        val result = CraftEngine.preview(
            recipe(RecipeOutcome.UpgradeInput(upgradeLevels = 2)),
            CraftContext(FakeState(), target = target),
        )
        result as CraftResult.Success

        val app = CraftEngine.applicationFor(result.outcome, crafter)
        app as CraftEngine.Application.ModifyTarget
        assertEquals(3, app.result.upgradeLevel, "preview shows the transformed instance")
        assertEquals(1, target.upgradeLevel, "the source instance is never mutated by a preview")
    }

    @Test
    fun `validateApplication is the abort gate that runs before any escrow is consumed`() {
        // CREATE: a recipe whose output key has no ItemDefinition aborts (INVALID_TARGET) -- so the caller
        // never drains escrow for an item it cannot produce (WP-3.1b review R2).
        val create = CraftEngine.Application.CreateItem(steel, quality = 0.5, count = 1, craftedBy = crafter)
        assertNull(CraftEngine.validateApplication(create, outputDefExists = true, targetAmount = null))
        assertEquals(CraftFailure.INVALID_TARGET, CraftEngine.validateApplication(create, outputDefExists = false, targetAmount = null))

        // MODIFY: exactly one target item, present, is required.
        val modify = CraftEngine.Application.ModifyTarget(ItemInstanceData(ItemIdentity(blade)))
        assertNull(CraftEngine.validateApplication(modify, outputDefExists = true, targetAmount = 1))
        assertEquals(CraftFailure.NO_TARGET_ITEM, CraftEngine.validateApplication(modify, outputDefExists = true, targetAmount = null))
        // N3: one craft cost must never transform a whole stack.
        assertEquals(CraftFailure.INVALID_TARGET, CraftEngine.validateApplication(modify, outputDefExists = true, targetAmount = 2))
    }

    @Test
    fun `invalid inputs preview as the matching failure, producing no application`() {
        // A target-transforming outcome with no target -> NO_TARGET_ITEM.
        val noTarget = CraftEngine.preview(recipe(RecipeOutcome.UpgradeInput()), CraftContext(FakeState(), target = null))
        assertEquals(CraftFailure.NO_TARGET_ITEM, (noTarget as CraftResult.Failure).reason)

        // A socket index the target does not have -> INVALID_TARGET.
        val badSocket = CraftEngine.preview(
            recipe(RecipeOutcome.RemoveGem(socketIndex = 3)),
            CraftContext(FakeState(), target = ItemInstanceData(ItemIdentity(blade))),
        )
        assertEquals(CraftFailure.INVALID_TARGET, (badSocket as CraftResult.Failure).reason)
    }
}
