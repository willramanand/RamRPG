package dev.willram.ramrpg.enchants.impl.armor

import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.stats.Stat
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.inventory.ItemStack

class Growth : CustomEnchantment("ram-growth", "Growth", 1, 5) {

    private val statPerLvl = 15

    override fun description(lvl: Int?): List<String> {
        val actualLvl = lvl ?: 1
        val health = RamRPG.get().stats[Stat.HEALTH]!!
        return listOf("<grey>Grants ${health.prefix}+${statPerLvl * actualLvl} ${health.symbol} ${health.displayName}<grey>.")
    }

    override fun handleStats(lvl: Int): Int {
        return statPerLvl * lvl
    }

    override fun allowed(item: ItemStack): Boolean {
        //if (item.type == Material.BOOK || item.type == Material.ENCHANTED_BOOK) return true
        return Tag.ITEMS_ENCHANTABLE_ARMOR.isTagged(item.type) || item.type == Material.ELYTRA
    }

    override fun requiredBookshelfPower(): Int {
        return 10
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