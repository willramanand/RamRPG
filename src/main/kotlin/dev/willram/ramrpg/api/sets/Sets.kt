/**
 * WP-5.3: armor set bonuses -- the "everything is an Effect" canary. A [SetDefinition] names its
 * member [ItemKey]s and a map of member-count threshold -> the [Effect]s granted once at least that
 * many of the wearer's equipped items are (non-inert) members. Thresholds STACK: reaching a higher
 * threshold keeps every lower threshold's effects active too (see docs/design/5.3-armor-sets.md).
 *
 * Threshold bonuses are expressed as ordinary [Effect]s -- consumed by
 * [dev.willram.ramrpg.core.services.SetStatProvider] (a
 * [dev.willram.ramrpg.api.stats.StatProvider], `core/services/SetStatProvider.kt`) for the
 * [dev.willram.ramrpg.api.effects.StatEffect] entries within them, exactly like every other item-based
 * bonus (equipment/enchant/reforge/socket) already flows through [dev.willram.ramrpg.api.stats.StatService].
 * No bespoke listener hook exists or is needed: set-count recompute rides the SAME equipment-dirty
 * trigger every other item-based provider already rides (see `core/listeners/EquipmentListener.kt`).
 */
package dev.willram.ramrpg.api.sets

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.effects.Effect
import dev.willram.ramrpg.api.identity.ItemKey
import net.kyori.adventure.text.Component

@JvmInline value class SetKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = SetKey(ContentId.of(ns, v)) }
}

data class SetDefinition(
    val key: SetKey,
    val displayName: Component,
    val members: Set<ItemKey>,
    val thresholds: Map<Int, List<Effect>>,
)

interface SetRegistry {
    fun register(owner: String, def: SetDefinition)
    fun unregisterOwner(owner: String): Int
    fun get(key: SetKey): SetDefinition?
    fun all(): Collection<SetDefinition>
}
