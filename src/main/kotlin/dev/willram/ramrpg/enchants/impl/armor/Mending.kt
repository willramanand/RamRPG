package dev.willram.ramrpg.enchants.impl.armor

import dev.willram.ramcore.data.NamespacedKeys
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import dev.willram.ramrpg.stats.Stat
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.inventory.ItemStack

class Mending : CustomEnchantment("ram-mending", "Mending", 1, 3), ExtendedVanillaEnchantment {

    private val statPerLvl = 2

    override fun description(lvl: Int?): List<String> {
        val actualLvl = lvl ?: 1
        val healthRegen = RamRPG.get().stats[Stat.HEALTH_REGEN]!!
        return listOf("<grey>Grants ${healthRegen.prefix}+${statPerLvl * actualLvl} ${healthRegen.symbol} ${healthRegen.displayName}<grey>.")
    }

    override fun handleStats(lvl: Int): Int {
        return statPerLvl * lvl
    }

    override fun allowed(item: ItemStack): Boolean {
        //if (item.type == Material.BOOK || item.type == Material.ENCHANTED_BOOK) return true
        return Tag.ITEMS_ENCHANTABLE_ARMOR.isTagged(item.type) || item.type == Material.ELYTRA
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return false
    }

    override fun requiredBookshelfPower(): Int {
        return 15
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        val base = 9
        val mult = 9
        return base + (mult * (lvl - 1))
    }

    override fun vanilla(): Enchantment {
        return Enchantment.MENDING
    }

    override fun shouldAddVanilla(): Boolean {
        return false
    }
}