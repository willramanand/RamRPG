package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramrpg.api.sets.SetDefinition
import dev.willram.ramrpg.api.sets.SetKey
import dev.willram.ramrpg.api.sets.SetRegistry

/** WP-5.3: the [SetRegistry] backing store. Same `ContentRegistry`-backed shape as every other owner-scoped
 *  RPG registry (`GemRegistryImpl`, `ReforgeRegistryImpl`, ...) -- no parallel storage mechanism (rule 1). */
class SetRegistryImpl(
    private val backing: ContentRegistry<SetDefinition> = ContentRegistry.create(SetDefinition::class.java)
) : SetRegistry {
    override fun register(owner: String, def: SetDefinition) {
        backing.register(owner, ContentKey.of(def.key.id, SetDefinition::class.java), def)
    }
    override fun unregisterOwner(owner: String): Int = backing.unregisterOwner(owner)
    override fun get(key: SetKey): SetDefinition? = backing.get(key.id).orElse(null)
    override fun all(): Collection<SetDefinition> = backing.entries().map { it.value() }
}
