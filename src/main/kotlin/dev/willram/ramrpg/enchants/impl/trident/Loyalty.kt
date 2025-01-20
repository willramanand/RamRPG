package dev.willram.ramrpg.enchants.impl.trident

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Loyalty : CustomEnchantment("ram-loyalty", "Loyalty", 1, 3), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Trident returns after being thrown.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_TRIDENT.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return enchantment is Riptide
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 5 * lvl
    }

    override fun requiredBookshelfPower(): Int {
        return 7
    }

    override fun vanilla(): Enchantment {
        return Enchantment.LOYALTY
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}