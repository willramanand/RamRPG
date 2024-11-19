package dev.willram.ramrpg.events

import dev.willram.ramrpg.enchants.CustomEnchantment
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

class CustomEnchantEvent(val enchanter: Player, val table: Block, val item: ItemStack, val enchantment: CustomEnchantment, val lvl: Int) : Event() {
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