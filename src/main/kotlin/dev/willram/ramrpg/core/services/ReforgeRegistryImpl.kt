package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.reforges.ReforgeDefinition
import dev.willram.ramrpg.api.reforges.ReforgeRegistry

/**
 * Owner-scoped [ReforgeRegistry] over RamCore's [ContentRegistry]. Unchanged behaviour; WP-3.3c note:
 * [all] is the source of the reforge POOL a reforge recipe draws from -- a `type = reforge` recipe names
 * one of these keys, and [dev.willram.ramrpg.core.crafting.ReforgeOutcomes.pool] orders them for a seeded
 * random-reforge selection. See `docs/design/3.3c-reforge-costs.md`.
 */
class ReforgeRegistryImpl(
    private val backing: ContentRegistry<ReforgeDefinition> = ContentRegistry.create(ReforgeDefinition::class.java)
) : ReforgeRegistry {
    override fun register(owner: String, def: ReforgeDefinition) {
        backing.register(owner, ContentKey.of(def.key.id, ReforgeDefinition::class.java), def)
    }
    override fun unregisterOwner(owner: String): Int = backing.unregisterOwner(owner)
    override fun get(key: ReforgeKey): ReforgeDefinition? = backing.get(key.id).orElse(null)
    override fun all(): Collection<ReforgeDefinition> = backing.entries().map { it.value() }
}
