package dev.willram.ramrpg.enchants.impl.rod

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Lure : CustomEnchantment("ram-lure", "Lure", 1, 3), ExtendedVanillaEnchantment {

    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Decreases the wait time for a","<grey>bite on the hook.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_FISHING.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        val base = 10
        val mult = 5
        return base + (mult * (lvl - 1))
    }

    override fun vanilla(): Enchantment {
        return Enchantment.LURE
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}