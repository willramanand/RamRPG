package dev.willram.ramrpg.enchants.impl.armor

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class SoulSpeed : CustomEnchantment("ram-soul-speed", "Soul Speed", 1, 3), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Increases movement speed on soul sand and soul soil.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_FOOT_ARMOR.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return lvl * 5
    }

    override fun requiredBookshelfPower(): Int {
        return 10
    }

    override fun vanilla(): Enchantment {
        return Enchantment.SOUL_SPEED
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}