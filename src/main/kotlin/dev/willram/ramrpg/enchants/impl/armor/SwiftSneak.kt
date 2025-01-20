package dev.willram.ramrpg.enchants.impl.armor

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class SwiftSneak : CustomEnchantment("ram-swift-sneak", "Swift Sneak", 1, 3), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Increases sneaking speed.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_LEG_ARMOR.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return lvl * 10
    }

    override fun requiredBookshelfPower(): Int {
        return 10
    }

    override fun vanilla(): Enchantment {
        return Enchantment.SWIFT_SNEAK
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}