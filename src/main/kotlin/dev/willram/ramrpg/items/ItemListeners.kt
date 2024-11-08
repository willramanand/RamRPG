package dev.willram.ramrpg.items

import com.comphenix.protocol.PacketType
import dev.willram.ramcore.data.NamespacedKeys
import dev.willram.ramcore.event.Events
import dev.willram.ramcore.protocol.Protocol
import dev.willram.ramrpg.RamRPG
import io.papermc.paper.event.player.PlayerPickItemEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack


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

            Events.subscribe(PrepareItemCraftEvent::class.java)
                .handler { e ->
                    if (e.inventory.result == null) return@handler
                    val item = e.inventory.result
                    if (item == null || !item.hasItemMeta()) return@handler
                    val meta = item.itemMeta
                    if (!meta.isUnbreakable)
                    {
                        meta.isUnbreakable = true
                    }
                    item.itemMeta = meta
                    e.inventory.result = item
                }

            Events.subscribe(CraftItemEvent::class.java)
                .handler { e ->
                    if (e.isCancelled) return@handler
                    val item = e.currentItem
                    if (item == null) return@handler
                    val meta = item.itemMeta
                    if (!meta.isUnbreakable)
                    {
                        meta.isUnbreakable = true
                    }
                    item.itemMeta = meta
                    e.currentItem = item
                }

            Events.subscribe(EntityPickupItemEvent::class.java)
                .handler { e ->
                    if (e.isCancelled) return@handler
                    val item = e.item.itemStack
                    if (item == null) return@handler
                    val meta = item.itemMeta
                    if (!meta.isUnbreakable)
                    {
                        meta.isUnbreakable = true
                    }
                    item.itemMeta = meta
                }

            Events.subscribe(InventoryClickEvent::class.java)
                .handler { e ->
                    if (e.isCancelled) return@handler
                    val item = e.currentItem
                    if (item == null || !item.hasItemMeta()) return@handler
                    val meta = item.itemMeta
                    if (!meta.isUnbreakable)
                    {
                        meta.isUnbreakable = true
                    }
                    item.itemMeta = meta
                }
        }

        private fun applyModifications(item: ItemStack?, player: Player): ItemStack? {
            if (item == null || !item.hasItemMeta()) return item;
            val newItem = item.clone()
            val meta = newItem.itemMeta

            val newLore: MutableList<Component> = ArrayList()

            val itemInfo = Items.retrieve(newItem.type.name)

            if (itemInfo != null) {
                for (stat in itemInfo.stats.keys) {
                    val actualStat = RamRPG.get().stats[stat]
                    newLore.add(MiniMessage.miniMessage().deserialize("<gray>${actualStat.displayName}: ${actualStat.prefix}${ if (itemInfo.stats[stat]!! > 0.0)  "+" else ""}${itemInfo.stats[stat]} ${actualStat.symbol}")
                        .decoration(TextDecoration.ITALIC, false))
                }

                newLore.add(Component.text(""))

                newLore.add(MiniMessage.miniMessage().deserialize("<b>${itemInfo.rarity.color}${itemInfo.rarity.displayName}")
                    .decoration(TextDecoration.ITALIC, false))

                meta.displayName(MiniMessage.miniMessage().deserialize("${itemInfo.rarity.color}${newItem.type.name.lowercase()}").decoration(TextDecoration.ITALIC, false))
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