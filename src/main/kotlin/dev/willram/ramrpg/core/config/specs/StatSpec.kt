/**
 * WP-1.5a: PURE spec for a `stats/` content entry -> [StatDefinition]. No Bukkit: [StatDefinition] is
 * itself Bukkit-free (Adventure + RamRPG types only), so [toDefinition] lives here and the whole file
 * unit-tests off-server.
 *
 * HOCON shape (namespaced ids/keys MUST be quoted -- ':' is a HOCON key/value separator):
 * ```
 * id = "ramrpg:crit_chance"
 * name = "<red>Crit Chance"     # MiniMessage; defaults to the id value
 * symbol = "☣"
 * color = "red"                 # named or #rrggbb; defaults white
 * base = 0.0                    # defaultBase
 * per-level = 0.5
 * min = 0.0
 * max = 100.0
 * format = "PERCENT"            # WHOLE | ONE_DECIMAL | PERCENT
 * translation-key = "ramrpg.stat.crit_chance"
 * ```
 */
package dev.willram.ramrpg.core.config.specs

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.stats.StatDefinition
import dev.willram.ramrpg.api.stats.StatFormat
import dev.willram.ramrpg.core.config.RpgContentSpec
import dev.willram.ramrpg.core.config.SpecNodes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.spongepowered.configurate.ConfigurationNode

data class StatSpec(
    val key: StatKey,
    val displayName: Component,
    val symbol: String?,
    val color: TextColor,
    val defaultBase: Double,
    val perLevel: Double,
    val min: Double?,
    val max: Double?,
    val format: StatFormat,
    val translationKey: String?,
) : RpgContentSpec {
    override val id: ContentId get() = key.id

    fun toDefinition(): StatDefinition = StatDefinition(
        key = key,
        displayName = displayName,
        symbol = symbol,
        color = color,
        defaultBase = defaultBase,
        perLevel = perLevel,
        min = min,
        max = max,
        format = format,
        translationKey = translationKey,
    )

    companion object {
        fun deserialize(node: ConfigurationNode): StatSpec {
            val id = SpecNodes.requireId(node)
            val min = SpecNodes.doubleOrNull(node, "min")
            val max = SpecNodes.doubleOrNull(node, "max")
            if (min != null && max != null && min > max) {
                throw ContentDeserializeException("stat min $min is greater than max $max")
            }
            return StatSpec(
                key = StatKey(id),
                displayName = SpecNodes.componentOr(node, "name", Component.text(id.value())),
                symbol = SpecNodes.optionalString(node, "symbol"),
                color = SpecNodes.colorOr(node, "color", NamedTextColor.WHITE),
                defaultBase = SpecNodes.doubleOr(node, "base", 0.0),
                perLevel = SpecNodes.doubleOr(node, "per-level", 0.0),
                min = min,
                max = max,
                format = SpecNodes.enumOr(node, "format", StatFormat.WHOLE, "stat format"),
                translationKey = SpecNodes.optionalString(node, "translation-key"),
            )
        }
    }
}
