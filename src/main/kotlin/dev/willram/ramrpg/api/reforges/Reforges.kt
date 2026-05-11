/**
 * Reforges grant per-category stat bonuses on top of an item's base stats.
 * Persisted on the stack as [ItemInstanceData.reforge] and consumed by
 * `ReforgeStatProvider` at stat aggregation time.
 */
package dev.willram.ramrpg.api.reforges

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import net.kyori.adventure.text.Component

import dev.willram.ramrpg.api.items.ReforgeKey

object ReforgeIds { fun of(ns: String, v: String) = ReforgeKey(ContentId.of(ns, v)) }

data class ReforgeDefinition(
    val key: ReforgeKey,
    val displayName: Component,
    /** Per-category stat bonuses. Match by any category in ItemDefinition.categories. */
    val bonusesByCategory: Map<ItemCategory, Map<StatKey, Double>> = emptyMap(),
    val universal: Map<StatKey, Double> = emptyMap(),
)

interface ReforgeRegistry {
    fun register(owner: String, def: ReforgeDefinition)
    fun unregisterOwner(owner: String): Int
    fun get(key: ReforgeKey): ReforgeDefinition?
    fun all(): Collection<ReforgeDefinition>
}
