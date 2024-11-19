package dev.willram.ramrpg.enchants.impl.weapon

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Sharpness : CustomEnchantment("ram-sharpness", "Sharpness", 1, 5), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        val baseStat = 10
        val actualLvl = lvl ?: 1
        return listOf("<grey>Grants <green>+${baseStat * actualLvl}% <grey>base weapon damage.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_SHARP_WEAPON.isTagged(item.type)
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
        return Enchantment.SHARPNESS
    }

    override fun shouldAddVanilla(): Boolean {
        return false
    }
}