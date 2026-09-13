/**
 * WP-1.5a: PURE spec for a `reforges/` content entry -> [ReforgeDefinition]. Fully Bukkit-free.
 *
 * HOCON shape:
 * ```
 * id = "ramrpg:sharp"
 * name = "<aqua>Sharp"
 * universal { "ramrpg:crit_chance" = 5.0 }
 * by-category {
 *   SWORD { "ramrpg:damage" = 10.0 }
 *   BOW   { "ramrpg:damage" = 6.0 }
 * }
 * ```
 */
package dev.willram.ramrpg.core.config.specs

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.reforges.ReforgeDefinition
import dev.willram.ramrpg.core.config.RpgContentSpec
import dev.willram.ramrpg.core.config.SpecNodes
import net.kyori.adventure.text.Component
import org.spongepowered.configurate.ConfigurationNode

data class ReforgeSpec(
    val key: ReforgeKey,
    val displayName: Component,
    val bonusesByCategory: Map<ItemCategory, Map<StatKey, Double>>,
    val universal: Map<StatKey, Double>,
) : RpgContentSpec {
    override val id: ContentId get() = key.id

    fun toDefinition(): ReforgeDefinition = ReforgeDefinition(
        key = key,
        displayName = displayName,
        bonusesByCategory = bonusesByCategory,
        universal = universal,
    )

    companion object {
        fun deserialize(node: ConfigurationNode): ReforgeSpec {
            val id = SpecNodes.requireId(node)
            val byCategory = LinkedHashMap<ItemCategory, Map<StatKey, Double>>()
            val categoryNode = node.node("by-category")
            categoryNode.childrenMap().forEach { (rawKey, _) ->
                val category = SpecNodes.parseEnum<ItemCategory>(rawKey.toString(), "item category")
                byCategory[category] = SpecNodes.statAmounts(categoryNode, rawKey.toString())
            }
            return ReforgeSpec(
                key = ReforgeKey(id),
                displayName = SpecNodes.componentOr(node, "name", Component.text(id.value())),
                bonusesByCategory = byCategory,
                universal = SpecNodes.statAmounts(node, "universal"),
            )
        }
    }
}
