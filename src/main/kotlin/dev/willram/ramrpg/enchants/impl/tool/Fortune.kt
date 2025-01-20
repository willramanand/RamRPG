package dev.willram.ramrpg.enchants.impl.tool

import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import dev.willram.ramrpg.stats.Stat
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Fortune : CustomEnchantment("ram-fortune", "Fortune", 1, 10), ExtendedVanillaEnchantment {

    private val statPerLvl = 15

    override fun description(lvl: Int?): List<String> {
        val actualLvl = lvl ?: 1
        val fortune = RamRPG.get().stats[Stat.FORTUNE]!!
        return listOf("<grey>Grants ${fortune.prefix}+${statPerLvl * actualLvl} ${fortune.symbol} ${fortune.displayName}")
    }

    override fun handleStats(lvl: Int): Int {
        return statPerLvl * lvl
    }

    override fun allowed(item: ItemStack): Boolean {
        //if (item.type == Material.BOOK || item.type == Material.ENCHANTED_BOOK) return true
        return Tag.ITEMS_ENCHANTABLE_MINING.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return enchantment is SilkTouch
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        val base = 10
        val mult = 10
        return base + (mult * (lvl - 1))
    }

    override fun requiredBookshelfPower(): Int {
        return 25
    }

    override fun vanilla(): Enchantment {
        return Enchantment.FORTUNE
    }

    override fun shouldAddVanilla(): Boolean {
        return false
    }
}