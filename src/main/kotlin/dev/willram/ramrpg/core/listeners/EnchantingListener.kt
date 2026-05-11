/** Enchanting table + anvil → RPG enchant flow. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.api.enchants.EnchantingContext
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.enchants.RPGEnchantment
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.enchantment.PrepareItemEnchantEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.enchantments.EnchantmentOffer
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

class EnchantingListener(
    private val enchants: EnchantmentRegistry,
    private val items: ItemInstanceService,
    private val defs: ItemDefinitionRegistry,
) {
    fun register() {
        Events.subscribe(PrepareItemEnchantEvent::class.java).handler(::onPrepare)
        Events.subscribe(EnchantItemEvent::class.java).handler(::onEnchant)
        Events.subscribe(PrepareAnvilEvent::class.java).handler(::onAnvil)
    }

    private fun onPrepare(e: PrepareItemEnchantEvent) {
        val item = e.item
        val candidates = pickable(item, e.enchanter)
        if (candidates.isEmpty()) {
            for (i in e.offers.indices) e.offers[i] = null
            return
        }
        val bookshelves = e.enchantmentBonus.coerceIn(0, 15)
        for (i in e.offers.indices) {
            val pick = candidates.random()
            val maxByPower = if (pick.maxLevel <= 1) 1
            else (1 + (bookshelves * pick.maxLevel / 15)).coerceIn(1, pick.maxLevel)
            val level = Random.nextInt(1, maxByPower + 1)
            val ctx = object : EnchantingContext { override val player: Player = e.enchanter; override val item: ItemStack = item }
            val cost = pick.xpCost(level, ctx).coerceIn(1, 30)
            e.offers[i] = EnchantmentOffer(Enchantment.UNBREAKING, level, cost)
            offerEnchant[Triple(e.enchanter.uniqueId, i, item.type)] = pick.key to level
        }
    }

    private fun onEnchant(e: EnchantItemEvent) {
        val rpg = offerEnchant.remove(Triple(e.enchanter.uniqueId, e.whichButton(), e.item.type))
        // strip vanilla enchants the table would have applied
        e.enchantsToAdd.clear()
        if (rpg == null) return
        val (key, lvl) = rpg
        val ench = enchants.get(key) ?: return
        if (!matchesItem(ench, e.item)) return
        val data = items.identify(e.item) ?: return
        val merged = data.enchantments + (key to lvl.coerceAtMost(ench.maxLevel))
        val rendered = items.write(e.item, data.copy(enchantments = merged))
        e.item.itemMeta = rendered.itemMeta
    }

    private fun onAnvil(e: PrepareAnvilEvent) {
        val inv = e.inventory
        val a = inv.firstItem ?: return
        val b = inv.secondItem
        val da = items.identify(a) ?: return
        val db = b?.let { items.identify(it) }
        val merged = HashMap<EnchantmentKey, Int>(da.enchantments)
        if (db != null) {
            for ((k, v) in db.enchantments) {
                val cur = merged[k]
                val ench = enchants.get(k) ?: continue
                val next = if (cur == null) v else if (cur == v && cur < ench.maxLevel) cur + 1 else maxOf(cur, v)
                merged[k] = next.coerceAtMost(ench.maxLevel)
            }
        }
        val rename = inv.renameText?.takeIf { it.isNotBlank() }
        val out = a.clone()
        val written = items.write(out, da.copy(enchantments = merged, customName = rename ?: da.customName))
        e.result = written
    }

    private fun pickable(item: ItemStack, player: Player): List<RPGEnchantment> {
        val data = items.identify(item) ?: return emptyList()
        val def = defs.get(data.identity.key) ?: return emptyList()
        return enchants.all().filter { ench -> ench.targets.any { it in def.categories } }
    }

    private fun matchesItem(ench: RPGEnchantment, item: ItemStack): Boolean {
        val data = items.identify(item) ?: return false
        val def = defs.get(data.identity.key) ?: return false
        return ench.targets.any { it in def.categories }
    }

    private val offerEnchant = java.util.concurrent.ConcurrentHashMap<Triple<java.util.UUID, Int, org.bukkit.Material>, Pair<EnchantmentKey, Int>>()
}
