package dev.willram.ramrpg.items

import dev.willram.ramcore.data.NamespacedKeys
import dev.willram.ramrpg.stats.Stat
import org.bukkit.NamespacedKey

enum class Items(val key: NamespacedKey, val stats: Map<Stat, Double>, val rarity: ItemRarity) {

    CROSSBOW                    (NamespacedKeys.create("crossbow"), mapOf(Stat.DAMAGE to 9.0), ItemRarity.COMMON),
    BOW                         (NamespacedKeys.create("bow"), mapOf(Stat.DAMAGE to 7.0), ItemRarity.COMMON),
    MACE                        (NamespacedKeys.create("mace"), mapOf(Stat.DAMAGE to 15.0), ItemRarity.RARE),

    WOODEN_PICKAXE              (NamespacedKeys.create("wooden_pickaxe"), mapOf(Stat.DAMAGE to 1.0), ItemRarity.COMMON),
    WOODEN_SHOVEL               (NamespacedKeys.create("wooden_shovel"), mapOf(), ItemRarity.COMMON),
    WOODEN_HOE                  (NamespacedKeys.create("wooden_hoe"), mapOf(), ItemRarity.COMMON),
    WOODEN_AXE                  (NamespacedKeys.create("wooden_axe"), mapOf(Stat.DAMAGE to 2.5), ItemRarity.COMMON),
    WOODEN_SWORD                (NamespacedKeys.create("wooden_sword"), mapOf(Stat.DAMAGE to 2.75), ItemRarity.COMMON),

    STONE_PICKAXE               (NamespacedKeys.create("stone_pickaxe"), mapOf(Stat.DAMAGE to 1.5), ItemRarity.COMMON),
    STONE_SHOVEL                (NamespacedKeys.create("stone_shovel"), mapOf(), ItemRarity.COMMON),
    STONE_HOE                   (NamespacedKeys.create("stone_hoe"), mapOf(), ItemRarity.COMMON),
    STONE_AXE                   (NamespacedKeys.create("stone_axe"), mapOf(Stat.DAMAGE to 2.75), ItemRarity.COMMON),
    STONE_SWORD                 (NamespacedKeys.create("stone_sword"), mapOf(Stat.DAMAGE to 3.0), ItemRarity.COMMON),

    IRON_PICKAXE                (NamespacedKeys.create("iron_pickaxe"), mapOf(Stat.DAMAGE to 1.75), ItemRarity.COMMON),
    IRON_SHOVEL                 (NamespacedKeys.create("iron_shovel"), mapOf(), ItemRarity.COMMON),
    IRON_HOE                    (NamespacedKeys.create("iron_hoe"), mapOf(), ItemRarity.COMMON),
    IRON_AXE                    (NamespacedKeys.create("iron_axe"), mapOf(Stat.DAMAGE to 3.5), ItemRarity.COMMON),
    IRON_SWORD                  (NamespacedKeys.create("iron_sword"), mapOf(Stat.DAMAGE to 4.5), ItemRarity.COMMON),

    GOLD_PICKAXE                (NamespacedKeys.create("gold_pickaxe"), mapOf(Stat.DAMAGE to 1.5), ItemRarity.COMMON),
    GOLD_SHOVEL                 (NamespacedKeys.create("gold_shovel"), mapOf(), ItemRarity.COMMON),
    GOLD_HOE                    (NamespacedKeys.create("gold_hoe"), mapOf(), ItemRarity.COMMON),
    GOLD_AXE                    (NamespacedKeys.create("gold_axe"), mapOf(Stat.DAMAGE to 2.0), ItemRarity.COMMON),
    GOLD_SWORD                  (NamespacedKeys.create("gold_sword"), mapOf(Stat.DAMAGE to 2.5), ItemRarity.COMMON),

    DIAMOND_PICKAXE             (NamespacedKeys.create("diamond_pickaxe"), mapOf(Stat.DAMAGE to 1.5), ItemRarity.COMMON),
    DIAMOND_SHOVEL              (NamespacedKeys.create("diamond_shovel"), mapOf(), ItemRarity.COMMON),
    DIAMOND_HOE                 (NamespacedKeys.create("diamond_hoe"), mapOf(), ItemRarity.COMMON),
    DIAMOND_AXE                 (NamespacedKeys.create("diamond_axe"), mapOf(Stat.DAMAGE to 6.0), ItemRarity.COMMON),
    DIAMOND_SWORD               (NamespacedKeys.create("diamond_sword"), mapOf(Stat.DAMAGE to 6.5), ItemRarity.COMMON),

    NETHERITE_PICKAXE           (NamespacedKeys.create("netherite_pickaxe"), mapOf(Stat.DAMAGE to 2.5), ItemRarity.UNCOMMON),
    NETHERITE_SHOVEL            (NamespacedKeys.create("netherite_shovel"), mapOf(), ItemRarity.UNCOMMON),
    NETHERITE_HOE               (NamespacedKeys.create("netherite_hoe"), mapOf(), ItemRarity.UNCOMMON),
    NETHERITE_AXE               (NamespacedKeys.create("netherite_axe"), mapOf(Stat.DAMAGE to 7.5), ItemRarity.UNCOMMON),
    NETHERITE_SWORD             (NamespacedKeys.create("netherite_sword"), mapOf(Stat.DAMAGE to 8.0), ItemRarity.UNCOMMON),

    LEATHER_HELMET              (NamespacedKeys.create("leather_helmet"), mapOf(Stat.DEFENSE to 2.5), ItemRarity.COMMON),
    LEATHER_CHESTPLATE          (NamespacedKeys.create("leather_chestplate"), mapOf(Stat.DEFENSE to 5.0), ItemRarity.COMMON),
    LEATHER_LEGGINGS            (NamespacedKeys.create("leather_leggings"), mapOf(Stat.DEFENSE to 3.5), ItemRarity.COMMON),
    LEATHER_BOOTS               (NamespacedKeys.create("leather_boots"), mapOf(Stat.DEFENSE to 2.5), ItemRarity.COMMON),

    CHAINMAIL_HELMET            (NamespacedKeys.create("chainmail_helmet"), mapOf(Stat.DEFENSE to 2.5), ItemRarity.COMMON),
    CHAINMAIL_CHESTPLATE        (NamespacedKeys.create("chainmail_chestplate"), mapOf(Stat.DEFENSE to 5.0), ItemRarity.COMMON),
    CHAINMAIL_LEGGINGS          (NamespacedKeys.create("chainmail_leggings"), mapOf(Stat.DEFENSE to 3.5), ItemRarity.COMMON),
    CHAINMAIL_BOOTS             (NamespacedKeys.create("chainmail_boots"), mapOf(Stat.DEFENSE to 2.5), ItemRarity.COMMON),

    IRON_HELMET                 (NamespacedKeys.create("iron_helmet"), mapOf(Stat.DEFENSE to 15.0), ItemRarity.COMMON),
    IRON_CHESTPLATE             (NamespacedKeys.create("iron_chestplate"), mapOf(Stat.DEFENSE to 25.0), ItemRarity.COMMON),
    IRON_LEGGINGS               (NamespacedKeys.create("iron_leggings"), mapOf(Stat.DEFENSE to 20.0), ItemRarity.COMMON),
    IRON_BOOTS                  (NamespacedKeys.create("iron_boots"), mapOf(Stat.DEFENSE to 15.0), ItemRarity.COMMON),

    GOLD_HELMET                 (NamespacedKeys.create("gold_helmet"), mapOf(Stat.DEFENSE to 12.5), ItemRarity.COMMON),
    GOLD_CHESTPLATE             (NamespacedKeys.create("gold_chestplate"), mapOf(Stat.DEFENSE to 15.0), ItemRarity.COMMON),
    GOLD_LEGGINGS               (NamespacedKeys.create("gold_leggings"), mapOf(Stat.DEFENSE to 12.5), ItemRarity.COMMON),
    GOLD_BOOTS                  (NamespacedKeys.create("gold_boots"), mapOf(Stat.DEFENSE to 10.0), ItemRarity.COMMON),

    DIAMOND_HELMET              (NamespacedKeys.create("diamond_helmet"), mapOf(Stat.DEFENSE to 25.0), ItemRarity.COMMON),
    DIAMOND_CHESTPLATE          (NamespacedKeys.create("diamond_chestplate"), mapOf(Stat.DEFENSE to 35.0), ItemRarity.COMMON),
    DIAMOND_LEGGINGS            (NamespacedKeys.create("diamond_leggings"), mapOf(Stat.DEFENSE to 25.0), ItemRarity.COMMON),
    DIAMOND_BOOTS               (NamespacedKeys.create("diamond_boots"), mapOf(Stat.DEFENSE to 15.0), ItemRarity.COMMON),

    NETHERITE_HELMET            (NamespacedKeys.create("netherite_helmet"), mapOf(Stat.DEFENSE to 35.0, Stat.TRUE_DEFENSE to 5.0), ItemRarity.UNCOMMON),
    NETHERITE_CHESTPLATE        (NamespacedKeys.create("netherite_chestplate"), mapOf(Stat.DEFENSE to 50.0, Stat.TRUE_DEFENSE to 5.0), ItemRarity.UNCOMMON),
    NETHERITE_LEGGINGS          (NamespacedKeys.create("netherite_leggings"), mapOf(Stat.DEFENSE to 35.0, Stat.TRUE_DEFENSE to 5.0), ItemRarity.UNCOMMON),
    NETHERITE_BOOTS             (NamespacedKeys.create("netherite_boots"), mapOf(Stat.DEFENSE to 25.0, Stat.TRUE_DEFENSE to 5.0), ItemRarity.UNCOMMON);

    companion object {
        fun retrieve(name: String): Items? {
            for (item in entries) {
                if (!item.name.equals(name, true)) continue
                return item
            }
            return null
        }
    }
}