package dev.willram.ramrpg.source.fishing


import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

enum class FishingSource(override val defaultXp: Double) : Source {
    COD(25.0),
    SALMON(60.0),
    TROPICAL_FISH(250.0),
    PUFFERFISH(115.0),
    TREASURE(500.0),
    JUNK(30.0),
    RARE(500.0),
    EPIC(1000.0);

    override val skill: Skill
        get() = Skill.FISHING

    companion object {
        fun valueOf(item: ItemStack): FishingSource {
            val mat = item.type

            when (mat) {
                Material.COD -> return COD
                Material.SALMON -> return SALMON
                Material.PUFFERFISH -> return PUFFERFISH
                Material.TROPICAL_FISH -> return TROPICAL_FISH
                Material.BOW, Material.ENCHANTED_BOOK, Material.NAME_TAG, Material.SADDLE, Material.NAUTILUS_SHELL -> return TREASURE
                Material.FISHING_ROD -> {
                    if (item.enchantments.isEmpty()) return JUNK
                    return TREASURE
                }
                Material.BOWL, Material.LEATHER, Material.LEATHER_BOOTS, Material.ROTTEN_FLESH,
                Material.POTION, Material.BONE, Material.TRIPWIRE_HOOK, Material.STICK, Material.STRING,
                Material.INK_SAC, Material.LILY_PAD, Material.BAMBOO -> return JUNK
                else -> return JUNK
            }
        }
    }
}