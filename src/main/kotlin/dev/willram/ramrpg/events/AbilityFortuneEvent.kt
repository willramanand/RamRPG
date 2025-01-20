package dev.willram.ramrpg.events

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class AbilityFortuneEvent(val player: Player, val type: Material, val location: Location, val blocksBroken: Int) : Event() {
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