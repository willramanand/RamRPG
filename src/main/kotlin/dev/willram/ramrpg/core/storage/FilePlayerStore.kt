/**
 * PlayerStore backed by RamCore's PlayerDataService (Stores API). The service loads each player's
 * PlayerRpgData before join, hands out the live instance synchronously while online, and writes it
 * back on quit, on the autosave timer, and at shutdown. This adapter is a thin bridge so the many
 * services and listeners that take a PlayerStore keep working unchanged.
 *
 * The store, key, and service are wired in RamRPG.load(); see [RPG_KEY].
 */
package dev.willram.ramrpg.core.storage

import dev.willram.ramcore.playerdata.PlayerDataKey
import dev.willram.ramcore.playerdata.PlayerDataService
import java.util.UUID

class FilePlayerStore(private val data: PlayerDataService) : PlayerStore {

    override fun get(id: UUID): PlayerRpgData? = data.get(id, RPG_KEY).orElse(null)

    /**
     * The mutation accessor. PlayerDataService tracks its own per-key dirty set (a bare
     * [PlayerRpgData.markDirty] is invisible to it), and its per-player quit and autosave writes save
     * dirty keys only. Every mutation in this plugin goes through require(), so announcing dirtiness
     * here is exactly enough to guarantee those writes happen; read-only callers use [get].
     */
    override fun require(id: UUID): PlayerRpgData {
        val existing = data.get(id, RPG_KEY).orElse(null)
        if (existing != null) {
            data.markDirty(id, RPG_KEY)
            return existing
        }
        // The player is not loaded (offline, or between preload and join). All real callers are
        // online, so this is a safety fallback: a transient default that is never persisted.
        return PlayerRpgData()
    }

    override fun put(id: UUID, data: PlayerRpgData) {
        this.data.get(id, RPG_KEY).ifPresent { this.data.set(id, RPG_KEY, data) }
    }

    override fun remove(id: UUID): PlayerRpgData? = get(id) // eviction is the service's job (quit)

    override fun all(): Map<UUID, PlayerRpgData> = emptyMap() // service is online-only; unused today

    companion object {
        /** The one per-player value RamRPG persists. Snapshot copies on the player thread (B3). */
        val RPG_KEY: PlayerDataKey<PlayerRpgData> =
            PlayerDataKey.of("rpg", PlayerRpgData::class.java, ::PlayerRpgData, PlayerRpgData::snapshot)
    }
}
