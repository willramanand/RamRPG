/**
 * WP-2.3a: fills the empty ELEMENTAL_BREAKDOWN (600) slot in the damage pipeline.
 * WP-2.3b: now CONSUMES the attacking weapon's declared
 * [dev.willram.ramrpg.api.items.ItemDefinition.damageSplit] instead of always defaulting all-physical,
 * and blends in any elemental bonus an enchant contributed via [ELEMENTAL_BONUS_METADATA_KEY].
 */
package dev.willram.ramrpg.builtin.stats

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.combat.DamageStage
import dev.willram.ramrpg.api.combat.DamageTag
import dev.willram.ramrpg.api.identity.DamageTypeKey
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.builtin.identity.BuiltinDamageTypes

/**
 * The one and only stage that seeds [DamageContext.components]. [ResistancesStage] (registered just
 * before ARMOR_MITIGATION) is the one and only stage that consumes them; neither stage re-seeds nor
 * double-counts -- see `docs/design/2.3a-damage-types.md`.
 *
 * WP-2.3a seeded every hit all-physical (all-true if [DamageTag.TRUE]) because no weapon/source split
 * mechanism existed yet. WP-2.3b fills that gap: [items] and [itemDefs] (both nullable -- see below)
 * resolve [DamageContext.weapon] down to its [dev.willram.ramrpg.api.items.ItemDefinition.damageSplit],
 * and when that split is non-empty it partitions the base damage proportionally instead of dumping it
 * all into [BuiltinDamageTypes.PHYSICAL]. An absent/unresolvable split (no weapon, unidentified stack,
 * unregistered definition, or a definition that simply never declared one) keeps the WP-2.3a all-physical
 * behavior byte-for-byte -- see [weaponSplit] and [seed].
 *
 * [items]/[itemDefs] default to `null` (rather than being required) so every WP-2.3a caller/test that
 * constructs `ElementalBreakdownStage()` with no arguments keeps compiling and keeps its original
 * all-physical/all-true behavior unchanged: with either dependency missing, [weaponSplit] can't resolve
 * anything and returns empty, which [seed] treats identically to "no declared split". The real pipeline
 * (`CombatModule`) always wires both from the [dev.willram.ramcore.service.ServiceContext].
 *
 * Damage already tagged [DamageTag.TRUE] upstream (a future true-damage ability/effect) STILL becomes a
 * single all-[BuiltinDamageTypes.TRUE] component regardless of any weapon split -- true damage bypasses
 * elemental typing entirely, exactly as it bypasses resistances ([ResistancesStage]) and armor
 * mitigation (`ArmorMitigationStage`).
 *
 * `components` is cleared at the top of [apply] so this stage can never accumulate if it were ever
 * invoked twice on the same context. In practice that never happens: each ferocity re-enqueue
 * (`FerocityStage`) builds a brand-new [DamageContext] with a fresh, empty `components` map, so the
 * required per-chain reset (the pipeline re-runs up to 8 ferocity chains) falls out of the existing
 * per-chain context scoping for free -- there is no shared mutable state across chains to reset.
 */
class ElementalBreakdownStage(
    private val items: ItemInstanceService? = null,
    private val itemDefs: ItemDefinitionRegistry? = null,
) : DamageStage {
    override val key: ContentId = ContentId.of("ramrpg", "elemental_breakdown")
    override val priority: Int = DamagePriority.ELEMENTAL_BREAKDOWN

    override fun apply(ctx: DamageContext) {
        ctx.components.clear()
        if (DamageTag.TRUE in ctx.tags) {
            ctx.components[BuiltinDamageTypes.TRUE] = ctx.finalDamage
            return
        }

        // WP-2.3b: an elemental enchant (see BuiltinEnchants) hooks ENCHANT_OFFENSE (300, before this
        // stage's clear() above) and stashes its flat bonus here, already added into ctx.finalDamage --
        // see ELEMENTAL_BONUS_METADATA_KEY's KDoc. base is the REMAINING (non-bonus) damage the weapon's
        // own split still governs; base + bonusTotal always equals finalDamage, so components still sums
        // to the total after both are folded back in below.
        @Suppress("UNCHECKED_CAST")
        val enchantBonuses = ctx.metadata[ELEMENTAL_BONUS_METADATA_KEY] as? Map<DamageTypeKey, Double> ?: emptyMap()
        val bonusTotal = enchantBonuses.values.sum()
        val base = (ctx.finalDamage - bonusTotal).coerceAtLeast(0.0)

        seed(ctx.components, weaponSplit(ctx), base)
        for ((type, amount) in enchantBonuses) {
            if (amount == 0.0) continue
            ctx.components[type] = (ctx.components[type] ?: 0.0) + amount
        }
    }

    /**
     * [DamageContext.weapon] -> [ItemInstanceService.identify] -> [ItemDefinitionRegistry.get] ->
     * [dev.willram.ramrpg.api.items.ItemDefinition.damageSplit]. Empty (never null) at every failure
     * point -- no weapon, an unidentified stack, or an unregistered definition -- so callers never need
     * a null check; [seed] already treats an empty split as "default all-physical".
     */
    private fun weaponSplit(ctx: DamageContext): Map<DamageTypeKey, Double> {
        val stack = ctx.weapon ?: return emptyMap()
        val instance = items?.identify(stack) ?: return emptyMap()
        val def = itemDefs?.get(instance.identity.key) ?: return emptyMap()
        return def.damageSplit
    }

    companion object {
        /**
         * WP-2.3b: the [DamageContext.metadata] key an elemental enchant's
         * [dev.willram.ramrpg.api.effects.DamagePipelineEffect] hook stashes its bonus elemental damage
         * under -- a `Map<DamageTypeKey, Double>` of flat bonus amounts ALREADY added into
         * [DamageContext.finalDamage] by that same hook (the same "add to finalDamage" idiom
         * `Sharpness`/`Power` use, see `BuiltinEnchants`). Enchant hooks run inside `EnchantDamageStage`
         * at pipeline priority `ENCHANT_OFFENSE` (300) -- strictly before this stage clears `components`
         * at 600 -- so a hook cannot write `components` directly without it being wiped; metadata is the
         * sanctioned cross-stage handoff [DamageContext] already exposes for exactly this. See
         * `docs/design/2.3a-damage-types.md`'s WP-2.3b append and [dev.willram.ramrpg.builtin.enchants.BuiltinEnchants].
         */
        const val ELEMENTAL_BONUS_METADATA_KEY: String = "ramrpg:elemental_bonus"

        /**
         * The pure split core: partitions [base] proportionally across [split]'s fractions into [target]
         * (additive, so it composes with whatever [target] already holds -- see the enchant-bonus loop in
         * [apply]). An empty [split] (no weapon, or a weapon with no declared split) falls back to a single
         * all-[BuiltinDamageTypes.PHYSICAL] entry of [base], matching the WP-2.3a default exactly. Never
         * touches Bukkit -- off-server testable on its own, per the WP-2.3b test plan.
         */
        fun seed(target: MutableMap<DamageTypeKey, Double>, split: Map<DamageTypeKey, Double>, base: Double) {
            if (split.isEmpty()) {
                target[BuiltinDamageTypes.PHYSICAL] = (target[BuiltinDamageTypes.PHYSICAL] ?: 0.0) + base
                return
            }
            for ((type, fraction) in split) {
                target[type] = (target[type] ?: 0.0) + base * fraction
            }
        }
    }
}
