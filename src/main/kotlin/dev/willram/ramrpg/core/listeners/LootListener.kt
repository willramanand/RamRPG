/** Generates EntityProfile.lootTable on death (RamCore LootGenerator) and drops the rolled items. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.loot.LootGenerator
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.core.loot.BossLootService
import dev.willram.ramrpg.core.loot.RpgItemPayload
import dev.willram.ramrpg.core.loot.RpgLootContexts
import org.bukkit.event.entity.EntityDeathEvent
import java.util.Random

class LootListener(
    private val profiles: EntityProfileRegistry,
    private val items: ItemInstanceService,
    private val defs: ItemDefinitionRegistry,
    private val bossLoot: BossLootService,
) {
    private val generator = LootGenerator()
    private val random = Random()

    fun register(consumer: TerminableConsumer) {
        consumer.bind(Events.subscribe(EntityDeathEvent::class.java).handler { e ->
            val profile = profiles.resolve(e.entity) ?: return@handler
            val table = profile.lootTable ?: return@handler

            // WP-1.1b: additionally register an instanced, per-player-claimable copy for boss kills.
            // This does NOT replace the ground drop below -- no claim UI exists yet (WP-6.4), so
            // removing the ground drop would make warden/wither/ender_dragon/elder_guardian kills
            // (already isBoss=true in BuiltinEntities) award nothing today. An unclaimed instance
            // simply expires via LootModule's sweep.
            if (profile.isBoss) {
                val contributors = listOfNotNull(e.entity.killer?.uniqueId)
                if (contributors.isNotEmpty()) bossLoot.onBossDeath(e.entity, contributors)
            }

            val context = RpgLootContexts.forKill(e.entity, e.entity.killer)
            val result = generator.generate(table, context, random)
            // Each RPG reward payload carries a resolved rollSeed; nothing rolled means no drop.
            val drops = result.rewards().mapNotNull { r ->
                (r.payload() as? RpgItemPayload)?.let { it to r.amount() }
            }
            if (drops.isEmpty()) return@handler
            val loc = e.entity.location
            Schedulers.run(loc) {
                for ((payload, amount) in drops) {
                    val def = defs.get(payload.item) ?: continue
                    val stack = items.create(def, payload.init)
                    stack.amount = amount.coerceIn(1, 64)
                    loc.world.dropItemNaturally(loc, stack)
                }
            }
        })
    }
}
