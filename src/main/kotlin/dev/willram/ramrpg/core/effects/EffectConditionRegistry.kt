/**
 * WP-1.5b: the id -> [Condition] factory registry for a [dev.willram.ramrpg.api.effects.TriggeredEffect]'s
 * `conditions` list. Same DESIGN RULE as [EffectActionRegistry]: builtin condition ids are DATA here,
 * never a `when` branch -- a new gate is added by [register]ing an id + factory
 * ([dev.willram.ramrpg.core.effects.BuiltinEffectActions]), and [create] resolves by lookup, raising a
 * [ContentDeserializeException] that NAMES an unknown id so failures aggregate.
 *
 * A factory reads the condition's `params` node at parse time and returns a [Condition] evaluated later
 * against the live [dev.willram.ramrpg.api.effects.EffectContext]. This registry touches no Bukkit; the
 * conditions it produces may (e.g. reading `player.isSneaking`).
 */
package dev.willram.ramrpg.core.effects

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.effects.Condition
import org.spongepowered.configurate.ConfigurationNode

/** Builds one [Condition] from a condition entry's `params` node. Pure at construction. */
fun interface EffectConditionFactory {
    fun create(params: ConfigurationNode): Condition
}

class EffectConditionRegistry {

    private val factories = LinkedHashMap<ContentId, EffectConditionFactory>()

    fun register(id: ContentId, factory: EffectConditionFactory) {
        require(factories.put(id, factory) == null) { "duplicate effect condition id '$id'" }
    }

    fun contains(id: ContentId): Boolean = id in factories

    fun ids(): Set<ContentId> = java.util.Collections.unmodifiableSet(LinkedHashSet(factories.keys))

    /**
     * Resolves [id] + [params] into a [Condition]. Throws [ContentDeserializeException] NAMING the id
     * when it is not registered, so the caller aggregates it rather than crashing the load.
     */
    fun create(id: ContentId, params: ConfigurationNode): Condition {
        val factory = factories[id]
            ?: throw ContentDeserializeException(
                "unknown effect condition '$id'; registered conditions: ${factories.keys.joinToString(", ").ifEmpty { "<none>" }}",
            )
        return factory.create(params)
    }
}
