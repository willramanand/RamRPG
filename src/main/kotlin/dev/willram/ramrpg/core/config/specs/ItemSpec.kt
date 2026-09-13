/**
 * WP-1.5a: PURE spec for an `items/` content entry. Deliberately holds `material` as a raw String:
 * [dev.willram.ramrpg.api.items.ItemDefinition] carries an `org.bukkit.Material`, so keeping the spec
 * Bukkit-free means the String -> Material resolution (and its "unknown material" error) happens in
 * [dev.willram.ramrpg.core.config.ContentRegistrarRpg] on the server, NOT here. `base-stats` are held
 * as raw amounts; the registrar attaches the `ModifierSource` (this item) when it builds the
 * StatModifier list.
 *
 * SEAM (WP-1.5b): items will also carry an `effects = [...]` bundle. 1.5a intentionally does NOT read
 * or model effects -- the EffectSpec schema and EffectActionRegistry are WP-1.5b's job. Leaving the
 * key unread here keeps the deserializer forward-compatible: adding effect parsing later is additive.
 *
 * HOCON shape (namespaced ids/keys MUST be quoted -- ':' is a HOCON separator):
 * ```
 * id = "ramrpg:test_sword"
 * name = "<red>Test Sword"
 * material = "DIAMOND_SWORD"
 * rarity = "RARE"                       # COMMON..MYTHIC; defaults COMMON
 * categories = ["SWORD"]
 * base-stats { "ramrpg:damage" = 50.0, "ramrpg:strength" = 20.0 }
 * description = ["<gray>A test blade."]
 * stat-rolls = [ { stat = "ramrpg:crit_chance", min = 0.0, max = 10.0 } ]
 * max-stack = 1
 * custom-model-data = 1001
 * allow-vanilla-wrapper = false
 * ```
 */
package dev.willram.ramrpg.core.config.specs

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.StatRoll
import dev.willram.ramrpg.core.config.RpgContentSpec
import dev.willram.ramrpg.core.config.SpecNodes
import net.kyori.adventure.text.Component
import org.spongepowered.configurate.ConfigurationNode

data class ItemSpec(
    val key: ItemKey,
    val displayName: Component,
    /** Raw material name; resolved to org.bukkit.Material by the registrar (kept out of this pure spec). */
    val material: String,
    val rarity: Rarity,
    val categories: Set<ItemCategory>,
    /** stat id -> ADD amount; the registrar wraps these in StatModifiers sourced to this item. */
    val baseStats: Map<StatKey, Double>,
    val description: List<Component>,
    val statRolls: List<StatRoll>,
    val maxStack: Int?,
    val customModelData: Int?,
    val allowVanillaWrapper: Boolean,
) : RpgContentSpec {
    override val id: ContentId get() = key.id

    companion object {
        fun deserialize(node: ConfigurationNode): ItemSpec {
            val id = SpecNodes.requireId(node)
            return ItemSpec(
                key = ItemKey(id),
                displayName = SpecNodes.componentOr(node, "name", Component.text(id.value())),
                material = SpecNodes.requiredString(node, "material").uppercase(),
                rarity = SpecNodes.enumOr(node, "rarity", Rarity.COMMON, "rarity"),
                categories = SpecNodes.enumList<ItemCategory>(node, "categories", "item category").toSet(),
                baseStats = SpecNodes.statAmounts(node, "base-stats"),
                description = SpecNodes.componentList(node, "description"),
                statRolls = statRolls(node),
                maxStack = SpecNodes.intOrNull(node, "max-stack"),
                customModelData = SpecNodes.intOrNull(node, "custom-model-data"),
                allowVanillaWrapper = SpecNodes.boolOr(node, "allow-vanilla-wrapper", false),
            )
        }

        private fun statRolls(node: ConfigurationNode): List<StatRoll> =
            node.node("stat-rolls").childrenList().map { roll ->
                val min = SpecNodes.doubleOr(roll, "min", 0.0)
                val max = SpecNodes.doubleOr(roll, "max", min)
                if (min > max) throw ContentDeserializeException("stat-roll min $min is greater than max $max")
                StatRoll(StatKey(SpecNodes.requireIdAt(roll, "stat")), min, max)
            }
    }
}
