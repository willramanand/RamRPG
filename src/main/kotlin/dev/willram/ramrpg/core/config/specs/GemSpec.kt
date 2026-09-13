/**
 * WP-1.5a: PURE spec for a `gems/` content entry -> [GemDefinition]. Fully Bukkit-free.
 *
 * HOCON shape:
 * ```
 * id = "ramrpg:ruby"
 * name = "<red>Ruby"
 * stats { "ramrpg:strength" = 5.0, "ramrpg:crit_damage" = 3.0 }
 * ```
 */
package dev.willram.ramrpg.core.config.specs

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.sockets.GemDefinition
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.core.config.RpgContentSpec
import dev.willram.ramrpg.core.config.SpecNodes
import net.kyori.adventure.text.Component
import org.spongepowered.configurate.ConfigurationNode

data class GemSpec(
    val key: GemKey,
    val displayName: Component,
    val statContribution: Map<StatKey, Double>,
) : RpgContentSpec {
    override val id: ContentId get() = key.id

    fun toDefinition(): GemDefinition = GemDefinition(
        key = key,
        displayName = displayName,
        statContribution = statContribution,
    )

    companion object {
        fun deserialize(node: ConfigurationNode): GemSpec {
            val id = SpecNodes.requireId(node)
            return GemSpec(
                key = GemKey(id),
                displayName = SpecNodes.componentOr(node, "name", Component.text(id.value())),
                statContribution = SpecNodes.statAmounts(node, "stats"),
            )
        }
    }
}
