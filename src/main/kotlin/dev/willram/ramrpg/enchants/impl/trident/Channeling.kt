package dev.willram.ramrpg.enchants.impl.trident

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Channeling : CustomEnchantment("ram-channeling", "Channeling", 1, 1), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>During thunderstorms, trident summons a lightning", "<grey>bolt on the target when hitting it.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_TRIDENT.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return enchantment is Riptide
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 15
    }

    override fun requiredBookshelfPower(): Int {
        return 17
    }

    override fun vanilla(): Enchantment {
        return Enchantment.CHANNELING
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}