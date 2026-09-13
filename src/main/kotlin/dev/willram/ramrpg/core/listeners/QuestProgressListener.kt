/** Wires Bukkit events into QuestService progress updates. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.core.services.QuestService
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDeathEvent

class QuestProgressListener(
    private val quests: QuestService,
    private val profiles: EntityProfileRegistry,
) {
    fun register(consumer: TerminableConsumer) {
        consumer.bind(Events.subscribe(org.bukkit.event.player.PlayerJoinEvent::class.java).handler { e ->
            quests.rolloverDailies(e.player)
        })
        consumer.bind(Events.subscribe(EntityDeathEvent::class.java).handler { e ->
            val killer = e.entity.killer ?: return@handler
            val profile = profiles.resolve(e.entity) ?: return@handler
            quests.onEntityKill(killer, profile.key.id.value())
        })
        consumer.bind(Events.subscribe(BlockBreakEvent::class.java).handler { e ->
            quests.onBlockBreak(e.player, e.block.type)
        })
        // periodic rollover for long-online players (every 30 minutes); Task is a Terminable, so
        // binding it cancels the timer on disable instead of leaking it across a reload.
        consumer.bind(dev.willram.ramcore.scheduler.Schedulers.forGlobal().runRepeating({ _: dev.willram.ramcore.scheduler.Task ->
            for (p in org.bukkit.Bukkit.getOnlinePlayers()) quests.rolloverDailies(p)
        }, 36000L, 36000L))
    }
}
