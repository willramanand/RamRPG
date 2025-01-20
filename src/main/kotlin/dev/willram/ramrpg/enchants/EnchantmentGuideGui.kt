package dev.willram.ramrpg.enchants

import dev.willram.ramcore.item.ItemStackBuilder
import dev.willram.ramcore.menu.Gui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent

class EnchantmentGuideGui(player: Player) : Gui(player, 6, "Enchant Guide") {

    private val FOOTER_SLOTS = listOf(45, 46, 47, 48, 50, 51, 52, 53)

    override fun redraw() {
        var count = 0
        for (enchant in Enchantments.all()) {
            val newLore = ArrayList<Component>()

            newLore.add(Component.text(""))

            for (s in enchant.description(enchant.maxLvl)) {
                newLore.add(MiniMessage.miniMessage().deserialize(s))
            }

            newLore.add(Component.text(""))

            newLore.add(MiniMessage.miniMessage().deserialize("<gold>Possible Levels: ${enchant.startLvl} - ${enchant.maxLvl}"))
            newLore.add(MiniMessage.miniMessage().deserialize("<light_purple>Required Bookshelf Power: ${enchant.requiredBookshelfPower()}"))
            newLore.add(MiniMessage.miniMessage().deserialize("<dark_aqua>XP Costs: ${enchant.xpCosts(enchant.startLvl,
                ItemStackBuilder.of(Material.AIR).build())} - ${enchant.xpCosts(enchant.maxLvl,
                ItemStackBuilder.of(Material.AIR).build())}</dark_aqua>"))

            this.setItem(count, ItemStackBuilder.of(Material.ENCHANTED_BOOK)
                .name(enchant.displayName(null))
                .lore(newLore)
                .build {})
            count++

            for (i in FOOTER_SLOTS) {
                this.setItem(i, ItemStackBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name("").build {})
            }
        }

        this.setItem(49, ItemStackBuilder.of(Material.BARRIER).name("<red>Close").build {
            this.player.closeInventory()
        })
    }

    override fun clickHandler(p0: InventoryClickEvent?): Boolean {
        return false
    }

    override fun closeHandler(p0: InventoryCloseEvent?) {}

    override fun invalidateHandler() {}
}