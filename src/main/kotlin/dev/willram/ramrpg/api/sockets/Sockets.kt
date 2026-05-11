/**
 * Socket + gem system. Sockets are listed on [ItemInstanceData.sockets]
 * and may hold a [GemKey]. `SocketStatProvider` emits stat modifiers from
 * each filled gem. Slot capacity is bounded by [RarityRules.socketCap].
 */
package dev.willram.ramrpg.api.sockets

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.StatKey
import net.kyori.adventure.text.Component

@JvmInline value class GemKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = GemKey(ContentId.of(ns, v)) }
}

data class GemDefinition(
    val key: GemKey,
    val displayName: Component,
    val statContribution: Map<StatKey, Double>,
)

interface GemRegistry {
    fun register(owner: String, def: GemDefinition)
    fun unregisterOwner(owner: String): Int
    fun get(key: GemKey): GemDefinition?
    fun all(): Collection<GemDefinition>
}
