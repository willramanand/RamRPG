/**
 * WP-1.1b: instanced per-player boss loot -- the hook Task 1.1 of `RAMRPG_ROADMAP_PROMPT.md` asks for
 * ahead of real bosses (WP-6.4: MobDefinition + EncounterDefinition + phases). One boss kill rolls its
 * `EntityProfile.lootTable` ONCE and registers a single RamCore `LootInstance` scoped GROUP with claim
 * policy PER_PLAYER_ONCE, so every contributor can independently claim their own copy of the same
 * rolled rewards instead of racing a single ground drop (or missing out because someone else grabbed
 * it first).
 *
 * [contributors] is supplied by the caller: today [dev.willram.ramrpg.core.listeners.LootListener]
 * passes just the killer (RamRPG has no boss-damage/party-contribution tracking yet -- see
 * `XpListener`, which also only credits the killer). Contribution is still routed through RamCore's
 * [PartyContributionTracker] rather than a raw `Set`, so the eligible-set computation itself is
 * RamCore's, not reimplemented RPG state; `dev.willram.ramcore.party.PartyManager` is not used here
 * because RamRPG has no live party subsystem to source membership from yet (an open question for
 * WP-6.4 / a future party WP, not this one).
 *
 * Not pure: this depends on live Bukkit `LivingEntity`/`Player` objects (the fixed WP signature), so
 * unlike [RpgLootPayloadCodec] it is tested with `ramcore-test`'s `ProxyFakes`, not a live server.
 */
package dev.willram.ramrpg.core.loot

import dev.willram.ramcore.loot.InstancedLoot
import dev.willram.ramcore.loot.LootClaimPolicy
import dev.willram.ramcore.loot.LootClaimResult
import dev.willram.ramcore.loot.LootClaimStatus
import dev.willram.ramcore.loot.LootGenerator
import dev.willram.ramcore.loot.LootInstance
import dev.willram.ramcore.loot.LootInstanceScope
import dev.willram.ramcore.loot.LootInstanceStore
import dev.willram.ramcore.party.PartyContributionTracker
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import net.kyori.adventure.text.Component
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant
import java.util.Random
import java.util.UUID

class BossLootService(
    private val profiles: EntityProfileRegistry,
    private val store: LootInstanceStore = InstancedLoot.inMemoryStore(),
    private val expiry: Duration = DEFAULT_EXPIRY,
    private val random: Random = Random(),
) {
    private val generator = LootGenerator()

    /**
     * Rolls [entity]'s boss loot table once and registers a GROUP-scoped, PER_PLAYER_ONCE instance
     * that every id in [contributors] may independently claim. Requires the resolved
     * `EntityProfile.lootTable` to be non-null (an `isBoss` profile is expected to define one).
     */
    fun onBossDeath(entity: LivingEntity, contributors: Collection<UUID>): LootInstance {
        val profile = profiles.resolve(entity)
        val table = checkNotNull(profile?.lootTable) {
            "boss loot requested for entity ${entity.uniqueId} but its EntityProfile has no lootTable"
        }
        val context = RpgLootContexts.forKill(entity, entity.killer)
        val result = generator.generate(table, context, random)
        check(result.successful()) { "boss loot generation failed for ${table.id()}: ${result.errors()}" }

        // Eligible-set bookkeeping only (informational metadata for a future claim UI/command); claim
        // dedup itself is entirely RamCore's PER_PLAYER_ONCE policy below, never reimplemented here.
        val tracker = PartyContributionTracker()
        for (id in contributors) tracker.add(id, 1.0)
        val eligible = tracker.eligible(1.0).map(UUID::toString)

        val instance = InstancedLoot.instance(table.id(), result.rewards())
            .scope(LootInstanceScope.GROUP)
            .claimPolicy(LootClaimPolicy.PER_PLAYER_ONCE)
            .expiresAt(InstancedLoot.expiresAfter(Instant.now(), expiry))
            .metadata(CONTRIBUTORS_METADATA_KEY, eligible)
            .build()
        return store.register(instance)
    }

    /**
     * Claims [instanceId] for [player] and messages them the outcome. Folia rule 4: run this on the
     * claiming player's own context (a future `/rpg claim`-style command dispatches here already
     * anchored to the player, the same way `QuestServiceImpl.complete` sends messages inline).
     */
    fun claim(player: Player, instanceId: UUID): LootClaimResult {
        val result = store.claim(instanceId, player.uniqueId, Instant.now())
        player.sendMessage(messageFor(result.status()))
        return result
    }

    /**
     * Drops expired instances. Folia rule 4: call this from a
     * [dev.willram.ramrpg.core.platform.PlatformScheduler.repeatGlobal] timer (wired in
     * `LootModule`), never a raw Bukkit task. Pure computation -- it never touches a Bukkit object,
     * so it needs no per-player re-dispatch itself.
     */
    fun sweepExpired(now: Instant = Instant.now()): List<LootInstance> = store.sweepExpired(now)

    private fun messageFor(status: LootClaimStatus): Component = when (status) {
        LootClaimStatus.SUCCESS -> Component.translatable("ramrpg.loot.claimed")
        LootClaimStatus.ALREADY_CLAIMED -> Component.translatable("ramrpg.loot.already_claimed")
        // NOT_FOUND (unknown/removed instance id) reuses the expired message: both mean "this loot is
        // no longer available to you", and the WP only calls for three lang keys.
        LootClaimStatus.EXPIRED, LootClaimStatus.NOT_FOUND -> Component.translatable("ramrpg.loot.expired")
    }

    companion object {
        val DEFAULT_EXPIRY: Duration = Duration.ofMinutes(10)
        const val SWEEP_INTERVAL_TICKS: Long = 20L * 60L
        const val CONTRIBUTORS_METADATA_KEY: String = "contributors"
    }
}
