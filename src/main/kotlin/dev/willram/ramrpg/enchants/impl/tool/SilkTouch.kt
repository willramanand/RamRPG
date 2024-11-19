package dev.willram.ramrpg.enchants.impl.tool

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class SilkTouch : CustomEnchantment("ram-silk-touch", "Silk Touch", 1, 1), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Allows collecting normally unobtainable", "<grey>block drops.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_MINING.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return enchantment is Fortune || enchantment is SmeltingTouch
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 45
    }

    override fun vanilla(): Enchantment {
        return Enchantment.SILK_TOUCH
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}