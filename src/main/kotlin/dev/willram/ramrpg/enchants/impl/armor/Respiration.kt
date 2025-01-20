package dev.willram.ramrpg.enchants.impl.armor

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Respiration : CustomEnchantment("ram-respiration", "Respiration", 1, 3), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Extends underwater breathing time.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_HEAD_ARMOR.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return lvl * 6
    }

    override fun requiredBookshelfPower(): Int {
        return 19
    }

    override fun vanilla(): Enchantment {
        return Enchantment.RESPIRATION
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}