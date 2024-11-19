package dev.willram.ramrpg.entity

import dev.willram.ramcore.event.Events
import org.bukkit.attribute.Attribute
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.CreatureSpawnEvent

class EntityListeners {

    companion object {
        fun register() {
            Events.subscribe(CreatureSpawnEvent::class.java, EventPriority.MONITOR)
                .handler { e ->
                    val type = e.entityType.toString()
                    val entityType = EntityStats.valueOf(type)
                    val healthValue = entityType.health
                    e.entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = healthValue
                    e.entity.health = healthValue
                }
        }
    }
}