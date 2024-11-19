package dev.willram.ramrpg.enchants.impl.armor

import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.enchants.CustomEnchantment
import dev.willram.ramrpg.enchants.ExtendedVanillaEnchantment
import dev.willram.ramrpg.stats.Stat
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class BlastProtection : CustomEnchantment("ram-blast-protection", "Blast Protection", 1, 4), ExtendedVanillaEnchantment {

    private val statPerLvl = 30

    override fun description(lvl: Int?): List<String> {
        val actualLvl = lvl ?: 1
        val defense = RamRPG.get().stats[Stat.DEFENSE]!!
        return listOf("<grey>Grants ${defense.prefix}+${statPerLvl * actualLvl} ${defense.symbol} ${defense.displayName} <grey>against", "<grey>explosions.")
    }

    override fun handleStats(lvl: Int): Int {
        return statPerLvl * lvl
    }

    override fun allowed(item: ItemStack): Boolean {
        return Tag.ITEMS_ENCHANTABLE_ARMOR.isTagged(item.type)
    }

    override fun conflicts(enchantment: CustomEnchantment): Boolean {
        return enchantment is FireProtection || enchantment is ProjectileProtection || enchantment is Protection
    }

    override fun xpCosts(lvl: Int, item: ItemStack): Int {
        val base = 10
        val mult = 5
        return base + (mult * (lvl - 1))
    }

    override fun vanilla(): Enchantment {
        return Enchantment.BLAST_PROTECTION
    }

    override fun shouldAddVanilla(): Boolean {
        return false
    }
}