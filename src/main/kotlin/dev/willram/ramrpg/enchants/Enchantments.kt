package dev.willram.ramrpg.enchants

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import dev.willram.ramcore.pdc.DataType
import dev.willram.ramcore.pdc.PDCs
import dev.willram.ramrpg.enchants.impl.armor.*
import dev.willram.ramrpg.enchants.impl.rod.LuckOfTheSea
import dev.willram.ramrpg.enchants.impl.rod.Lure
import dev.willram.ramrpg.enchants.impl.tool.Efficiency
import dev.willram.ramrpg.enchants.impl.tool.Fortune
import dev.willram.ramrpg.enchants.impl.tool.SilkTouch
import dev.willram.ramrpg.enchants.impl.tool.SmeltingTouch
import dev.willram.ramrpg.enchants.impl.weapon.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

class Enchantments {

    companion object {
        private val ENCHANTS_DATA_TYPE = DataType.asMap(DataType.STRING, DataType.INTEGER)
        private val ENCHANTS_PDC_KEY = "ramrpg-enchants"
        private val REGISTRY: MutableMap<String, CustomEnchantment> = mutableMapOf()
        private val CONVERSION_REGISTRY: BiMap<Enchantment, CustomEnchantment> = HashBiMap.create()

        // Armor Enchantments
        val PROTECTION: CustomEnchantment = Protection()
        val PROJECTILE_PROTECTION: CustomEnchantment = ProjectileProtection()
        val BLAST_PROTECTION: CustomEnchantment = BlastProtection()
        val FIRE_PROTECTION: CustomEnchantment = FireProtection()
        val GROWTH: CustomEnchantment = Growth()
        val MENDING: CustomEnchantment = Mending()

        // Fishing Enchantments
        val LURE: CustomEnchantment = Lure()
        val LUCK_OF_THE_SEA: CustomEnchantment = LuckOfTheSea()

        // Mining Enchantments
        val EFFICIENCY: CustomEnchantment = Efficiency()
        val FORTUNE: CustomEnchantment = Fortune()
        val SILK_TOUCH: CustomEnchantment = SilkTouch()
        val SMELTING_TOUCH: CustomEnchantment = SmeltingTouch()

        // Weapon Enchantments
        val CRITICAL: CustomEnchantment = Critical()
        val FEROCIOUS: CustomEnchantment = Ferocious()
        val KNOCKBACK: CustomEnchantment = Knockback()
        val LIFESTEAL: CustomEnchantment = Lifesteal()
        val LOOTING: CustomEnchantment = Looting()
        val SHARPNESS: CustomEnchantment = Sharpness()
        val SWEEPING_EDGE: CustomEnchantment = SweepingEdge()
        val TRUE_STRIKE: CustomEnchantment = TrueStrike()
        val FIRE_ASPECT: CustomEnchantment = FireAspect()

        fun register() {
            // ARMOR
            REGISTRY.putIfAbsent(PROTECTION.key, PROTECTION)
            REGISTRY.putIfAbsent(PROJECTILE_PROTECTION.key, PROJECTILE_PROTECTION)
            REGISTRY.putIfAbsent(BLAST_PROTECTION.key, BLAST_PROTECTION)
            REGISTRY.putIfAbsent(FIRE_PROTECTION.key, FIRE_PROTECTION)
            REGISTRY.putIfAbsent(GROWTH.key, GROWTH)
            REGISTRY.putIfAbsent(MENDING.key, MENDING)

            // FISHING RODS
            REGISTRY.putIfAbsent(LURE.key, LURE)
            REGISTRY.putIfAbsent(LUCK_OF_THE_SEA.key, LUCK_OF_THE_SEA)

            // TOOLS
            REGISTRY.putIfAbsent(EFFICIENCY.key, EFFICIENCY)
            REGISTRY.putIfAbsent(FORTUNE.key, FORTUNE)
            REGISTRY.putIfAbsent(SILK_TOUCH.key, SILK_TOUCH)
            REGISTRY.putIfAbsent(SMELTING_TOUCH.key, SMELTING_TOUCH)

            // WEAPONS
            REGISTRY.putIfAbsent(CRITICAL.key, CRITICAL)
            REGISTRY.putIfAbsent(FEROCIOUS.key, FEROCIOUS)
            REGISTRY.putIfAbsent(KNOCKBACK.key, KNOCKBACK)
            REGISTRY.putIfAbsent(LIFESTEAL.key, LIFESTEAL)
            REGISTRY.putIfAbsent(LOOTING.key, LOOTING)
            REGISTRY.putIfAbsent(SHARPNESS.key, SHARPNESS)
            REGISTRY.putIfAbsent(SWEEPING_EDGE.key, SWEEPING_EDGE)
            REGISTRY.putIfAbsent(TRUE_STRIKE.key, TRUE_STRIKE)
            REGISTRY.putIfAbsent(FIRE_ASPECT.key, FIRE_ASPECT)

            for (enchantment in REGISTRY.values) {
                if (enchantment !is ExtendedVanillaEnchantment) continue
                CONVERSION_REGISTRY.putIfAbsent(enchantment.vanilla(), enchantment)
            }
        }

        fun hasEnchant(item: ItemStack, enchantment: CustomEnchantment): Boolean {
            //if(item == null || !item.hasItemMeta()) return false
            if (!PDCs.has(item.itemMeta, ENCHANTS_PDC_KEY)) return false
            val map = PDCs.get(item.itemMeta, ENCHANTS_PDC_KEY, ENCHANTS_DATA_TYPE) ?: return false
            return map.containsKey(enchantment.key)
        }

        fun removeEnchant(item: ItemStack, enchantment: CustomEnchantment) {
            //if(item == null || !item.hasItemMeta()) return
            if (!hasEnchant(item, enchantment)) return
            val meta = item.itemMeta
            var map = PDCs.get(item.itemMeta, ENCHANTS_PDC_KEY, ENCHANTS_DATA_TYPE)
            if (map == null) {
                map = mutableMapOf()
            }
            map.remove(enchantment.key)
            PDCs.set(meta, ENCHANTS_PDC_KEY, ENCHANTS_DATA_TYPE, map)

            if (enchantment is ExtendedVanillaEnchantment && enchantment.shouldAddVanilla()) {
                meta.removeEnchant(enchantment.vanilla())
            }

            item.itemMeta = meta
        }

        fun addEnchant(item: ItemStack, enchantment: CustomEnchantment, lvl: Int) {
            //if(item == null || !item.hasItemMeta()) return
            if (hasEnchant(item, enchantment)) {
                removeEnchant(item, enchantment)
            }
            val meta = item.itemMeta
            var map = PDCs.get(item.itemMeta, ENCHANTS_PDC_KEY, ENCHANTS_DATA_TYPE)
            if (map == null) {
                map = mutableMapOf()
            }
            map[enchantment.key] = lvl
            PDCs.set(meta, ENCHANTS_PDC_KEY, ENCHANTS_DATA_TYPE, map)

            if (enchantment is ExtendedVanillaEnchantment && enchantment.shouldAddVanilla()) {
                meta.addEnchant(enchantment.vanilla(), lvl, true)
            }

            item.itemMeta = meta
        }

        fun getEnchants(item: ItemStack): Map<CustomEnchantment, Int> {
            if(item == null || !item.hasItemMeta()) return mutableMapOf()
            val enchants = mutableMapOf<CustomEnchantment, Int>()
            val map = PDCs.get(item.itemMeta, ENCHANTS_PDC_KEY, ENCHANTS_DATA_TYPE)
            if (map == null) return enchants
            for (entry in map) {
                val enchantment = REGISTRY[entry.key]!!
                enchants[enchantment] = entry.value
            }
            return enchants
        }

        fun getEnchantmentLevel(item: ItemStack, enchantment: CustomEnchantment): Int {
            if(item == null || !item.hasItemMeta()) return 0
            if (!hasEnchant(item, enchantment)) return 0
            val map = PDCs.get(item.itemMeta, ENCHANTS_PDC_KEY, ENCHANTS_DATA_TYPE)
            if (map == null) return 0
            return map[enchantment.key]!!
        }

        fun removeAllEnchants(item: ItemStack) {
            val enchants = getEnchants(item)
            for (entry in enchants) {
                removeEnchant(item, entry.key)
            }
        }

        fun convertEnchants(item: ItemStack) {
            if (item == null || !item.hasItemMeta() || item.itemMeta.enchants.isEmpty()) return
            for (enchantment in item.itemMeta.enchants) {
                item.removeEnchantment(enchantment.key)
                if (CONVERSION_REGISTRY.containsKey(enchantment.key)) {
                    addEnchant(item, CONVERSION_REGISTRY.getValue(enchantment.key), enchantment.value)
                }
            }
        }

        fun all(): List<CustomEnchantment> {
            return REGISTRY.values.toList()
        }
    }
}