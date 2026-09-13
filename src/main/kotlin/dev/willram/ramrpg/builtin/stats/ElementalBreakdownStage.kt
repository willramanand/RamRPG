/** WP-2.3a: fills the empty ELEMENTAL_BREAKDOWN (600) slot in the damage pipeline. */
package dev.willram.ramrpg.builtin.stats

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.combat.DamageStage
import dev.willram.ramrpg.api.combat.DamageTag
import dev.willram.ramrpg.builtin.identity.BuiltinDamageTypes

/**
 * The one and only stage that seeds [DamageContext.components]. [ResistancesStage] (registered just
 * before ARMOR_MITIGATION) is the one and only stage that consumes them; neither stage re-seeds nor
 * double-counts -- see `docs/design/2.3a-damage-types.md`.
 *
 * Weapon/source elemental splits are WP-2.3b content and don't exist yet, so every hit defaults to a
 * single all-[BuiltinDamageTypes.PHYSICAL] component (per the WP-2.3a spec: "seeds components from the
 * weapon/source split, default all-physical if none"). The one exception is damage already tagged
 * [DamageTag.TRUE] upstream (a future true-damage ability/effect), which becomes a single
 * all-[BuiltinDamageTypes.TRUE] component instead: [ResistancesStage] exempts that component from every
 * resistance lookup, and the pre-existing `ArmorMitigationStage` already skips flat armor mitigation
 * whenever [DamageTag.TRUE] is present, so true damage bypasses both defenses for free.
 *
 * `components` is cleared at the top of [apply] so this stage can never accumulate if it were ever
 * invoked twice on the same context. In practice that never happens: each ferocity re-enqueue
 * (`FerocityStage`) builds a brand-new [DamageContext] with a fresh, empty `components` map, so the
 * required per-chain reset (the pipeline re-runs up to 8 ferocity chains) falls out of the existing
 * per-chain context scoping for free -- there is no shared mutable state across chains to reset.
 */
class ElementalBreakdownStage : DamageStage {
    override val key: ContentId = ContentId.of("ramrpg", "elemental_breakdown")
    override val priority: Int = DamagePriority.ELEMENTAL_BREAKDOWN

    override fun apply(ctx: DamageContext) {
        ctx.components.clear()
        val type = if (DamageTag.TRUE in ctx.tags) BuiltinDamageTypes.TRUE else BuiltinDamageTypes.PHYSICAL
        ctx.components[type] = ctx.finalDamage
    }
}
