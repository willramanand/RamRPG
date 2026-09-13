package dev.willram.ramrpg

import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.crafting.ConsumedInput
import dev.willram.ramrpg.api.crafting.CraftResult
import dev.willram.ramrpg.api.crafting.OutcomePlan
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeCost
import dev.willram.ramrpg.api.crafting.RecipeKey
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.RecipeOutcomeKind
import dev.willram.ramrpg.api.crafting.StationKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.skills.XpContext
import dev.willram.ramrpg.core.services.CraftXp
import dev.willram.ramrpg.core.services.CraftXpSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-3.1c: the PURE craft-XP model and the [CraftXpSource] it feeds -- asserted with no server and no
 * [org.bukkit.entity.Player]. Proves the rate formula (base x tier x quality x critical) and that the
 * XpSource surfaces the right skill, key and amount, so a downstream `SkillService.addXp` grants exactly
 * this. [XpContext] is a proxy stub because [CraftXpSource.xp] never reads it (the service applies the
 * multiplier), mirroring SkillXpRewardTest.
 */
class CraftXpSourceTest {

    private val cooking = SkillKey.of("ramrpg", "cooking")
    private val ctx: XpContext = ProxyFakes.stub(XpContext::class.java)
    private val EPS = 1e-9

    private fun recipe(outcome: RecipeOutcome): Recipe =
        Recipe(RecipeKey.of("ramrpg", "r"), StationKey.of("ramrpg", "smithing"), inputs = emptyList(), outcome = outcome)

    private fun success(quality: Double, critical: Boolean, outcome: OutcomePlan): CraftResult.Success =
        CraftResult.Success(
            outcome = outcome,
            consumption = listOf(ConsumedInput(count = 1, itemKey = ItemKey.of("ramrpg", "wheat"))),
            cost = RecipeCost.FREE,
            quality = quality,
            critical = critical,
        )

    // ---- rate model ------------------------------------------------------------------------------

    @Test
    fun `base craft is the per-kind base at tier 1, quality 0, no crit`() {
        assertEquals(10.0, CraftXp.xpFor(RecipeOutcomeKind.NEW_ITEM, tier = 1, quality = 0.0, critical = false), EPS)
        assertEquals(4.0, CraftXp.xpFor(RecipeOutcomeKind.REPAIR, tier = 1, quality = 0.0, critical = false), EPS)
    }

    @Test
    fun `tier raises XP by half a base per tier above one`() {
        assertEquals(1.0, CraftXp.tierMultiplier(1), EPS)
        assertEquals(1.5, CraftXp.tierMultiplier(2), EPS)
        assertEquals(2.5, CraftXp.tierMultiplier(4), EPS)
        // tier is clamped to >= 1 so a bad tier never yields negative/zero XP.
        assertEquals(1.0, CraftXp.tierMultiplier(0), EPS)
        assertEquals(15.0, CraftXp.xpFor(RecipeOutcomeKind.NEW_ITEM, tier = 2, quality = 0.0, critical = false), EPS)
    }

    @Test
    fun `quality raises XP up to plus fifty percent, clamped to zero-one`() {
        assertEquals(1.0, CraftXp.qualityMultiplier(0.0), EPS)
        assertEquals(1.5, CraftXp.qualityMultiplier(1.0), EPS)
        assertEquals(1.5, CraftXp.qualityMultiplier(2.0), EPS) // clamped
        assertEquals(15.0, CraftXp.xpFor(RecipeOutcomeKind.NEW_ITEM, tier = 1, quality = 1.0, critical = false), EPS)
    }

    @Test
    fun `a critical craft doubles the grant`() {
        val normal = CraftXp.xpFor(RecipeOutcomeKind.NEW_ITEM, tier = 3, quality = 0.4, critical = false)
        val crit = CraftXp.xpFor(RecipeOutcomeKind.NEW_ITEM, tier = 3, quality = 0.4, critical = true)
        assertEquals(normal * 2.0, crit, EPS)
    }

    @Test
    fun `factors compound multiplicatively`() {
        // base 10 (NEW_ITEM) * tier2 (1.5) * quality0.5 (1.25) * crit (2) = 37.5
        assertEquals(37.5, CraftXp.xpFor(RecipeOutcomeKind.NEW_ITEM, tier = 2, quality = 0.5, critical = true), EPS)
    }

    @Test
    fun `higher skill inputs strictly raise XP`() {
        val low = CraftXp.xpFor(RecipeOutcomeKind.NEW_ITEM, tier = 1, quality = 0.2, critical = false)
        val high = CraftXp.xpFor(RecipeOutcomeKind.NEW_ITEM, tier = 3, quality = 0.9, critical = false)
        assertTrue(high > low, "tier+quality should raise XP: $high !> $low")
    }

    // ---- the XpSource ----------------------------------------------------------------------------

    @Test
    fun `CraftXpSource surfaces the skill, the single craft key and the computed amount`() {
        val src = CraftXpSource(cooking, RecipeOutcomeKind.NEW_ITEM, tier = 2, quality = 1.0, critical = false)
        assertEquals(cooking, src.skill)
        assertEquals("ramrpg:craft", src.key.toString())
        assertEquals(CraftXp.xpFor(RecipeOutcomeKind.NEW_ITEM, 2, 1.0, false), src.xp(ctx), EPS)
        assertEquals(22.5, src.xp(ctx), EPS) // 10 base * 1.5 tier * 1.5 quality
    }

    @Test
    fun `forCraft builds a source using a NewItem's quality skill and the result's quality and crit`() {
        val r = recipe(RecipeOutcome.NewItem(ItemKey.of("ramrpg", "bread"), qualitySkill = cooking))
        val s = success(quality = 0.5, critical = true, outcome = OutcomePlan.Create(ItemKey.of("ramrpg", "bread"), 0.5))
        val src = CraftXpSource.forCraft(r, s, tier = 2)!!
        assertEquals(cooking, src.skill)
        assertEquals(RecipeOutcomeKind.NEW_ITEM, src.kind)
        assertEquals(0.5, src.quality, EPS)
        assertTrue(src.critical)
        assertEquals(CraftXp.xpFor(RecipeOutcomeKind.NEW_ITEM, 2, 0.5, true), src.xp(ctx), EPS)
    }

    @Test
    fun `forCraft returns null when no skill drives the craft's XP`() {
        // A Repair recipe carries no quality skill, and none is supplied -> nothing to grant XP to.
        val r = recipe(RecipeOutcome.Repair())
        val s = success(quality = 0.3, critical = false, outcome = OutcomePlan.Create(ItemKey.of("ramrpg", "x"), 0.3))
        assertNull(CraftXpSource.forCraft(r, s, tier = 1))
    }

    @Test
    fun `forCraft honours an explicit skill override for a non-NewItem outcome`() {
        val r = recipe(RecipeOutcome.Repair())
        val s = success(quality = 0.0, critical = false, outcome = OutcomePlan.Create(ItemKey.of("ramrpg", "x"), 0.0))
        val src = CraftXpSource.forCraft(r, s, tier = 1, skill = cooking)!!
        assertEquals(cooking, src.skill)
        assertEquals(RecipeOutcomeKind.REPAIR, src.kind)
        assertEquals(4.0, src.xp(ctx), EPS)
    }
}
