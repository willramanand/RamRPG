/** Marks stats dirty + applies attribute baseValues on equipment / world / join events. */
package dev.willram.ramrpg.core.listeners

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import dev.willram.ramcore.event.Events
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramrpg.api.stats.StatDirtyReason
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.builtin.identity.RamStats
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent

class EquipmentListener(private val stats: StatService) {

    fun register() {
        Events.subscribe(PlayerJoinEvent::class.java, EventPriority.HIGH).handler { e ->
            stats.markDirty(e.player, StatDirtyReason.JOIN)
            Schedulers.run(e.player) { applyAttributes(e.player, setHealth = true) }
        }
        Events.subscribe(PlayerArmorChangeEvent::class.java).handler { e ->
            stats.markDirty(e.player, StatDirtyReason.EQUIPMENT_CHANGED)
            Schedulers.run(e.player) { applyAttributes(e.player) }
        }
        Events.subscribe(PlayerSwapHandItemsEvent::class.java).handler { e ->
            stats.markDirty(e.player, StatDirtyReason.EQUIPMENT_CHANGED)
            Schedulers.run(e.player) { applyAttributes(e.player) }
        }
        Events.subscribe(PlayerItemBreakEvent::class.java).handler { e ->
            stats.markDirty(e.player, StatDirtyReason.EQUIPMENT_CHANGED)
            Schedulers.run(e.player) { applyAttributes(e.player) }
        }
        Events.subscribe(PlayerChangedWorldEvent::class.java).handler { e ->
            if (e.player.gameMode.isInvulnerable) return@handler
            stats.markDirty(e.player, StatDirtyReason.WORLD_CHANGED)
            Schedulers.run(e.player) { applyAttributes(e.player) }
        }
        Events.subscribe(PlayerItemHeldEvent::class.java).handler { e ->
            stats.markDirty(e.player, StatDirtyReason.EQUIPMENT_CHANGED)
            Schedulers.run(e.player) { applyAttributes(e.player) }
        }
        Events.subscribe(InventoryClickEvent::class.java).handler { e ->
            val p = e.whoClicked as? org.bukkit.entity.Player ?: return@handler
            stats.markDirty(p, StatDirtyReason.EQUIPMENT_CHANGED)
            Schedulers.run(p) { applyAttributes(p) }
        }
    }

    fun applyAttributes(player: Player, setHealth: Boolean = false) {
        val snap = stats.recalculateNow(player)
        val health = snap[RamStats.HEALTH].coerceAtLeast(1.0)
        val swing = snap[RamStats.ATTACK_SPEED]
        val speed = snap[RamStats.SPEED]

        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = health
        player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = 4.0 + swing
        player.walkSpeed = if (speed > 0.0) {
            (0.2f + (0.8f * (speed.toFloat() / (100f + speed.toFloat()))))
        } else 0.2f
        if (setHealth) player.health = health
        player.healthScale = 20.0
    }
}
