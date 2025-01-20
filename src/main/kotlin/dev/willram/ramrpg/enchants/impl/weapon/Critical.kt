package dev.willram.ramrpg.enchants.impl.weapon

import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.stats.Stat
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.inventory.ItemStack

class Critical : CustomEnchantment("ram-critical", "Critical", 1, 5) {

    private val statPerLvl = 20

    override fun description(lvl: Int?): List<String> {
        val actualLvl = lvl ?: 1
        val critDamage = RamRPG.get().stats[Stat.CRIT_DAMAGE]!!
        return listOf("<grey>Grants ${critDamage.prefix}+${statPerLvl * actualLvl} ${critDamage.symbol} ${critDamage.displayName}")
    }

    override fun handleStats(lvl: Int): Int {
        return statPerLvl * lvl
    }

    override fun allowed(item: ItemStack): Boolean {
        //if (item.type == Material.BOOK || item.type == Material.ENCHANTED_BOOK) return true
        return Tag.ITEMS_ENCHANTABLE_SHARP_WEAPON.isTagged(item.type)
    }

    override fun requiredBookshelfPower(): Int {
        return 20
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        val base = 10
        val mult = 5
        return base + (mult * (lvl - 1))
    }
}