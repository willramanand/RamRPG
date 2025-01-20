package dev.willram.ramrpg.enchants.impl.bow

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Power : CustomEnchantment("ram-power", "Power", 1, 5), ExtendedVanillaEnchantment {

    private val statPerLvl = 10
    override fun description(lvl: Int?): List<String> {
        val actualLvl = lvl ?: 1
        return listOf("<grey>Grants <green>+${10 * actualLvl}% <grey>base weapon damage.")
    }

    override fun allowed(item: ItemStack): Boolean {
        //if (item.type == Material.BOOK || item.type == Material.ENCHANTED_BOOK) return true
        return Tag.ITEMS_ENCHANTABLE_BOW.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return lvl * statPerLvl
    }

    override fun requiredBookshelfPower(): Int {
        return 5
    }

    override fun vanilla(): Enchantment {
        return Enchantment.POWER
    }

    override fun shouldAddVanilla(): Boolean {
        return false
    }
}