package dev.willram.ramrpg.enchants

import dev.willram.ramcore.utils.Formatter
import org.bukkit.inventory.ItemStack

abstract class CustomEnchantment(val key: String, val baseName: String, val startLvl: Int, val maxLvl: Int) {

    fun displayName(lvl: Int?): String {
        if (lvl == null) {
            return baseName
        }
        return "$baseName ${Formatter.romanNumeral(lvl)}"
    }

    abstract fun description(lvl: Int?): List<String>
    abstract fun allowed(item: ItemStack): Boolean
    abstract fun conflicts(enchantment: CustomEnchantment): Boolean
    abstract fun xpCosts(lvl: Int, item: ItemStack): Int

    open fun handleStats(lvl: Int): Int {
        return 0
    }
    open fun handleEffects() {}
}