package dev.willram.ramrpg.enchants.impl.crossbow

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Multishot : CustomEnchantment("ram-multishot", "Multishot", 1, 1), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Fires 3 arrows at the same time.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_CROSSBOW.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return enchantment is Piercing
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 20
    }

    override fun requiredBookshelfPower(): Int {
        return 21
    }

    override fun vanilla(): Enchantment {
        return Enchantment.MULTISHOT
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}