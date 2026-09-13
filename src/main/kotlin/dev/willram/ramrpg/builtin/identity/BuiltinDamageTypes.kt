/**
 * WP-2.3a: builtin [DamageTypeKey] constants under namespace `ramrpg`, plus the naming convention
 * that derives each type's `resistance_<type>` [StatKey] (registered in
 * [dev.willram.ramrpg.builtin.stats.BuiltinStats]) and consumed by
 * [dev.willram.ramrpg.builtin.stats.ResistancesStage].
 */
package dev.willram.ramrpg.builtin.identity

import dev.willram.ramrpg.api.identity.DamageTypeKey
import dev.willram.ramrpg.api.identity.StatKey
import net.kyori.adventure.text.Component

object BuiltinDamageTypes {
    val PHYSICAL = DamageTypeKey.of("ramrpg", "physical")
    val FIRE = DamageTypeKey.of("ramrpg", "fire")
    val FROST = DamageTypeKey.of("ramrpg", "frost")
    val LIGHTNING = DamageTypeKey.of("ramrpg", "lightning")
    val ARCANE = DamageTypeKey.of("ramrpg", "arcane")
    val POISON = DamageTypeKey.of("ramrpg", "poison")

    /** Bypasses [dev.willram.ramrpg.builtin.stats.ResistancesStage] entirely -- see [resistanceStat]. */
    val TRUE = DamageTypeKey.of("ramrpg", "true")

    /** All builtin damage types, physical first, true last. */
    val ALL: List<DamageTypeKey> = listOf(PHYSICAL, FIRE, FROST, LIGHTNING, ARCANE, POISON, TRUE)

    /**
     * The `resistance_<type>` [StatKey] that mitigates [type]. Registered as a [dev.willram.ramrpg.api.stats.StatDefinition]
     * for every type -- including [TRUE] -- so the stats GUI and lang file stay complete, but
     * [dev.willram.ramrpg.builtin.stats.ResistancesStage] deliberately never looks this stat up for
     * [TRUE]: true damage ignores resistances entirely regardless of what `resistance_true` holds.
     */
    fun resistanceStat(type: DamageTypeKey): StatKey = StatKey.of("ramrpg", "resistance_${type.id.value()}")

    /** Player-facing display name, translated via `ramrpg.damage_type.<type>`. */
    fun displayName(type: DamageTypeKey): Component = when (type) {
        PHYSICAL -> Component.translatable("ramrpg.damage_type.physical", Component.text("Physical"))
        FIRE -> Component.translatable("ramrpg.damage_type.fire", Component.text("Fire"))
        FROST -> Component.translatable("ramrpg.damage_type.frost", Component.text("Frost"))
        LIGHTNING -> Component.translatable("ramrpg.damage_type.lightning", Component.text("Lightning"))
        ARCANE -> Component.translatable("ramrpg.damage_type.arcane", Component.text("Arcane"))
        POISON -> Component.translatable("ramrpg.damage_type.poison", Component.text("Poison"))
        TRUE -> Component.translatable("ramrpg.damage_type.true", Component.text("True"))
        else -> Component.text(type.id.value())
    }
}
