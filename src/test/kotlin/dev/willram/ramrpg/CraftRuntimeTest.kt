package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.crafting.CraftFailure
import dev.willram.ramrpg.api.crafting.OutcomePlan
import dev.willram.ramrpg.api.crafting.RecipeCost
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.plan
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.RarityRules
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.items.SocketData
import dev.willram.ramrpg.core.crafting.CraftRuntime
import dev.willram.ramrpg.core.crafting.ReforgeOutcomes
import dev.willram.ramrpg.core.crafting.SocketOutcomes
import dev.willram.ramrpg.core.crafting.UpgradeMessages
import dev.willram.ramrpg.core.crafting.UpgradeOutcomes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-3.1e: the PURE outcome-runtime dispatch. Asserts that [CraftRuntime.effectiveCraft] reuses the
 * already-tested pure helpers ([UpgradeOutcomes] / [SocketOutcomes] / [ReforgeOutcomes]) to add the cap,
 * failure and rarity-scaled-cost the generic engine does not model -- with no server, no
 * [org.bukkit.entity.Player] and no mocked menu (the live apply/XP/menu path stays M10-blocked, so the
 * hard logic lives HERE where it is testable). Every assertion pins cost + resultData + status, and the
 * seeded upgrade roll is deterministic.
 */
class CraftRuntimeTest {

    private val EPS = 1e-9
    private val swordKey = ItemKey.of("ramrpg", "sword")
    private val generic = ContentId.of("ramrpg", "generic")

    private fun instance(upgradeLevel: Int = 0, sockets: List<SocketData> = emptyList()): ItemInstanceData =
        ItemInstanceData(identity = ItemIdentity(swordKey), upgradeLevel = upgradeLevel, sockets = sockets)

    // -- Upgrade: cap / failure / rarity-scaled cost -------------------------------------------------

    @Test
    fun `upgrade success in the safe zone caps the level, scales cost by rarity, and signals success`() {
        val target = instance(upgradeLevel = 0)
        val outcome = RecipeOutcome.UpgradeInput(upgradeLevels = 1)
        val plan = outcome.plan(target, 0.5)!!

        // Level 0 -> 1 is in the safe zone (failureChance == 0), so this is deterministic for any seed.
        val eff = CraftRuntime.effectiveCraft(outcome, plan, target, Rarity.RARE, RecipeCost(money = 100.0, experienceLevels = 3), seed = 987L)

        assertEquals(CraftRuntime.CraftStatus.SUCCESS, eff.status)
        assertEquals(1, eff.resultData!!.upgradeLevel, "the upgraded (capped) level is written")
        // Cost is rarity-scaled, NOT the static conf cost: money via UpgradeOutcomes, XP via RarityRules.
        assertEquals(UpgradeOutcomes.upgradeGoldCost(0, 1, Rarity.RARE), eff.cost.money, EPS)
        assertEquals(RarityRules.upgradeXpCost(0, 1, Rarity.RARE), eff.cost.experienceLevels)
        assertEquals(UpgradeMessages.success(), eff.message)
    }

    @Test
    fun `upgrade failure above the safe threshold leaves the item unchanged and charges only the failure fraction`() {
        val target = instance(upgradeLevel = 8) // RARE cap 10; reaching 9 is above the safe threshold (5)
        val outcome = RecipeOutcome.UpgradeInput(upgradeLevels = 1)
        val plan = outcome.plan(target, 0.5)!!
        val failSeed = (0L..100_000L).first { UpgradeOutcomes.isFailure(9, it) }

        val eff = CraftRuntime.effectiveCraft(outcome, plan, target, Rarity.RARE, RecipeCost(money = 800.0), seed = failSeed)

        assertEquals(CraftRuntime.CraftStatus.FAILURE, eff.status, "a rolled failure is still a consumed attempt")
        assertEquals(8, eff.resultData!!.upgradeLevel, "the item is returned UNCHANGED on failure")
        assertEquals(0, eff.cost.experienceLevels, "no XP levels are spent on a failed attempt")
        val fullGold = UpgradeOutcomes.upgradeGoldCost(8, 9, Rarity.RARE)
        assertEquals(UpgradeOutcomes.goldCostOnFailure(fullGold), eff.cost.money, EPS)
        assertEquals(UpgradeMessages.failure(), eff.message)
    }

    @Test
    fun `upgrade at cap is REJECTED before consume with the at-cap message`() {
        val target = instance(upgradeLevel = UpgradeOutcomes.cap(Rarity.RARE)) // already maxed
        val outcome = RecipeOutcome.UpgradeInput(upgradeLevels = 1)
        val plan = outcome.plan(target, 0.5)!!

        val eff = CraftRuntime.effectiveCraft(outcome, plan, target, Rarity.RARE, RecipeCost.FREE, seed = 1L)

        assertEquals(CraftRuntime.CraftStatus.REJECTED, eff.status)
        assertEquals(CraftFailure.INVALID_TARGET, eff.rejection)
        assertNull(eff.resultData, "nothing is written when the craft is rejected")
        assertEquals(RecipeCost.FREE, eff.cost, "an at-cap reject charges nothing")
        assertEquals(UpgradeMessages.atCap(), eff.message)
    }

    @Test
    fun `the upgrade roll is seeded-deterministic`() {
        val target = instance(upgradeLevel = 8)
        val outcome = RecipeOutcome.UpgradeInput(upgradeLevels = 1)
        val plan = outcome.plan(target, 0.5)!!

        val a = CraftRuntime.effectiveCraft(outcome, plan, target, Rarity.RARE, RecipeCost(money = 800.0), seed = 42L)
        val b = CraftRuntime.effectiveCraft(outcome, plan, target, Rarity.RARE, RecipeCost(money = 800.0), seed = 42L)
        assertEquals(a, b, "same (outcome, target, rarity, seed) -> identical EffectiveCraft")
    }

    // -- Add socket: slot cap + scaled cut cost -----------------------------------------------------

    @Test
    fun `add socket over the rarity slot cap is REJECTED with the cap-reached message`() {
        // COMMON caps at 1 socket; the target already has one, so cutting another exceeds the cap.
        val target = instance(sockets = listOf(SocketData(generic)))
        val outcome = RecipeOutcome.AddSocket(socketType = generic, count = 1)
        val plan = outcome.plan(target, 0.5)!!

        val eff = CraftRuntime.effectiveCraft(outcome, plan, target, Rarity.COMMON, RecipeCost(money = 500.0, experienceLevels = 3), seed = 0L)

        assertEquals(CraftRuntime.CraftStatus.REJECTED, eff.status)
        assertEquals(CraftFailure.INVALID_TARGET, eff.rejection)
        assertNull(eff.resultData)
        assertEquals(SocketOutcomes.message(SocketOutcomes.SocketOpError.SLOT_CAP_EXCEEDED), eff.message)
    }

    @Test
    fun `add socket within the cap succeeds with the scaled cut cost and the plan's appended sockets`() {
        val target = instance(sockets = emptyList())
        val outcome = RecipeOutcome.AddSocket(socketType = generic, count = 1)
        val plan = outcome.plan(target, 0.5)!!

        // A large, distinctive conf cost proves the runtime substitutes the SocketOutcomes cost, not the conf one.
        val eff = CraftRuntime.effectiveCraft(outcome, plan, target, Rarity.EPIC, RecipeCost(money = 9_999.0), seed = 0L)

        assertEquals(CraftRuntime.CraftStatus.SUCCESS, eff.status)
        assertEquals(SocketOutcomes.addSocketCost(existingSlots = 0), eff.cost)
        assertEquals((plan as OutcomePlan.Modify).result, eff.resultData, "the plan's appended-sockets instance is reused")
        assertEquals(1, eff.resultData!!.sockets.size)
    }

    // -- Reforge: rarity-scaled cost ----------------------------------------------------------------

    @Test
    fun `reforge succeeds with the rarity-scaled reforge cost and the plan result`() {
        val target = instance()
        val reforge = ReforgeKey(ContentId.of("ramrpg", "sharp"))
        val outcome = RecipeOutcome.Reforge(reforge)
        val plan = outcome.plan(target, 0.5)!!

        val eff = CraftRuntime.effectiveCraft(outcome, plan, target, Rarity.LEGENDARY, RecipeCost(money = 250.0, experienceLevels = 10), seed = 0L)

        assertEquals(CraftRuntime.CraftStatus.SUCCESS, eff.status)
        assertEquals(ReforgeOutcomes.reforgeCost(Rarity.LEGENDARY), eff.cost, "cost scales to the target's rarity, not the conf baseline")
        assertEquals((plan as OutcomePlan.Modify).result, eff.resultData)
        assertEquals(reforge, eff.resultData!!.reforge)
    }

    // -- Passthrough kinds: plan result + conf cost, unchanged --------------------------------------

    @Test
    fun `a Modify passthrough kind keeps the plan result and the conf cost unchanged`() {
        val target = instance()
        val repair = RecipeOutcome.Repair()
        val plan = repair.plan(target, 0.5)!!
        val confCost = RecipeCost(money = 12.0, experienceLevels = 1)

        val eff = CraftRuntime.effectiveCraft(repair, plan, target, Rarity.MYTHIC, confCost, seed = 0L)

        assertEquals(CraftRuntime.CraftStatus.SUCCESS, eff.status)
        assertEquals(confCost, eff.cost, "a passthrough kind is not rarity-scaled")
        assertEquals((plan as OutcomePlan.Modify).result, eff.resultData)
        assertNull(eff.message, "a passthrough kind adds no bespoke message")
    }

    @Test
    fun `a fresh-item passthrough kind keeps the conf cost and writes no Modify result`() {
        val newItem = RecipeOutcome.NewItem(ItemKey.of("ramrpg", "bread"))
        val plan = newItem.plan(null, 0.7)!!
        val confCost = RecipeCost(money = 5.0)

        val eff = CraftRuntime.effectiveCraft(newItem, plan, target = null, rarity = Rarity.COMMON, confCost = confCost, seed = 0L)

        assertEquals(CraftRuntime.CraftStatus.SUCCESS, eff.status)
        assertEquals(confCost, eff.cost)
        assertNull(eff.resultData, "a Create outcome writes no in-place Modify instance")
        assertTrue(plan is OutcomePlan.Create)
    }
}
