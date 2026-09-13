/**
 * WP-5.3: PURE spec for a `sets/` content entry -> [SetDefinition]. Fully Bukkit-free, same shape as
 * every other content spec ([ReforgeSpec], [GemSpec], [EnchantSpec]). A set's threshold bonuses are
 * ordinary `effects = [...]` bundles parsed by [EffectSpec] -- the SAME parser items/enchants use, so
 * set content is never a special case (rule 2 -- Effect-only).
 *
 * HOCON shape (namespaced ids MUST be quoted -- ':' is a HOCON separator; threshold keys are quoted
 * integers, since they are HOCON object keys):
 * ```
 * id = "ramrpg:netherite_berserker"
 * name = "<dark_red>Netherite Berserker"
 * members = ["ramrpg:netherite_helmet", "ramrpg:netherite_chestplate", "ramrpg:netherite_leggings", "ramrpg:netherite_boots"]
 * thresholds {
 *   "2" { effects = [ { type = stat, stat = "ramrpg:crit_chance", amount = 8.0 } ] }
 *   "4" { effects = [ { type = stat, stat = "ramrpg:crit_damage", amount = 25.0 } ] }
 * }
 * ```
 */
package dev.willram.ramrpg.core.config.specs

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.effects.Effect
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.sets.SetDefinition
import dev.willram.ramrpg.api.sets.SetKey
import dev.willram.ramrpg.core.config.RpgContentSpec
import dev.willram.ramrpg.core.config.SpecNodes
import net.kyori.adventure.text.Component
import org.spongepowered.configurate.ConfigurationNode

data class SetSpec(
    val key: SetKey,
    val displayName: Component,
    val members: Set<ItemKey>,
    val thresholds: Map<Int, List<Effect>>,
) : RpgContentSpec {
    override val id: ContentId get() = key.id

    fun toDefinition(): SetDefinition = SetDefinition(
        key = key,
        displayName = displayName,
        members = members,
        thresholds = thresholds,
    )

    companion object {
        fun deserialize(node: ConfigurationNode, effects: EffectSpec.Registries? = null): SetSpec {
            val id = SpecNodes.requireId(node)

            val members = node.node("members").childrenList()
                .mapNotNull { it.getString() }
                .map { ItemKey(SpecNodes.parseId(it, "members entry")) }
                .toSet()
            if (members.isEmpty()) throw ContentDeserializeException("set '$id' must declare at least one member")

            val thresholds = LinkedHashMap<Int, List<Effect>>()
            node.node("thresholds").childrenMap().forEach { (rawKey, thresholdNode) ->
                val count = rawKey.toString().trim().toIntOrNull()
                    ?: throw ContentDeserializeException("threshold key '$rawKey' is not an integer member count")
                if (count <= 0) throw ContentDeserializeException("threshold count $count must be positive")
                // A distinct synthetic owner per threshold (not just [id]) so each threshold's effect
                // keys ("<owner>_fx<index>") never collide across thresholds of the SAME set.
                val thresholdOwner = ContentId.of(id.namespace(), "${id.value()}_t$count")
                thresholds[count] = EffectSpec.bundle(thresholdNode, "effects", thresholdOwner, effects)
            }
            if (thresholds.isEmpty()) throw ContentDeserializeException("set '$id' must declare at least one threshold")

            return SetSpec(
                key = SetKey(id),
                displayName = SpecNodes.componentOr(node, "name", Component.text(id.value())),
                members = members,
                thresholds = thresholds,
            )
        }
    }
}
