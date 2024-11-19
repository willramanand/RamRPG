package dev.willram.ramrpg.enchants.impl.weapon

import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.stats.Stat
import org.bukkit.Tag
import org.bukkit.inventory.ItemStack

class TrueStrike : CustomEnchantment("ram-true-strike", "True Strike", 1, 5) {

    private val statPerLvl = 5

    override fun description(lvl: Int?): List<String> {
        val actualLvl = lvl ?: 1
        val critChance = RamRPG.get().stats[Stat.CRIT_CHANCE]!!
        return listOf("<grey>Grants ${critChance.prefix}+${statPerLvl * actualLvl} ${critChance.symbol} ${critChance.displayName}")
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
        val mult = 10
        return base + (mult * (lvl - 1))
    }

}