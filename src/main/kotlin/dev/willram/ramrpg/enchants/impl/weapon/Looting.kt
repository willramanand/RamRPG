package dev.willram.ramrpg.enchants.impl.weapon

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Looting : CustomEnchantment("ram-looting", "Looting", 1, 3), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Grants increase loot from mob kills.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_SWORD.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        val base = 10
        val mult = 10
        return base + (mult * (lvl - 1))
    }

    override fun vanilla(): Enchantment {
        return Enchantment.LOOTING
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }

}