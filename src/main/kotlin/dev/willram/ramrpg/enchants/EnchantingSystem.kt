package dev.willram.ramrpg.enchants

import dev.willram.ramcore.event.Events
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.inventory.PrepareGrindstoneEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.inventory.MerchantRecipe

class EnchantingSystem {

    companion object {
        fun register() {
            Events.subscribe(InventoryOpenEvent::class.java)
                .filter { e -> e.inventory.type == InventoryType.ENCHANTING }
                .handler { e ->
                    e.isCancelled = true

                    val enchantingGui = EnchantingGui(e.player as Player, e.inventory.location?.block!!)
                    enchantingGui.open()
                }

            Events.subscribe(PrepareGrindstoneEvent::class.java)
                .filter { e -> e.result != null }
                .handler { e ->
                    val result = e.result!!
                    Enchantments.removeAllEnchants(result)
                    e.result = result
                }

            Events.subscribe(PrepareGrindstoneEvent::class.java)
                .filter { e -> e.result == null }
                .filter { e -> e.inventory.lowerItem != null || e.inventory.upperItem != null }
                .handler { e ->
                    val item = e.inventory.lowerItem ?: e.inventory.upperItem ?: return@handler
                    val result = item.clone()
                    Enchantments.removeAllEnchants(result)
                    e.result = result
                }

            // Stop the use of enchanted books for now
            Events.subscribe(PrepareAnvilEvent::class.java)
                .filter { e -> e.result != null }
                .filter { e -> e.inventory.firstItem != null && e.inventory.secondItem != null }
                .filter { e -> e.inventory.firstItem?.type == Material.ENCHANTED_BOOK || e.inventory.secondItem?.type == Material.ENCHANTED_BOOK }
                .handler { e ->
                    e.result = null
                }

            // Remove minecraft enchants from chests
            Events.subscribe(InventoryOpenEvent::class.java)
                .filter { e -> e.inventory.type == InventoryType.CHEST }
                .handler { e ->
                    val contents = e.inventory.contents
                    for (item in contents) {
                        if (item == null || item.enchantments.isEmpty()) continue
                        Enchantments.convertEnchants(item)
                    }
                }

            // Remove minecraft enchants from pickups
            Events.subscribe(EntityPickupItemEvent::class.java)
                .filter { e -> e.entity.type == EntityType.PLAYER }
                .handler { e ->
                    val item = e.item.itemStack.clone()
                    if (item.enchantments.isEmpty()) return@handler
                    Enchantments.convertEnchants(item)
                    e.item.itemStack = item
                }

            // Remove minecraft enchants from trades
            // TODO: Add custom enchants in place
            Events.subscribe(PlayerInteractAtEntityEvent::class.java)
                .filter { e -> e.rightClicked is Villager }
                .handler { e ->
                    val villager = e.rightClicked as Villager
                    if (villager.recipes.isEmpty()) return@handler
                    val trades = villager.recipes
                    val newTrades = ArrayList<MerchantRecipe>()
                    for (recipe in trades) {
                        if (recipe.result.type == Material.ENCHANTED_BOOK) continue
                        newTrades.add(recipe)
                    }

                    villager.recipes = newTrades
                }
        }
    }
}