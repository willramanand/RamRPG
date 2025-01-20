package dev.willram.ramrpg.enchants.impl.weapon

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class SweepingEdge : CustomEnchantment("ram-sweeping-edge", "Sweeping Edge", 1, 3), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Grants increased sweeping damage.")
    }

    override fun allowed(item: ItemStack): Boolean {
        //if (item.type == Material.BOOK || item.type == Material.ENCHANTED_BOOK) return true
        return Tag.ITEMS_ENCHANTABLE_SWORD.isTagged(item.type)
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
        return Enchantment.SWEEPING_EDGE
    }

    override fun requiredBookshelfPower(): Int {
        return 9
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}