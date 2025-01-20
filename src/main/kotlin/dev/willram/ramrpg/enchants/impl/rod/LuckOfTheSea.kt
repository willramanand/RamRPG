package dev.willram.ramrpg.enchants.impl.rod

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class LuckOfTheSea : CustomEnchantment("ram-luckofthesea", "Luck of The Sea", 1, 3), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Increases luck while fishing.")
    }

    override fun allowed(item: ItemStack): Boolean {
        //if (item.type == Material.BOOK || item.type == Material.ENCHANTED_BOOK) return true
        return Tag.ITEMS_ENCHANTABLE_FISHING.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        val base = 10
        val mult = 5
        return base + (mult * (lvl - 1))
    }

    override fun requiredBookshelfPower(): Int {
        return 15
    }

    override fun vanilla(): Enchantment {
        return Enchantment.LUCK_OF_THE_SEA
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}