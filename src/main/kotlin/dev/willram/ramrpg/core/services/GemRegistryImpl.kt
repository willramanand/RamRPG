package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramrpg.api.sockets.GemDefinition
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.api.sockets.GemRegistry

/**
 * Owner-scoped [GemRegistry] over a RamCore [ContentRegistry]. WP-3.3d: the `insert_gem` recipes in
 * `content/recipes/sockets.conf` reference gems by the SAME [GemKey] ids registered here (e.g. the
 * builtin ruby/sapphire/emerald/amethyst/topaz/opal), and the gem a craft sets is looked up through this
 * registry by [dev.willram.ramrpg.core.services.socketStatsFor] -- there is no parallel gem store for
 * crafted sockets.
 */
class GemRegistryImpl(
    private val backing: ContentRegistry<GemDefinition> = ContentRegistry.create(GemDefinition::class.java)
) : GemRegistry {
    override fun register(owner: String, def: GemDefinition) {
        backing.register(owner, ContentKey.of(def.key.id, GemDefinition::class.java), def)
    }
    override fun unregisterOwner(owner: String): Int = backing.unregisterOwner(owner)
    override fun get(key: GemKey): GemDefinition? = backing.get(key.id).orElse(null)
    override fun all(): Collection<GemDefinition> = backing.entries().map { it.value() }
}
