/**
 * WP-3.1c: crafting XP -- the PURE rate model ([CraftXp]) and the [XpSource] that grants it
 * ([CraftXpSource]). Skill XP for a successful craft flows through the EXISTING XpSource mechanism
 * (`api/skills`): a [CraftXpSource] is an ordinary [XpSource] whose [XpSource.skill] is the recipe's
 * quality skill and whose [XpSource.xp] returns the pure [CraftXp.xpFor] amount, so
 * [dev.willram.ramrpg.api.skills.SkillService.addXp] resolves the curve, levels up and fires the
 * level-up callbacks exactly as for mining/fishing/etc. There is deliberately no bespoke craft-XP path
 * (the WP-1.2b `skill_xp` reward's [dev.willram.ramrpg.core.rewards.SkillXpRewardAction] is the same
 * XpSource-then-addXp shape).
 *
 * PURITY. [CraftXp] and [CraftXpSource] never touch a live server, so CraftXpSourceTest asserts the XP a
 * craft yields with no [org.bukkit.entity.Player] and no server. `tier` is a pure input: the material
 * tier (WP-3.2a's tier ladder, `tier-N` tag) of the crafted item, which only the caller can resolve from
 * the item definition -- the formula takes it as a number, so the model stays pure and testable.
 *
 * TRIGGER SEAM (open question -- reported to the orchestrator). Nothing fires this yet. The craft path
 * ([dev.willram.ramrpg.core.services.CraftingServiceImpl.craft], out of scope for this WP) applies its
 * side effects INLINE and emits NO craft-completion event or hook, and the sole success site,
 * [dev.willram.ramrpg.core.menus.StationMenu]'s craft button (also out of scope), likewise fires no
 * event a listener could subscribe to. So there is no seam to hang XP off without editing an out-of-scope
 * file. The wiring is a one-liner at that success site once a seam exists:
 * `CraftXpSource.forCraft(recipe, success, tier)?.let { skillService.addXp(player, it) }`. This WP ships
 * the pure model + the source + tests; the trigger is a follow-up.
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramrpg.api.crafting.CraftResult
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.RecipeOutcomeKind
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.api.skills.XpContext
import dev.willram.ramrpg.api.skills.XpSource

/**
 * The PURE, deterministic crafting-XP rate model. `xpFor` folds four factors:
 *
 * ```
 * xp = base(kind) * tierMultiplier(tier) * qualityMultiplier(quality) * (critical ? CRITICAL_MULTIPLIER : 1)
 * ```
 *
 * See `docs/design/3.1c-crafting-xp.md` for the base table and the derivation. Higher-tier materials,
 * higher-quality results and a critical craft each strictly raise the grant.
 */
object CraftXp {

    /** Share of the base added by quality: quality 1.0 => +50%. */
    const val QUALITY_WEIGHT: Double = 0.5

    /** Extra multiplier per tier above tier 1 (tier 2 => x1.5, tier 3 => x2.0, tier 4 => x2.5). */
    const val TIER_STEP: Double = 0.5

    /** A critical craft doubles the XP grant. */
    const val CRITICAL_MULTIPLIER: Double = 2.0

    /** Base XP for any outcome kind not listed in [BASE_XP] (defensive; every current kind is listed). */
    const val DEFAULT_BASE: Double = 6.0

    /** Base XP per outcome kind, before tier/quality/critical scaling. */
    val BASE_XP: Map<RecipeOutcomeKind, Double> = mapOf(
        RecipeOutcomeKind.NEW_ITEM to 10.0,
        RecipeOutcomeKind.TRANSMUTE to 8.0,
        RecipeOutcomeKind.UPGRADE_INPUT to 12.0,
        RecipeOutcomeKind.REPAIR to 4.0,
        RecipeOutcomeKind.ENCHANT to 10.0,
        RecipeOutcomeKind.REFORGE to 8.0,
        RecipeOutcomeKind.INSERT_GEM to 6.0,
        RecipeOutcomeKind.ADD_SOCKET to 8.0,
        RecipeOutcomeKind.REMOVE_GEM to 3.0,
    )

    /** Base XP for [kind] ([DEFAULT_BASE] if somehow absent). Pure. */
    fun baseXp(kind: RecipeOutcomeKind): Double = BASE_XP[kind] ?: DEFAULT_BASE

    /** Tier multiplier: `1 + TIER_STEP*(tier-1)`, with tier clamped to >= 1. Pure. */
    fun tierMultiplier(tier: Int): Double = 1.0 + TIER_STEP * (tier.coerceAtLeast(1) - 1)

    /** Quality multiplier: `1 + QUALITY_WEIGHT*quality`, with quality clamped to `[0,1]`. Pure. */
    fun qualityMultiplier(quality: Double): Double = 1.0 + QUALITY_WEIGHT * quality.coerceIn(0.0, 1.0)

    /** The XP a craft of [kind] at [tier] rolling [quality] yields (doubled when [critical]). Pure. */
    fun xpFor(kind: RecipeOutcomeKind, tier: Int, quality: Double, critical: Boolean): Double {
        val amount = baseXp(kind) * tierMultiplier(tier) * qualityMultiplier(quality)
        return if (critical) amount * CRITICAL_MULTIPLIER else amount
    }
}

/**
 * The [XpSource] a successful craft grants through [dev.willram.ramrpg.api.skills.SkillService.addXp]. Its
 * [xp] is the pure [CraftXp.xpFor] amount (the service still applies its own `multiplier`, as with every
 * XpSource). Construct one per craft via [CraftXpSource.forCraft].
 */
class CraftXpSource(
    override val skill: SkillKey,
    val kind: RecipeOutcomeKind,
    val tier: Int,
    val quality: Double,
    val critical: Boolean,
) : XpSource {
    override val key: XpSourceKey = SOURCE_KEY

    override fun xp(ctx: XpContext): Double = CraftXp.xpFor(kind, tier, quality, critical)

    companion object {
        /** One XpSourceKey for every craft grant (mirrors [dev.willram.ramrpg.core.rewards.SkillXpRewardAction]'s single key). */
        val SOURCE_KEY: XpSourceKey = XpSourceKey.of("ramrpg", "craft")

        /** The skill a recipe's XP grants to: a [RecipeOutcome.NewItem]'s `qualitySkill`, else `null`. Pure. */
        fun skillFor(recipe: Recipe): SkillKey? = (recipe.outcome as? RecipeOutcome.NewItem)?.qualitySkill

        /**
         * Builds the [CraftXpSource] for a successful craft, or `null` when no skill drives its XP (no
         * [skill] was supplied and the recipe carries none). [tier] is the crafted item's material tier
         * (default 1); [skill] overrides [skillFor] when the caller resolves the skill differently.
         */
        fun forCraft(
            recipe: Recipe,
            result: CraftResult.Success,
            tier: Int = 1,
            skill: SkillKey? = skillFor(recipe),
        ): CraftXpSource? =
            skill?.let { CraftXpSource(it, recipe.outcome.kind, tier, result.quality, result.critical) }
    }
}
