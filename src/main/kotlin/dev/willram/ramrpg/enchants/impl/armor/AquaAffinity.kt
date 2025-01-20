package dev.willram.ramrpg.enchants.impl.armor

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class AquaAffinity : CustomEnchantment("ram-aqua-affinity", "Aqua Affinity", 1, 1), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Increases underwater mining speed.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_HEAD_ARMOR.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 15
    }

    override fun requiredBookshelfPower(): Int {
        return 9
    }

    override fun vanilla(): Enchantment {
        return Enchantment.AQUA_AFFINITY
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}