package dev.willram.ramrpg.enchants.impl.armor

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class FeatherFalling : CustomEnchantment("ram-feather-falling", "Feather Falling", 1, 4), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Reduces fall damage.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_FOOT_ARMOR.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 5 * lvl
    }

    override fun requiredBookshelfPower(): Int {
        return 14
    }

    override fun vanilla(): Enchantment {
        return Enchantment.FEATHER_FALLING
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}