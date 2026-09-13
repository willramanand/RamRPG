package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramrpg.api.crafting.Station
import dev.willram.ramrpg.api.crafting.StationKey
import dev.willram.ramrpg.api.crafting.StationRegistry

/**
 * WP-3.1b: owner-scoped [Station] registry, backed by RamCore's [ContentRegistry] exactly like
 * [RecipeRegistryImpl] / [GemRegistryImpl]. Stations are content, so they too owner-unregister on
 * `/rpg reload`. [StationBlockListener][dev.willram.ramrpg.core.listeners.StationBlockListener] reads
 * [all] to find which station a right-clicked block opens.
 */
class StationRegistryImpl(
    private val backing: ContentRegistry<Station> = ContentRegistry.create(Station::class.java),
) : StationRegistry {
    override fun register(owner: String, station: Station) {
        backing.register(owner, ContentKey.of(station.key.id, Station::class.java), station)
    }

    override fun unregisterOwner(owner: String): Int = backing.unregisterOwner(owner)

    override fun get(key: StationKey): Station? = backing.get(key.id).orElse(null)

    override fun all(): Collection<Station> = backing.entries().map { it.value() }
}
