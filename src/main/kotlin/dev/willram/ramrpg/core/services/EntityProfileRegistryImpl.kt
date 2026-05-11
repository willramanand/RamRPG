package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramrpg.api.entities.EntityProfile
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.identity.EntityProfileKey
import org.bukkit.entity.LivingEntity

class EntityProfileRegistryImpl(
    private val backing: ContentRegistry<EntityProfile> = ContentRegistry.create(EntityProfile::class.java),
    var mythicResolver: ((LivingEntity) -> String?)? = null,
) : EntityProfileRegistry {

    override fun register(owner: String, profile: EntityProfile) {
        backing.register(owner, ContentKey.of(profile.key.id, EntityProfile::class.java), profile)
    }
    override fun unregisterOwner(owner: String): Int = backing.unregisterOwner(owner)
    override fun get(key: EntityProfileKey): EntityProfile? = backing.get(key.id).orElse(null)
    override fun all(): Collection<EntityProfile> = backing.entries().map { it.value() }

    override fun resolve(entity: LivingEntity): EntityProfile? {
        mythicResolver?.invoke(entity)?.let {
            backing.get(ContentId.of("mythic", it.lowercase())).orElse(null)?.let { p -> return p }
        }
        return backing.get(ContentId.of("ramrpg", entity.type.name.lowercase())).orElse(null)
    }
}
