package dev.willram.ramrpg.enchants.impl.weapon

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Knockback : CustomEnchantment("ram-knockback", "Knockback", 1, 2), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Increases player knockback.")
    }

    override fun allowed(item: ItemStack): Boolean {
        //if (item.type == Material.BOOK || item.type == Material.ENCHANTED_BOOK) return true
        return Tag.ITEMS_ENCHANTABLE_SHARP_WEAPON.isTagged(item.type)
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
        return Enchantment.KNOCKBACK
    }

    override fun requiredBookshelfPower(): Int {
        return 12
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}