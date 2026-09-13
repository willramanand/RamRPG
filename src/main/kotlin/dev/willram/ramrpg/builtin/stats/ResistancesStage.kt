/** WP-2.3a: the resistance stage, registered just before ARMOR_MITIGATION (1100). */
package dev.willram.ramrpg.builtin.stats

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.combat.DamageStage
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.builtin.identity.BuiltinDamageTypes
import org.bukkit.entity.Player

/**
 * Consumes the components [ElementalBreakdownStage] seeded at ELEMENTAL_BREAKDOWN (600). Runs at
 * [DamagePriority.RESISTANCES] (1000), just before ARMOR_MITIGATION (1100), so the flat armor formula
 * still applies to the post-resistance total unchanged.
 *
 * Reduces each typed component by that type's own `resistance_<type>` stat -- never the total, and
 * never another type's component -- using the same `def/(def+100)` diminishing-returns curve as
 * `ArmorMitigationStage` and `TrueDefenseStage`, so resistance "feels" consistent with the existing
 * armor/true-defense stats.
 *
 * [BuiltinDamageTypes.TRUE] is exempt by construction: the loop below skips it outright, so true
 * damage ignores every resistance regardless of what a `resistance_true` stat might hold.
 *
 * Only [Player] victims have resistance stats today (mobs have no per-type resistance data source --
 * that is WP-2.3b/content scope); a non-player victim's components pass through unreduced, mirroring
 * how `TrueDefenseStage` is Player-only for the analogous true-defense stat.
 */
class ResistancesStage(private val stats: StatService) : DamageStage {
    override val key: ContentId = ContentId.of("ramrpg", "resistances")
    override val priority: Int = DamagePriority.RESISTANCES

    override fun apply(ctx: DamageContext) {
        if (ctx.components.isEmpty()) return
        val snapshot = (ctx.victim as? Player)?.let { stats.snapshot(it) }
        if (snapshot != null) {
            for (type in ctx.components.keys.toList()) {
                if (type == BuiltinDamageTypes.TRUE) continue
                val amount = ctx.components.getValue(type)
                if (amount <= 0.0) continue
                val resistance = snapshot.get(BuiltinDamageTypes.resistanceStat(type))
                if (resistance <= 0.0) continue
                val factor = 1.0 - (resistance / (resistance + 100.0))
                ctx.components[type] = amount * factor
            }
        }
        ctx.finalDamage = ctx.components.values.sum()
    }
}
