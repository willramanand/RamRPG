/**
 * WP-1.5a: PURE spec for an `enchants/` content entry -> [RPGEnchantment]. [RPGEnchantment] is a
 * Bukkit-free interface (its one Bukkit-touching method, `xpCost(level, EnchantingContext)`, keeps its
 * interface default here), so [toEnchantment] returns a pure implementation from this file.
 *
 * SEAM (WP-1.5b): an enchant's gameplay is a bundle of [dev.willram.ramrpg.api.effects.Effect]s. 1.5a
 * does NOT model the effect schema, so [SpecEnchantment.effects] returns an empty list for every
 * level. WP-1.5b fills this by parsing an `effects = [...]` bundle into the spec and returning it
 * (scaled by level) here -- an additive change that does not alter this file's shape.
 *
 * HOCON shape:
 * ```
 * id = "ramrpg:sharpness"
 * name = "<blue>Sharpness"
 * max-level = 5
 * targets = ["SWORD", "AXE"]
 * rarity = "COMMON"                       # EnchantmentRarity; defaults COMMON
 * description = ["<gray>Increases melee damage."]
 * conflicts-with = ["ramrpg:smite"]
 * ```
 */
package dev.willram.ramrpg.core.config.specs

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.effects.Effect
import dev.willram.ramrpg.api.enchants.EnchantmentRarity
import dev.willram.ramrpg.api.enchants.RPGEnchantment
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.core.config.RpgContentSpec
import dev.willram.ramrpg.core.config.SpecNodes
import net.kyori.adventure.text.Component
import org.spongepowered.configurate.ConfigurationNode

data class EnchantSpec(
    val key: EnchantmentKey,
    val displayName: Component,
    val maxLevel: Int,
    val targets: Set<ItemCategory>,
    val rarity: EnchantmentRarity,
    val description: List<Component>,
    val conflictsWith: Set<EnchantmentKey>,
) : RpgContentSpec {
    override val id: ContentId get() = key.id

    fun toEnchantment(): RPGEnchantment = SpecEnchantment(this)

    /**
     * PURE [RPGEnchantment] backed by an [EnchantSpec]. `effects(level)` is empty in 1.5a (see the
     * class seam note); `xpCost` and `bookshelfPower` keep the interface defaults, so no Bukkit type
     * (EnchantingContext) is referenced.
     */
    private class SpecEnchantment(private val spec: EnchantSpec) : RPGEnchantment {
        override val key: EnchantmentKey get() = spec.key
        override val displayName: Component get() = spec.displayName
        override val maxLevel: Int get() = spec.maxLevel
        override val targets: Set<ItemCategory> get() = spec.targets
        override val rarity: EnchantmentRarity get() = spec.rarity
        override fun description(level: Int): List<Component> = spec.description
        override fun effects(level: Int): List<Effect> = emptyList()
        override fun conflicts(other: EnchantmentKey): Boolean = other in spec.conflictsWith
    }

    companion object {
        private const val DEFAULT_MAX_LEVEL = 1

        fun deserialize(node: ConfigurationNode): EnchantSpec {
            val id = SpecNodes.requireId(node)
            return EnchantSpec(
                key = EnchantmentKey(id),
                displayName = SpecNodes.componentOr(node, "name", Component.text(id.value())),
                maxLevel = SpecNodes.intOr(node, "max-level", DEFAULT_MAX_LEVEL),
                targets = SpecNodes.enumList<ItemCategory>(node, "targets", "item category").toSet(),
                rarity = SpecNodes.enumOr(node, "rarity", EnchantmentRarity.COMMON, "enchantment rarity"),
                description = SpecNodes.componentList(node, "description"),
                conflictsWith = SpecNodes.stringList(node, "conflicts-with")
                    .map { EnchantmentKey(SpecNodes.parseId(it, "conflicts-with id")) }
                    .toSet(),
            )
        }
    }
}
