package dev.willram.ramrpg.items

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import dev.willram.ramcore.data.NamespacedKeys
import dev.willram.ramcore.data.PDCs
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.stats.Stat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.persistence.PersistentDataType
import java.util.function.Consumer
import javax.annotation.Nonnull

class RamItemBuilder private constructor(@Nonnull private val itemStack: ItemStack) {

    private val lore: MutableList<Component> = mutableListOf()
    private val statModifiers: MutableMap<Stat, Double> = mutableMapOf()
    private val attributesModifiers: Multimap<Attribute, AttributeModifier> = HashMultimap.create()
    private var displayName: Component = itemStack.displayName()
    private var rarity: ItemRarity = ItemRarity.COMMON

    companion object {
        private val ALL_FLAGS = ItemFlag.entries.toTypedArray()

        @JvmStatic
        fun of(material: Material): RamItemBuilder {
            return RamItemBuilder(ItemStack(material))
        }

        @JvmStatic
        fun of(itemStack: ItemStack): RamItemBuilder {
            return RamItemBuilder(itemStack)
        }
    }

    fun transform(it: Consumer<ItemStack>): RamItemBuilder {
        it.accept(this.itemStack)
        return this
    }

    fun transformMeta(meta: Consumer<ItemMeta>): RamItemBuilder {
        this.itemStack.itemMeta?.let {
            meta.accept(it)
            this.itemStack.itemMeta = it
        }
        return this
    }

    fun name(name: String): RamItemBuilder {
        displayName = MiniMessage.miniMessage().deserialize(name)
        return this
    }

    fun lore(line: String): RamItemBuilder {
        lore.add(MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false))
        return this
    }

    fun lore(vararg lines: Component): RamItemBuilder {
        lore.addAll(lines)
        return this
    }

    fun lore(lines: Iterable<Component>): RamItemBuilder {
        lore.addAll(lines)
        return this
    }

    fun clearLore(): RamItemBuilder {
        lore.clear()
        return this
    }

    fun flag(vararg flags: ItemFlag): RamItemBuilder {
        return transformMeta { it.addItemFlags(*flags) }
    }

    fun unflag(vararg flags: ItemFlag): RamItemBuilder {
        return transformMeta { it.removeItemFlags(*flags) }
    }

    fun color(color: Color): RamItemBuilder {
        return transform {
            when (it.type) {
                Material.LEATHER_BOOTS, Material.LEATHER_CHESTPLATE, Material.LEATHER_HELMET, Material.LEATHER_LEGGINGS -> {
                    val meta = it.itemMeta as LeatherArmorMeta
                    meta.setColor(color)
                    it.itemMeta = meta
                }
                else -> {}
            }
        }
    }

    fun breakable(flag: Boolean): RamItemBuilder {
        return transformMeta { it.isUnbreakable = !flag }
    }

    fun apply(consumer: RamItemBuilder.() -> Unit): RamItemBuilder {
        consumer(this)
        return this
    }

    fun rarity(rarity: ItemRarity): RamItemBuilder {
        this.rarity = rarity
        return this
    }

    fun addStatModifier(stat: Stat, value: Double): RamItemBuilder {
        statModifiers[stat] = value
        return this
    }

    fun hasStatModifier(stat: Stat): Boolean {
        return statModifiers[stat] != null
    }

    fun removeStatModifier(stat: Stat): RamItemBuilder {
        statModifiers.remove(stat)
        return this
    }

    fun addAttributeModifier(attribute: Attribute, modifier: AttributeModifier): RamItemBuilder {
        attributesModifiers.put(attribute, modifier)
        return this
    }

    fun hasAttributeModifier(attribute: Attribute): Boolean {
        return attributesModifiers.containsKey(attribute)
    }

    fun removeAttributeModifier(attribute: Attribute): RamItemBuilder {
        attributesModifiers.removeAll(attribute)
        return this
    }

    fun build(): ItemStack {
        val meta = this.itemStack.itemMeta

        meta.displayName(MiniMessage.miniMessage().deserialize("${rarity.color}<displayname>", Placeholder.component("displayname", displayName)).decoration(TextDecoration.ITALIC, false))
        val newLore: MutableList<Component> = mutableListOf()

        if (this.lore.isNotEmpty()) {
            newLore.add(Component.text(""))
            newLore.addAll(this.lore)
        }
        newLore.add(Component.text(""))

        for (stat in statModifiers.keys) {
            val actualStat = RamRPG.get().stats[stat]
            PDCs.set(meta, actualStat.modifierName, PersistentDataType.DOUBLE, statModifiers[stat])
            newLore.add(MiniMessage.miniMessage().deserialize("<gray>${actualStat.displayName}: ${actualStat.prefix}${ if (statModifiers[stat]!! > 0.0)  "+" else ""}${statModifiers[stat]} ${actualStat.symbol}")
                .decoration(TextDecoration.ITALIC, false))
        }

        newLore.add(Component.text(""))

        newLore.add(MiniMessage.miniMessage().deserialize("<b>${rarity.color}${rarity.displayName}")
            .decoration(TextDecoration.ITALIC, false))
        PDCs.set(meta, "rpg_rarity", PersistentDataType.STRING, rarity.name)

        meta.lore(newLore)

        meta.attributeModifiers?.putAll(attributesModifiers)

        meta.addAttributeModifier(
            Attribute.ATTACK_DAMAGE,
            AttributeModifier(NamespacedKeys.create("Help"), 0.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
        )
        meta.itemFlags.addAll(ALL_FLAGS)

        val newItem = ItemStack(this.itemStack.type)
        newItem.itemMeta = meta

        return newItem
    }
}

