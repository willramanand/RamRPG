package dev.willram.ramrpg.enchants.impl.trident

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Riptide : CustomEnchantment("ram-riptide", "Riptide", 1, 3), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Trident launches player with itself", "<grey>when thrown while in water or rain.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_TRIDENT.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return enchantment is Loyalty || enchantment is Channeling
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 5 * lvl
    }

    override fun requiredBookshelfPower(): Int {
        return 10
    }

    override fun vanilla(): Enchantment {
        return Enchantment.RIPTIDE
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}