package dev.willram.ramrpg

import dev.willram.ramcore.data.DataKeyCodec
import dev.willram.ramcore.playerdata.PlayerDataOptions
import dev.willram.ramcore.playerdata.PlayerDataService
import dev.willram.ramcore.store.FileStore
import dev.willram.ramcore.store.StoreCodec
import dev.willram.ramcore.store.StoreMigrations
import dev.willram.ramcore.store.Stores
import dev.willram.ramcore.testkit.FakeClock
import dev.willram.ramcore.testkit.FakeScheduler
import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.core.storage.FilePlayerStore
import dev.willram.ramrpg.core.storage.PlayerRpgData
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Verifies the WP-F5 storage swap-in: RamRPG persists PlayerRpgData through PlayerDataService over a
 * real FileStore (Stores API), off-server on FakeScheduler + ProxyFakes. Covers the disk round-trip,
 * the require()->markDirty bridge that makes a clean-loaded player's mutation survive a quit, the
 * transient default for an unloaded player, and the snapshot deep copy.
 */
@Timeout(15, unit = TimeUnit.SECONDS)
class PlayerStoreTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var scheduler: FakeScheduler
    private lateinit var clock: FakeClock
    private val id: UUID = UUID.randomUUID()
    private lateinit var player: ProxyFakes.Recording<Player>

    @BeforeEach
    fun setUp() {
        scheduler = FakeScheduler.install()
        clock = FakeClock.epoch()
        player = ProxyFakes.recording(Player::class.java, mapOf("getUniqueId" to id, "getName" to "will"))
    }

    @AfterEach
    fun tearDown() {
        scheduler.close()
    }

    private fun fileStore(): FileStore<UUID, PlayerRpgData> =
        Stores.file(dir, DataKeyCodec.uuidKeys(), StoreCodec.gson(PlayerRpgData::class.java), StoreMigrations.start())

    private fun newService(): PlayerDataService =
        PlayerDataService.create(PlayerDataOptions.defaults().withLoadTimeout(Duration.ofSeconds(1)), clock)

    private fun loadAndJoin(service: PlayerDataService) {
        service.preload(id)
        scheduler.runAll()
        service.join(player.fake())
        assertTrue(service.isLoaded(id))
    }

    /**
     * Reads a player's persisted record back from disk with a fresh store. Store I/O runs on
     * Schedulers.async(), so drain the fake scheduler and then read without blocking (join() ignores
     * interrupts, so a blocking wait here could hang the suite instead of failing).
     */
    private fun reload(): PlayerRpgData {
        val future = fileStore().load(id).toCompletableFuture()
        scheduler.runAll()
        assertTrue(future.isDone, "store load did not complete after runAll()")
        return future.getNow(java.util.Optional.empty()).orElseThrow()
    }

    @Test
    fun `a player's data round-trips through disk on quit`() {
        val service = newService()
        service.register(FilePlayerStore.RPG_KEY, fileStore())
        val store = FilePlayerStore(service)
        loadAndJoin(service)

        val data = store.require(id)
        data.skillLevels["combat"] = 5
        data.currentMana = 42.0
        data.questCompleted.add("q1")

        service.quit(id)
        scheduler.runAll()
        assertFalse(service.isLoaded(id))

        val loaded = reload()
        assertEquals(5, loaded.skillLevels["combat"])
        assertEquals(42.0, loaded.currentMana)
        assertTrue(loaded.questCompleted.contains("q1"))
    }

    @Test
    fun `mutating a clean-loaded player through require persists on quit`() {
        // Seed an existing record on disk, so the player loads clean (not the always-dirty fresh
        // default). Only require()'s markDirty makes the later mutation survive the quit.
        val seed = PlayerRpgData().apply { skillLevels["combat"] = 1 }
        val seedFuture = fileStore().save(id, seed).toCompletableFuture()
        scheduler.runAll()
        assertTrue(seedFuture.isDone, "store seed save did not complete after runAll()")

        val service = newService()
        service.register(FilePlayerStore.RPG_KEY, fileStore())
        val store = FilePlayerStore(service)
        loadAndJoin(service)
        assertEquals(1, store.get(id)!!.skillLevels["combat"], "loaded the seeded record")

        store.require(id).skillLevels["combat"] = 2

        service.quit(id)
        scheduler.runAll()
        assertEquals(2, reload().skillLevels["combat"])
    }

    @Test
    fun `require on an unloaded player returns a transient default without throwing`() {
        val service = newService()
        service.register(FilePlayerStore.RPG_KEY, fileStore())
        val store = FilePlayerStore(service)

        val result = store.require(UUID.randomUUID())
        assertNotNull(result)
        assertEquals(-1.0, result.currentMana, "a fresh, unpersisted default")
    }

    @Test
    fun `snapshot is an independent deep copy`() {
        val original = PlayerRpgData().apply {
            skillLevels["combat"] = 3
            questCompleted.add("q1")
            currentMana = 10.0
        }

        val snap = original.snapshot()

        original.skillLevels["combat"] = 99
        original.questCompleted.add("q2")
        original.currentMana = 20.0

        assertEquals(3, snap.skillLevels["combat"])
        assertEquals(setOf("q1"), snap.questCompleted)
        assertEquals(10.0, snap.currentMana)
    }
}
