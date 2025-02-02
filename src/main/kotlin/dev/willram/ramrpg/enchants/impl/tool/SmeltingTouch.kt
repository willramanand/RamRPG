package dev.willram.ramrpg.enchants.impl.tool

import dev.willram.ramrpg.enchants.CustomEnchantment
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.inventory.ItemStack

class SmeltingTouch : CustomEnchantment("ram-smelting-touch", "Smelting Touch", 1, 1) {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Automatically smelts broken blocks into","<grey>their smelted form.")
    }

    override fun allowed(item: ItemStack): Boolean {
        //if (item.type == Material.BOOK || item.type == Material.ENCHANTED_BOOK) return true
        return Tag.ITEMS_ENCHANTABLE_MINING.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return enchantment is SilkTouch
    }

    override fun requiredBookshelfPower(): Int {
        return 30
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 45
    }
}