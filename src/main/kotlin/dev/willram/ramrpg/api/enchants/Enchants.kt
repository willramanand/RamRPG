/**
 * RPG enchantment surface. Each [RPGEnchantment] declares targeted
 * [ItemCategory] set and produces a list of [Effect]s per level.
 * Stat contributions and damage hooks both flow through these effects.
 */
package dev.willram.ramrpg.api.enchants

import dev.willram.ramrpg.api.effects.Effect
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.items.ItemCategory
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

enum class EnchantmentRarity { COMMON, UNCOMMON, RARE, VERY_RARE, LEGENDARY }

interface EnchantingContext { val player: Player?; val item: ItemStack? }

interface RPGEnchantment {
    val key: EnchantmentKey
    val displayName: Component
    val maxLevel: Int
    val targets: Set<ItemCategory>
    val rarity: EnchantmentRarity get() = EnchantmentRarity.COMMON
    fun description(level: Int): List<Component> = emptyList()
    fun effects(level: Int): List<Effect>
    fun conflicts(other: EnchantmentKey): Boolean = false
    fun bookshelfPower(level: Int): Int = 0
    fun xpCost(level: Int, ctx: EnchantingContext): Int = level * 10
}

interface EnchantmentRegistry {
    fun register(owner: String, ench: RPGEnchantment)
    fun unregisterOwner(owner: String): Int
    fun get(key: EnchantmentKey): RPGEnchantment?
    fun all(): Collection<RPGEnchantment>
}
