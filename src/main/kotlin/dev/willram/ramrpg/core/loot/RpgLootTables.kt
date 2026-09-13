/**
 * Builder for RamCore [LootTable]s from RPG drop specs. Independent-chance drops (each item rolls its
 * own chance, any number can drop) map to one single-entry pool per item (rolls = 1, gated by
 * LootConditions.chance). Weighted-pool drops map to one multi-entry pool with the given roll count.
 * Every entry produces an [RpgItemPayload] reward seeded by [RpgLootFunctions.SEED].
 */
package dev.willram.ramrpg.core.loot

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.loot.LootConditions
import dev.willram.ramcore.loot.LootPool
import dev.willram.ramcore.loot.LootPoolEntry
import dev.willram.ramcore.loot.LootTable
import dev.willram.ramrpg.api.identity.ItemKey

object RpgLootTables {

    /** One weighted entry in a pool. */
    data class PoolItem(val item: ItemKey, val weight: Double, val chance: Double = 1.0, val count: Int = 1)

    fun builder(id: ContentId): Builder = Builder(id)

    class Builder(private val id: ContentId) {
        private val pools = ArrayList<LootPool>()

        /** An item that rolls its own [chance] independently of every other drop. */
        fun independent(item: ItemKey, chance: Double, count: Int = 1): Builder {
            val entry = LootPoolEntry.builder("${item.id.value()}", RpgLootFunctions.rpgItemReward(item, count))
                .when_(chance)
                .apply(RpgLootFunctions.SEED)
                .build()
            pools += LootPool.builder("ind_${pools.size}_${item.id.value()}").rolls(1).entry(entry).build()
            return this
        }

        /** A weighted pool that yields [rolls] picks across [entries]. */
        fun weightedPool(rolls: Int, entries: List<PoolItem>): Builder {
            if (entries.isEmpty() || rolls <= 0) return this
            val pool = LootPool.builder("weighted_${pools.size}").rolls(rolls)
            for (e in entries) {
                pool.entry(
                    LootPoolEntry.builder("${e.item.id.value()}", RpgLootFunctions.rpgItemReward(e.item, e.count))
                        .weight(e.weight)
                        .when_(e.chance)
                        .apply(RpgLootFunctions.SEED)
                        .build()
                )
            }
            pools += pool.build()
            return this
        }

        fun isEmpty(): Boolean = pools.isEmpty()

        fun build(): LootTable {
            val t = LootTable.builder(id)
            for (p in pools) t.pool(p)
            return t.build()
        }
    }
}

/** `chance = 1.0` needs no condition; anything less gets a LootConditions.chance gate. */
private fun LootPoolEntry.Builder.when_(chance: Double): LootPoolEntry.Builder =
    if (chance >= 1.0) this else this.`when`(LootConditions.chance(chance))
