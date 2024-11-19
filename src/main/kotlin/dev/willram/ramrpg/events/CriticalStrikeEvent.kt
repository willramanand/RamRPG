package dev.willram.ramrpg.events

import dev.willram.ramrpg.enums.CriticalType
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class CriticalStrikeEvent(val player: Player, val type: CriticalType) : Event() {
    val isCancelled: Boolean = false

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }

    companion object {
        private val HANDLERS = HandlerList()

        //I just added this.
        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }
}