package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramrpg.api.entities.EntityProfile
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.identity.EntityProfileKey
import dev.willram.ramrpg.core.regions.LevelBandService
import org.bukkit.entity.LivingEntity

class EntityProfileRegistryImpl(
    private val backing: ContentRegistry<EntityProfile> = ContentRegistry.create(EntityProfile::class.java),
    var mythicResolver: ((LivingEntity) -> String?)? = null,
    /**
     * WP-6.2: set by [dev.willram.ramrpg.core.modules.MobModule] at enable-time (never `RamRPG.kt`,
     * B5) -- the same external-hook pattern [mythicResolver] already uses, since this registry (not
     * `EntitySpawnListener`'s construction site) is the one seam both `RamRPG.kt`'s bootstrap listener
     * wiring and a later-running module can both reach. Null until then; [resolve] no-ops the band side
     * effect while null.
     */
    var levelBandService: LevelBandService? = null,
) : EntityProfileRegistry {

    override fun register(owner: String, profile: EntityProfile) {
        backing.register(owner, ContentKey.of(profile.key.id, EntityProfile::class.java), profile)
    }
    override fun unregisterOwner(owner: String): Int = backing.unregisterOwner(owner)
    override fun get(key: EntityProfileKey): EntityProfile? = backing.get(key.id).orElse(null)
    override fun all(): Collection<EntityProfile> = backing.entries().map { it.value() }

    /**
     * WP-6.2: whenever this resolves a profile for a live entity, it also resolves (and caches --
     * [LevelBandService.resolveAndCache] is a no-op past the first call for that entity, rule 4) the
     * entity's level band from its spawn location. This is the "resolve picks the band from spawn
     * location" seam: [dev.willram.ramrpg.core.listeners.EntitySpawnListener] calls [resolve] first
     * thing, so the band is already cached by the time it reads it back to scale stats; the same holds
     * for [dev.willram.ramrpg.core.listeners.LootListener] at death.
     */
    override fun resolve(entity: LivingEntity): EntityProfile? {
        mythicResolver?.invoke(entity)?.let {
            backing.get(ContentId.of("mythic", it.lowercase())).orElse(null)?.let { p ->
                levelBandService?.resolveAndCache(entity)
                return p
            }
        }
        val profile = backing.get(ContentId.of("ramrpg", entity.type.name.lowercase())).orElse(null)
        if (profile != null) levelBandService?.resolveAndCache(entity)
        return profile
    }
}
