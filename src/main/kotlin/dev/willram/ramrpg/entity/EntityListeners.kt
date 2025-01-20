package dev.willram.ramrpg.entity

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.EntityType
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.CreatureSpawnEvent

class EntityListeners {

    companion object {
        fun register() {
            Events.subscribe(CreatureSpawnEvent::class.java, EventPriority.MONITOR)
                .filter { e -> e.entityType != EntityType.ARMOR_STAND }
                .handler { e ->
                    if (RamRPG.get().mythicMobsEnabled && MythicBukkit.inst().mobManager.isMythicMob(e.entity)) return@handler
                    val type = e.entityType.toString()
                    val entityType = EntityStats.valueOf(type)
                    val healthValue = entityType.health
                    e.entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = healthValue
                    e.entity.health = healthValue
                }
        }
    }
}