/**
 * RamCore-backed PlayerStore. Uses Repositories.jsonByUuid for async file
 * I/O keyed by player UUID. saveAsync queues background writes.
 */
package dev.willram.ramrpg.core.storage

import dev.willram.ramcore.data.FileDataRepository
import dev.willram.ramcore.data.Repositories
import dev.willram.ramcore.scheduler.Schedulers
import java.nio.file.Path
import java.util.UUID

class FilePlayerStore(directory: Path) : PlayerStore, AutoCloseable {

    private val repo: FileDataRepository<UUID, PlayerRpgData> =
        Repositories.jsonByUuid(directory, PlayerRpgData::class.java) { r -> Schedulers.runAsync(r) }

    init { repo.setup() }

    override fun get(id: UUID): PlayerRpgData? = repo.find(id).orElse(null)
    override fun require(id: UUID): PlayerRpgData {
        repo.find(id).orElse(null)?.let { return it }
        val fresh = PlayerRpgData()
        repo.add(id, fresh)
        return fresh
    }
    override fun put(id: UUID, data: PlayerRpgData) { repo.add(id, data); data.markDirty() }
    override fun remove(id: UUID): PlayerRpgData? {
        val v = repo.find(id).orElse(null) ?: return null
        repo.remove(id)
        return v
    }
    override fun all(): Map<UUID, PlayerRpgData> = repo.registry()

    fun loadAsync(id: UUID, onLoaded: (PlayerRpgData) -> Unit) {
        Schedulers.runAsync {
            val data = repo.find(id).orElseGet {
                val fresh = PlayerRpgData()
                repo.add(id, fresh)
                fresh
            }
            onLoaded(data)
        }
    }

    fun saveAsync(id: UUID) { repo.queueSave(id) }
    fun saveAll() { repo.saveAll() }
    override fun close() { repo.close() }
}
