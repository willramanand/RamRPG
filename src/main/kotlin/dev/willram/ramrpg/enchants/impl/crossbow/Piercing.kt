package dev.willram.ramrpg.enchants.impl.crossbow

import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Piercing : CustomEnchantment("ram-piercing", "Piercing", 1, 4), ExtendedVanillaEnchantment {
    override fun description(lvl: Int?): List<String> {
        return listOf("<grey>Arrows pierce entities, allowing for arrows","<grey>to pierce through stacks of mobs.")
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_CROSSBOW.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return enchantment is Multishot
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        return 6 * lvl
    }

    override fun requiredBookshelfPower(): Int {
        return 18
    }

    override fun vanilla(): Enchantment {
        return Enchantment.PIERCING
    }

    override fun shouldAddVanilla(): Boolean {
        return true
    }
}