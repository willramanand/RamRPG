package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import java.util.concurrent.atomic.AtomicInteger

class ItemDefinitionRegistryImpl(
    private val backing: ContentRegistry<ItemDefinition> = ContentRegistry.create(ItemDefinition::class.java)
) : ItemDefinitionRegistry {
    private val rev = AtomicInteger(0)

    override fun get(key: ItemKey): ItemDefinition? = backing.get(key.id).orElse(null)

    override fun register(owner: String, def: ItemDefinition) {
        backing.register(owner, ContentKey.of(def.key.id, ItemDefinition::class.java), def)
        rev.incrementAndGet()
    }

    override fun unregisterOwner(owner: String): Int {
        val n = backing.unregisterOwner(owner)
        if (n > 0) rev.incrementAndGet()
        return n
    }

    override fun all(): Collection<ItemDefinition> = backing.entries().map { it.value() }

    override fun revision(): Int = rev.get()
}
