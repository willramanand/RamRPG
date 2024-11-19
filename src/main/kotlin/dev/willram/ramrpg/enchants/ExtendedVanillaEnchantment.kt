package dev.willram.ramrpg.enchants

import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.meta.ItemMeta

interface ExtendedVanillaEnchantment {

    fun vanilla(): Enchantment
    fun shouldAddVanilla(): Boolean

    fun handleRemove(meta: ItemMeta) {
        if (!meta.hasEnchant(vanilla())) return
        meta.removeEnchant(vanilla())
    }

    fun handleAdd(meta: ItemMeta, lvl: Int) {
        if (!shouldAddVanilla()) return
        if (meta.hasEnchant(vanilla())) {
            handleRemove(meta)
        }
        meta.addEnchant(vanilla(), lvl, true)
    }
}