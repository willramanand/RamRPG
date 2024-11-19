package dev.willram.ramrpg.enchants.impl.weapon

import dev.willram.ramrpg.enchants.CustomEnchantment
import org.bukkit.Tag
import org.bukkit.inventory.ItemStack

class Lifesteal : CustomEnchantment("ram-lifesteal", "Lifesteal", 1, 5) {

    private val statPerLvl = 2

    override fun description(lvl: Int?): List<String> {
        val actualLvl = lvl ?: 1
        return listOf("<grey>Grants <green>+${statPerLvl * actualLvl}% <grey>healing based on damage dealt.")
    }

    override fun handleStats(lvl: Int): Int {
        return statPerLvl * lvl
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_SHARP_WEAPON.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        val base = 10
        val mult = 15
        return base + (mult * (lvl - 1))
    }

}