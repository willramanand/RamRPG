/**
 * WP-2.2: pure durability arithmetic on [ItemInstanceData]. See `docs/design/2.2-durability.md` for the
 * drain rates and the never-breaks model this backs.
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramrpg.api.items.ItemInstanceData

/**
 * `damage`/`repair` are the only two operations this service has -- both pure functions of
 * [ItemInstanceData], clamping the result to `[0, maxDurability]` and returning the updated data. Neither
 * mutates an `ItemStack`; the caller (the durability-drain [dev.willram.ramrpg.api.combat.DamageStage] in
 * `builtin/stats/DamageStages.kt`, or a future repair station) writes the result back via
 * `ItemInstanceService.write`.
 *
 * **This service never decides inert-ness.** WP-2.1c's `ItemDefinition.inertReason`/`isInert`
 * (`InertReason.ZERO_DURABILITY`, `api/items/Items.kt`) already treats `durability <= 0` as inert -- this
 * service's entire job is making that number reach `0` in the first place (and, later, letting a repair
 * recipe raise it back up). It must NOT re-derive or duplicate that check.
 */
class DurabilityService {

    /**
     * Reduces [data]'s durability by [amount], clamped to `[0, maxDurability]` -- never negative, so the
     * instance is never "destroyed", only depleted (WP-2.1c's ZERO_DURABILITY path takes over from there).
     * A non-positive [amount] is a no-op (returns [data] unchanged, same reference).
     */
    fun damage(data: ItemInstanceData, amount: Int): ItemInstanceData {
        if (amount <= 0) return data
        val newDurability = (data.durability - amount).coerceIn(0, data.maxDurability)
        if (newDurability == data.durability) return data
        return data.copy(durability = newDurability)
    }

    /**
     * Restores [data]'s durability by [amount], clamped to `[0, maxDurability]`. Pure and symmetric with
     * [damage]; nothing calls this yet in WP-2.2 -- WP-3.3b wires a smithing-station repair recipe onto it.
     * Repair only ever changes [ItemInstanceData.durability]: quality, upgradeLevel, sockets and
     * enchantments are untouched (see `docs/design/0.1-durability.md`).
     */
    fun repair(data: ItemInstanceData, amount: Int): ItemInstanceData {
        if (amount <= 0) return data
        val newDurability = (data.durability + amount).coerceIn(0, data.maxDurability)
        if (newDurability == data.durability) return data
        return data.copy(durability = newDurability)
    }

    companion object {
        /** Weapon durability lost per landed hit (attacker's held weapon), via the APPLY-stage drain. */
        const val DRAIN_PER_HIT: Int = 1

        /** Each worn armor piece's durability lost per hit the wearer takes, via the same APPLY-stage drain. */
        const val DRAIN_PER_ARMOR_HIT: Int = 1

        /**
         * Documented rate for tool durability lost per block broken -- NOT wired in WP-2.2. There is no
         * `BlockBreakEvent` listener in this WP's file scope (`DurabilityListener.kt`, `DamageStages.kt`,
         * this file), so mining/tool-use drain is left for a future WP (or the orchestrator) to call
         * [damage] with this rate from a new listener. See `docs/design/2.2-durability.md`.
         */
        const val DRAIN_PER_BLOCK_BREAK: Int = 1

        /** Durability at/below this fraction of `maxDurability` triggers the low-durability warning message. */
        const val WARNING_THRESHOLD_FRACTION: Double = 0.10
    }
}
