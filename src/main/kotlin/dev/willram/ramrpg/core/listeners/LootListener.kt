/** Rolls EntityProfile.loot on death and drops items. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.entities.LootEntry
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceInit
import dev.willram.ramrpg.api.items.ItemInstanceService
import org.bukkit.event.entity.EntityDeathEvent
import kotlin.random.Random

class LootListener(
    private val profiles: EntityProfileRegistry,
    private val items: ItemInstanceService,
    private val defs: ItemDefinitionRegistry,
) {
    fun register() {
        Events.subscribe(EntityDeathEvent::class.java).handler { e ->
            val profile = profiles.resolve(e.entity) ?: return@handler
            if (profile.loot.isEmpty()) return@handler
            val drops = roll(profile.loot) + rollPool(profile.lootPool, profile.lootRolls)
            if (drops.isEmpty()) return@handler
            val loc = e.entity.location
            Schedulers.run(loc) {
                for ((key, count) in drops) {
                    val def = defs.get(key) ?: continue
                    val stack = items.create(def, ItemInstanceInit())
                    stack.amount = count.coerceIn(1, 64)
                    loc.world.dropItemNaturally(loc, stack)
                }
            }
        }
    }

    private fun roll(loot: List<LootEntry>): List<Pair<dev.willram.ramrpg.api.identity.ItemKey, Int>> {
        val rolled = ArrayList<Pair<dev.willram.ramrpg.api.identity.ItemKey, Int>>()
        for (entry in loot) {
            if (Random.nextDouble() >= entry.chance) continue
            val count = if (entry.minCount >= entry.maxCount) entry.minCount
            else Random.nextInt(entry.minCount, entry.maxCount + 1)
            rolled += entry.item to count
        }
        return rolled
    }

    private fun rollPool(pool: List<LootEntry>, rolls: Int): List<Pair<dev.willram.ramrpg.api.identity.ItemKey, Int>> {
        if (pool.isEmpty() || rolls <= 0) return emptyList()
        val totalWeight = pool.sumOf { it.weight }
        if (totalWeight <= 0.0) return emptyList()
        val out = ArrayList<Pair<dev.willram.ramrpg.api.identity.ItemKey, Int>>(rolls)
        repeat(rolls) {
            var roll = Random.nextDouble() * totalWeight
            for (entry in pool) {
                roll -= entry.weight
                if (roll <= 0.0) {
                    if (Random.nextDouble() < entry.chance) {
                        val count = if (entry.minCount >= entry.maxCount) entry.minCount
                        else Random.nextInt(entry.minCount, entry.maxCount + 1)
                        out += entry.item to count
                    }
                    break
                }
            }
        }
        return out
    }
}
