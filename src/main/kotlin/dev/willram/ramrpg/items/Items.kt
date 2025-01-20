package dev.willram.ramrpg.items

import dev.willram.ramcore.data.NamespacedKeys
import dev.willram.ramcore.pdc.PDCs
import dev.willram.ramrpg.stats.Stat
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

enum class Items(val key: String, val displayName: String, val stats: Map<Stat, Double>, val rarity: ItemRarity) {

    ENCHANTED_BOOK              ("enchanted_book", "Enchanted Book", mapOf(), ItemRarity.UNCOMMON),

    TRIDENT                     ("trident", "Trident", mapOf(Stat.DAMAGE to 10.0), ItemRarity.RARE),
    CROSSBOW                    ("crossbow", "Crossbow", mapOf(Stat.DAMAGE to 9.0), ItemRarity.COMMON),
    BOW                         ("bow", "Bow", mapOf(Stat.DAMAGE to 7.0), ItemRarity.COMMON),
    MACE                        ("mace", "Mace", mapOf(Stat.DAMAGE to 15.0), ItemRarity.RARE),
    FISHING_ROD                 ("fishing_rod", "Fishing Rod", mapOf(), ItemRarity.COMMON),

    WOODEN_PICKAXE              ("wooden_pickaxe", "Wooden Pickaxe", mapOf(Stat.DAMAGE to 1.0), ItemRarity.COMMON),
    WOODEN_SHOVEL               ("wooden_shovel", "Wooden Shovel", mapOf(), ItemRarity.COMMON),
    WOODEN_HOE                  ("wooden_hoe", "Wooden Hoe", mapOf(), ItemRarity.COMMON),
    WOODEN_AXE                  ("wooden_axe", "Wooden Shovel", mapOf(Stat.DAMAGE to 2.5), ItemRarity.COMMON),
    WOODEN_SWORD                ("wooden_sword", "Wooden Sword", mapOf(Stat.DAMAGE to 2.75), ItemRarity.COMMON),

    STONE_PICKAXE               ("stone_pickaxe", "Stone Pickaxe", mapOf(Stat.DAMAGE to 1.5), ItemRarity.COMMON),
    STONE_SHOVEL                ("stone_shovel", "Stone Shovel", mapOf(), ItemRarity.COMMON),
    STONE_HOE                   ("stone_hoe", "Stone Hoe", mapOf(), ItemRarity.COMMON),
    STONE_AXE                   ("stone_axe", "Stone Axe", mapOf(Stat.DAMAGE to 2.75), ItemRarity.COMMON),
    STONE_SWORD                 ("stone_sword", "Stone Sword", mapOf(Stat.DAMAGE to 3.0), ItemRarity.COMMON),

    IRON_PICKAXE                ("iron_pickaxe", "Iron Pickaxe", mapOf(Stat.DAMAGE to 1.75), ItemRarity.COMMON),
    IRON_SHOVEL                 ("iron_shovel", "Iron Shovel", mapOf(), ItemRarity.COMMON),
    IRON_HOE                    ("iron_hoe", "Iron Hoe", mapOf(), ItemRarity.COMMON),
    IRON_AXE                    ("iron_axe", "Iron Axe", mapOf(Stat.DAMAGE to 3.5), ItemRarity.COMMON),
    IRON_SWORD                  ("iron_sword", "Iron Sword", mapOf(Stat.DAMAGE to 4.5), ItemRarity.COMMON),

    GOLDEN_PICKAXE              ("golden_pickaxe", "Golden Pickaxe", mapOf(Stat.DAMAGE to 1.5), ItemRarity.COMMON),
    GOLDEN_SHOVEL               ("golden_shovel", "Golden Shovel", mapOf(), ItemRarity.COMMON),
    GOLDEN_HOE                  ("golden_hoe", "Golden Hoe", mapOf(), ItemRarity.COMMON),
    GOLDEN_AXE                  ("golden_axe", "Golden Axe", mapOf(Stat.DAMAGE to 2.0), ItemRarity.COMMON),
    GOLDEN_SWORD                ("golden_sword", "Golden Sword", mapOf(Stat.DAMAGE to 2.5), ItemRarity.COMMON),

    DIAMOND_PICKAXE             ("diamond_pickaxe", "Diamond Pickaxe", mapOf(Stat.DAMAGE to 1.5), ItemRarity.COMMON),
    DIAMOND_SHOVEL              ("diamond_shovel", "Diamond Shovel", mapOf(), ItemRarity.COMMON),
    DIAMOND_HOE                 ("diamond_hoe", "Diamond Hoe", mapOf(), ItemRarity.COMMON),
    DIAMOND_AXE                 ("diamond_axe", "Diamond Axe", mapOf(Stat.DAMAGE to 6.0), ItemRarity.COMMON),
    DIAMOND_SWORD               ("diamond_sword", "Diamond Sword", mapOf(Stat.DAMAGE to 6.5), ItemRarity.COMMON),

    NETHERITE_PICKAXE           ("netherite_pickaxe", "Netherite Pickaxe", mapOf(Stat.DAMAGE to 2.5), ItemRarity.UNCOMMON),
    NETHERITE_SHOVEL            ("netherite_shovel", "Netherite Shovel", mapOf(), ItemRarity.UNCOMMON),
    NETHERITE_HOE               ("netherite_hoe", "Netherite Hoe", mapOf(), ItemRarity.UNCOMMON),
    NETHERITE_AXE               ("netherite_axe", "Netherite Axe", mapOf(Stat.DAMAGE to 7.5), ItemRarity.UNCOMMON),
    NETHERITE_SWORD             ("netherite_sword", "Netherite Sword", mapOf(Stat.DAMAGE to 8.0), ItemRarity.UNCOMMON),

    LEATHER_HELMET              ("leather_helmet", "Leather Helmet", mapOf(Stat.DEFENSE to 2.5), ItemRarity.COMMON),
    LEATHER_CHESTPLATE          ("leather_chestplate", "Leather Chestplate", mapOf(Stat.DEFENSE to 5.0), ItemRarity.COMMON),
    LEATHER_LEGGINGS            ("leather_leggings", "Leather Leggings", mapOf(Stat.DEFENSE to 3.5), ItemRarity.COMMON),
    LEATHER_BOOTS               ("leather_boots", "Leather Boots", mapOf(Stat.DEFENSE to 2.5), ItemRarity.COMMON),

    CHAINMAIL_HELMET            ("chainmail_helmet", "Chainmail Helmet", mapOf(Stat.DEFENSE to 2.5), ItemRarity.COMMON),
    CHAINMAIL_CHESTPLATE        ("chainmail_chestplate", "Chainmail Chestplate", mapOf(Stat.DEFENSE to 5.0), ItemRarity.COMMON),
    CHAINMAIL_LEGGINGS          ("chainmail_leggings", "Chainmail Leggings", mapOf(Stat.DEFENSE to 3.5), ItemRarity.COMMON),
    CHAINMAIL_BOOTS             ("chainmail_boots", "Chainmail Boots", mapOf(Stat.DEFENSE to 2.5), ItemRarity.COMMON),

    IRON_HELMET                 ("iron_helmet", "Iron Helmet", mapOf(Stat.DEFENSE to 15.0), ItemRarity.COMMON),
    IRON_CHESTPLATE             ("iron_chestplate", "Iron Chestplate", mapOf(Stat.DEFENSE to 25.0), ItemRarity.COMMON),
    IRON_LEGGINGS               ("iron_leggings", "Iron Leggings", mapOf(Stat.DEFENSE to 20.0), ItemRarity.COMMON),
    IRON_BOOTS                  ("iron_boots", "Iron Boots", mapOf(Stat.DEFENSE to 15.0), ItemRarity.COMMON),

    GOLDEN_HELMET               ("golden_helmet", "Golden Helmet", mapOf(Stat.DEFENSE to 12.5), ItemRarity.COMMON),
    GOLDEN_CHESTPLATE           ("golden_chestplate", "Golden Chestplate", mapOf(Stat.DEFENSE to 15.0), ItemRarity.COMMON),
    GOLDEN_LEGGINGS             ("golden_leggings", "Golden Leggings", mapOf(Stat.DEFENSE to 12.5), ItemRarity.COMMON),
    GOLDEN_BOOTS                ("golden_boots", "Golden Boots", mapOf(Stat.DEFENSE to 10.0), ItemRarity.COMMON),

    DIAMOND_HELMET              ("diamond_helmet", "Diamond Helmet", mapOf(Stat.DEFENSE to 25.0), ItemRarity.COMMON),
    DIAMOND_CHESTPLATE          ("diamond_chestplate", "Diamond Chestplate", mapOf(Stat.DEFENSE to 35.0), ItemRarity.COMMON),
    DIAMOND_LEGGINGS            ("diamond_leggings", "Diamond Leggings", mapOf(Stat.DEFENSE to 25.0), ItemRarity.COMMON),
    DIAMOND_BOOTS               ("diamond_boots", "Diamond Boots", mapOf(Stat.DEFENSE to 15.0), ItemRarity.COMMON),

    NETHERITE_HELMET            ("netherite_helmet", "Netherite Helmet", mapOf(Stat.DEFENSE to 35.0, Stat.TRUE_DEFENSE to 5.0), ItemRarity.UNCOMMON),
    NETHERITE_CHESTPLATE        ("netherite_chestplate", "Netherite Chestplate", mapOf(Stat.DEFENSE to 50.0, Stat.TRUE_DEFENSE to 5.0), ItemRarity.UNCOMMON),
    NETHERITE_LEGGINGS          ("netherite_leggings", "Netherite Leggings", mapOf(Stat.DEFENSE to 35.0, Stat.TRUE_DEFENSE to 5.0), ItemRarity.UNCOMMON),
    NETHERITE_BOOTS             ("netherite_boots", "Netherite Boots", mapOf(Stat.DEFENSE to 25.0, Stat.TRUE_DEFENSE to 5.0), ItemRarity.UNCOMMON),

    ELYTRA                      ("elytra", "Elytra", mapOf(), ItemRarity.RARE),
    ;
    companion object {
        val ITEM_TYPE_KEY = "ramrpg-item-type"
        fun retrieve(inputItem: ItemStack): Items? {
            if (inputItem != null && inputItem.hasItemMeta() && PDCs.has(inputItem.itemMeta, ITEM_TYPE_KEY)) {
                val customItemType = PDCs.get(inputItem.itemMeta, ITEM_TYPE_KEY, PersistentDataType.STRING)
                if (customItemType != null) {
                    for (item in entries) {
                        if (item.key != customItemType) continue
                        return item
                    }
                }
            }
            for (item in entries) {
                if (!item.name.equals(inputItem.type.name, true)) continue
                return item
            }
            return null
        }
    }
}