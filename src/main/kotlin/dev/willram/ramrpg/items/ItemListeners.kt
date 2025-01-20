package dev.willram.ramrpg.items

import com.comphenix.protocol.PacketType
import dev.willram.ramcore.data.NamespacedKeys
import dev.willram.ramcore.event.Events
import dev.willram.ramcore.menu.Gui
import dev.willram.ramcore.pdc.PDCs
import dev.willram.ramcore.protocol.Protocol
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.enchants.Enchantments
import io.papermc.paper.event.player.PlayerPickItemEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType


class ItemListeners {

    companion object {
        fun register() {
            Protocol.subscribe(PacketType.Play.Server.WINDOW_ITEMS, PacketType.Play.Server.SET_SLOT)
                .handler { e ->
                    val packetType = e.packetType
                    val packet = e.packet
                    val player = e.player

                    if (packetType == PacketType.Play.Server.SET_SLOT) {
                        val modifier = packet.itemModifier
                        if (modifier.size() > 0) {
                            val itemStack = packet.itemModifier.readSafely(0)
                            val newItemStack = applyModifications(itemStack, player)
                            modifier.write(0, newItemStack)
                        }
                    } else if (packetType == PacketType.Play.Server.WINDOW_ITEMS) {
                        val structMode = packet.itemListModifier
                        if (structMode.size() > 0) {
                            var itemStacks = packet.itemListModifier.readSafely(0)
                            itemStacks = itemStacks.stream().map { applyModifications(it, player) }.toList()
                            structMode.writeSafely(0, itemStacks);
                        }
                    }

                    e.packet = packet
                }

            Events.subscribe(PlayerItemDamageEvent::class.java)
                .handler { e ->
                    e.isCancelled = true
                }
        }

        private fun applyModifications(item: ItemStack?, player: Player): ItemStack? {
            if (item == null) return item
            if (item.hasItemMeta() && PDCs.has(item.itemMeta, Gui.GUI_ITEM_KEY)) return item
            val itemInfo = Items.retrieve(item) ?: return item
            val newItem = item.clone()
            val meta = newItem.itemMeta

            val newLore: MutableList<Component> = ArrayList()

            for (stat in itemInfo.stats.keys) {
                val actualStat = RamRPG.get().stats[stat]
                newLore.add(MiniMessage.miniMessage().deserialize("<gray>${actualStat.displayName}: ${actualStat.prefix}${ if (itemInfo.stats[stat]!! > 0.0)  "+" else ""}${itemInfo.stats[stat]} ${actualStat.symbol}")
                    .decoration(TextDecoration.ITALIC, false))
            }

            if (PDCs.has(meta, "ramrpg-lore")) {
                val loreStrings = PDCs.get(meta, "ramrpg-lore", PersistentDataType.LIST.strings())
                if (loreStrings != null) {
                    newLore.add(Component.text(""))
                    for (lore in loreStrings) {
                        newLore.add(MiniMessage.miniMessage().deserialize(lore).decoration(TextDecoration.ITALIC, false))
                    }
                }
            }

            newLore.add(Component.text(""))
            //meta.removeEnchantments()
            val itemEnchants = Enchantments.getEnchants(item)
            for (enchantEntry in itemEnchants) {
                newLore.add(MiniMessage.miniMessage().deserialize("<blue>${enchantEntry.key.displayName(enchantEntry.value)}").decoration(TextDecoration.ITALIC, false))
                for (line in enchantEntry.key.description(enchantEntry.value)) {
                    newLore.add(MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false))
                }
            }

            if (itemEnchants.isNotEmpty()) {
                meta.setEnchantmentGlintOverride(true)
            } else {
                meta.setEnchantmentGlintOverride(false)
            }

            newLore.add(Component.text(""))

            newLore.add(MiniMessage.miniMessage().deserialize("<b>${itemInfo.rarity.color}${itemInfo.rarity.displayName}")
                .decoration(TextDecoration.ITALIC, false))

            if (meta.displayName() == null) {
                meta.displayName(
                    MiniMessage.miniMessage().deserialize("${itemInfo.rarity.color}${itemInfo.displayName}")
                        .decoration(TextDecoration.ITALIC, false)
                )
            }

            if (!meta.hasAttributeModifiers()) {
                meta.addAttributeModifier(
                    Attribute.ATTACK_DAMAGE,
                    AttributeModifier(NamespacedKeys.create("hide_attributes_modifier"), 0.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
                )
            }

            meta.itemFlags.addAll(ItemFlag.entries.toTypedArray())
            meta.lore(newLore)
            newItem.itemMeta = meta
            return newItem
        }
    }
}