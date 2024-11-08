package dev.willram.ramrpg.items

import net.kyori.adventure.text.format.TextColor

enum class ItemRarity(val displayName: String, val color: String) {
    COMMON("Common", "<white>"),
    UNCOMMON("Uncommon", "<green>"),
    RARE("Rare", "<blue>"),
    EPIC("Epic", "<dark_purple>"),
    LEGENDARY("Legendary", "<gold>"),
    MYTHIC("Mythic", "<dark_red>"),
}