package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.skills.SkillDefinition
import dev.willram.ramrpg.api.skills.SkillRegistry

class SkillRegistryImpl(
    private val backing: ContentRegistry<SkillDefinition> = ContentRegistry.create(SkillDefinition::class.java)
) : SkillRegistry {
    override fun register(owner: String, def: SkillDefinition) {
        backing.register(owner, ContentKey.of(def.key.id, SkillDefinition::class.java), def)
    }
    override fun unregisterOwner(owner: String): Int = backing.unregisterOwner(owner)
    override fun get(key: SkillKey): SkillDefinition? = backing.get(key.id).orElse(null)
    override fun all(): Collection<SkillDefinition> = backing.entries().map { it.value() }
}
