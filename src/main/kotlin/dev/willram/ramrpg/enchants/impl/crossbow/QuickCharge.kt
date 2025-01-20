package dev.willram.ramrpg.enchants.impl.crossbow

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class QuickCharge : CustomEnchantment("ram-quick-charge", "Quick Charge", 1 ,3), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Decreases crossbow charging time.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_CROSSBOW.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 5 * lvl
    }

    override fun requiredBookshelfPower(): Int {
        return 12
    }

    override fun vanilla(): Enchantment {
        return Enchantment.QUICK_CHARGE
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}