package dev.willram.ramrpg.enchants.impl.weapon

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class FireAspect : CustomEnchantment("ram-fire-aspect", "Fire Aspect", 1, 2), ExtendedVanillaEnchantment {

    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Ignites attacked enemies.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_SWORD.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 15
    }

    override fun vanilla(): Enchantment {
        return Enchantment.FIRE_ASPECT
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }

}