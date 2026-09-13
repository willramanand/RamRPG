package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.loot.LootClaimStatus
import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.entities.EntityProfile
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.identity.EntityProfileKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.core.loot.BossLootService
import dev.willram.ramrpg.core.loot.RpgItemPayload
import dev.willram.ramrpg.core.loot.RpgLootTables
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * WP-1.1b: one boss kill -> one GROUP-scoped, PER_PLAYER_ONCE [dev.willram.ramcore.loot.LootInstance].
 * Pure/off-server: Bukkit `Player`/`LivingEntity` are faked with `ramcore-test`'s [ProxyFakes] rather
 * than a live server or a mocking library (none is on the test classpath).
 */
class BossLootInstanceTest {
    private val bossDrop = ItemKey.of("test", "boss_drop")
    private lateinit var service: BossLootService
    private lateinit var boss: LivingEntity

    @BeforeEach
    fun setUp() {
        val profile = EntityProfile(
            key = EntityProfileKey.of("test", "boss"),
            isBoss = true,
            lootTable = RpgLootTables.builder(ContentId.of("test", "boss_table"))
                .independent(bossDrop, chance = 1.0)
                .build(),
        )
        service = BossLootService(FixedProfileRegistry(profile))
        boss = ProxyFakes.proxy(LivingEntity::class.java, mapOf("getUniqueId" to UUID.randomUUID()))
    }

    private fun fakePlayer(id: UUID = UUID.randomUUID()): Player =
        ProxyFakes.proxy(Player::class.java, mapOf("getUniqueId" to id))

    @Test
    fun `a contributor claims their instance exactly once`() {
        val alice = UUID.randomUUID()
        val instance = service.onBossDeath(boss, listOf(alice))

        val first = service.claim(fakePlayer(alice), instance.id())
        assertTrue(first.successful())
        assertEquals(1, first.rewards().size)
        assertEquals(bossDrop, (first.rewards()[0].payload() as RpgItemPayload).item)

        val second = service.claim(fakePlayer(alice), instance.id())
        assertFalse(second.successful())
        assertEquals(LootClaimStatus.ALREADY_CLAIMED, second.status())
    }

    @Test
    fun `two different contributors each independently claim their own copy`() {
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()
        val instance = service.onBossDeath(boss, listOf(alice, bob))

        val aliceClaim = service.claim(fakePlayer(alice), instance.id())
        val bobClaim = service.claim(fakePlayer(bob), instance.id())

        assertTrue(aliceClaim.successful())
        assertTrue(bobClaim.successful())
        assertEquals(
            aliceClaim.rewards().map { it.id() },
            bobClaim.rewards().map { it.id() },
            "both contributors receive the same rolled rewards, independently claimable",
        )
    }

    @Test
    fun `claiming an unknown instance id reports not found`() {
        service.onBossDeath(boss, listOf(UUID.randomUUID()))

        val claim = service.claim(fakePlayer(), UUID.randomUUID())

        assertFalse(claim.successful())
        assertEquals(LootClaimStatus.NOT_FOUND, claim.status())
    }

    /** Minimal [EntityProfileRegistry] test double: always resolves to the fixed [profile]. */
    private class FixedProfileRegistry(private val profile: EntityProfile) : EntityProfileRegistry {
        override fun register(owner: String, profile: EntityProfile) {}
        override fun unregisterOwner(owner: String): Int = 0
        override fun get(key: EntityProfileKey): EntityProfile? = profile.takeIf { it.key == key }
        override fun resolve(entity: LivingEntity): EntityProfile = profile
        override fun all(): Collection<EntityProfile> = listOf(profile)
    }
}
