/**
 * WP-1.5b: the id -> [BlockMatcher] registry backing the `on_block_break:<matcher-id>` trigger form.
 * Same DESIGN RULE as the other two registries: builtin matcher ids are DATA here, never a `when` --
 * `on_block_break` names a matcher by id and this registry resolves it, so a new "which blocks" set is
 * added by [register]ing an id ([dev.willram.ramrpg.core.effects.BuiltinEffectActions], where
 * [dev.willram.ramrpg.api.effects.BlockMatchers.ofMaterials] is entry one), not by editing a branch.
 *
 * A [BlockMatcher] is resolved once, at parse time (the trigger holds the matcher instance), so this
 * registry stores fully-built matchers rather than factories -- a matcher needs no per-effect params.
 * The registry touches no Bukkit; the matchers it stores do (they test `block.type`).
 */
package dev.willram.ramrpg.core.effects

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.effects.BlockMatcher

class BlockMatcherRegistry {

    private val matchers = LinkedHashMap<ContentId, BlockMatcher>()

    fun register(id: ContentId, matcher: BlockMatcher) {
        require(matchers.put(id, matcher) == null) { "duplicate block matcher id '$id'" }
    }

    fun contains(id: ContentId): Boolean = id in matchers

    fun get(id: ContentId): BlockMatcher? = matchers[id]

    fun ids(): Set<ContentId> = java.util.Collections.unmodifiableSet(LinkedHashSet(matchers.keys))

    /**
     * Resolves [id] into a [BlockMatcher]. Throws [ContentDeserializeException] NAMING the id when it is
     * not registered, so `on_block_break:<bad-id>` aggregates as a load error rather than crashing.
     */
    fun require(id: ContentId): BlockMatcher =
        matchers[id]
            ?: throw ContentDeserializeException(
                "unknown block matcher '$id'; registered matchers: ${matchers.keys.joinToString(", ").ifEmpty { "<none>" }}",
            )
}
