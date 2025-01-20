package dev.willram.ramrpg.enchants.impl.armor

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class DepthStrider : CustomEnchantment("ram-depth-strider", "Depth Strider", 1, 3), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Increases underwater movement speed.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_FOOT_ARMOR.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return enchantment is FrostWalker
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 15 * lvl
    }

    override fun requiredBookshelfPower(): Int {
        return 16
    }

    override fun vanilla(): Enchantment {
        return Enchantment.DEPTH_STRIDER
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}