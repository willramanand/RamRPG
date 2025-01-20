package dev.willram.ramrpg.enchants.impl.weapon

import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.stats.Stat
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.inventory.ItemStack

class Ferocious : CustomEnchantment("ram-ferocious", "Ferocious", 1, 5) {

    private val statPerLvl = 2

    override fun description(lvl: Int?): List<String> {
        val actualLvl = lvl ?: 1
        val ferocity = RamRPG.get().stats[Stat.FEROCITY]!!
        return listOf("<grey>Grants ${ferocity.prefix}+${statPerLvl * actualLvl} ${ferocity.symbol} ${ferocity.displayName}")
    }

    override fun handleStats(lvl: Int): Int {
        return statPerLvl * lvl
    }

    override fun allowed(item: ItemStack): Boolean {
        //if (item.type == Material.BOOK || item.type == Material.ENCHANTED_BOOK) return true
        return Tag.ITEMS_ENCHANTABLE_SHARP_WEAPON.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun requiredBookshelfPower(): Int {
        return 30
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        val base = 15
        val mult = 10
        return base + (mult * (lvl - 1))
    }

}