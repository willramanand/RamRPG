/**
 * StatService implementation. Caches per-player snapshots; recomputes when
 * any provider marks the player dirty. Aggregation order: Σ ADD →
 * × (1 + Σ MULTIPLY_BASE) → × Π (1 + MULTIPLY_TOTAL), then clamp.
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.stats.*
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class StatServiceImpl : StatService {
    private val definitions = ConcurrentHashMap<StatKey, StatDefinition>()
    private val providers = mutableListOf<OwnedProvider>()
    private val cache = ConcurrentHashMap<UUID, StatSnapshot>()
    private val dirty = ConcurrentHashMap.newKeySet<UUID>()

    private data class OwnedProvider(val owner: String, val provider: StatProvider)

    override fun registerDefinition(def: StatDefinition) { definitions[def.key] = def }
    override fun definitions(): Collection<StatDefinition> = definitions.values
    override fun definition(key: StatKey): StatDefinition? = definitions[key]

    @Synchronized
    override fun registerProvider(provider: StatProvider, owner: String) {
        providers += OwnedProvider(owner, provider)
        cache.clear()
        dirty.clear()
    }

    @Synchronized
    override fun unregisterProviders(owner: String) {
        providers.removeAll { it.owner == owner }
        cache.clear()
    }

    override fun markDirty(player: Player, reason: StatDirtyReason) {
        dirty.add(player.uniqueId)
    }

    override fun snapshot(player: Player): StatSnapshot {
        val id = player.uniqueId
        val cached = cache[id]
        if (cached != null && !dirty.contains(id)) return cached
        return recalculateNow(player)
    }

    override fun recalculateNow(player: Player): StatSnapshot {
        val ctx = object : StatContext { override val player: Player = player }
        val mods = ArrayList<StatModifier>(64)
        val snapshotProviders = synchronized(this) { providers.toList() }
        for (op in snapshotProviders) op.provider.provideStats(ctx, mods)
        val snap = aggregate(mods)
        cache[player.uniqueId] = snap
        dirty.remove(player.uniqueId)
        return snap
    }

    private fun aggregate(mods: List<StatModifier>): StatSnapshot {
        val keys = mods.map { it.stat }.toSet() + definitions.keys
        val out = HashMap<StatKey, Double>(keys.size)
        for (k in keys) {
            val def = definitions[k]
            val base = def?.defaultBase ?: 0.0
            var add = base
            var multBase = 0.0
            var multTotal = 1.0
            for (m in mods) {
                if (m.stat != k) continue
                when (m.operation) {
                    ModifierOperation.ADD -> add += m.amount
                    ModifierOperation.MULTIPLY_BASE -> multBase += m.amount
                    ModifierOperation.MULTIPLY_TOTAL -> multTotal *= (1.0 + m.amount)
                }
            }
            var v = add * (1.0 + multBase) * multTotal
            if (def != null) {
                if (def.min != null) v = maxOf(v, def.min)
                if (def.max != null) v = minOf(v, def.max)
            }
            out[k] = v
        }
        return StatSnapshot(out)
    }
}
