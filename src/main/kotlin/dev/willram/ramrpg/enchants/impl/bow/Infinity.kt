package dev.willram.ramrpg.enchants.impl.bow

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Infinity : CustomEnchantment("ram-infinity", "Infinity", 1, 1), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Shoots an infinite amount of arrows.")
    }

    override fun allowed(item: ItemStack): Boolean {
        //if (item.type == Material.BOOK || item.type == Material.ENCHANTED_BOOK) return true
        return Tag.ITEMS_ENCHANTABLE_BOW.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 30
    }

    override fun requiredBookshelfPower(): Int {
        return 20
    }

    override fun vanilla(): Enchantment {
        return Enchantment.INFINITY
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}