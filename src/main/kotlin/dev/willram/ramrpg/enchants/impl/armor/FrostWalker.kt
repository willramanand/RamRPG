package dev.willram.ramrpg.enchants.impl.armor

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class FrostWalker : CustomEnchantment("ram-frost-walker", "Frost Walker", 1, 2), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Allows the player to walk on water", "<grey>by freezing the water under their feet.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_FOOT_ARMOR.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return enchantment is DepthStrider
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 7 * lvl
    }

    override fun requiredBookshelfPower(): Int {
        return 18
    }

    override fun vanilla(): Enchantment {
        return Enchantment.FROST_WALKER
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}