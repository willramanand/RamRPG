/**
 * WP-6.2: region level scaling. Builds [LevelBandService] from the packaged `content/regions/bands.conf`
 * (see docs/design/6.2-level-bands.md) and wires it into the ALREADY-CONSTRUCTED
 * [EntityProfileRegistryImpl] singleton resolved from the [ServiceContext] -- the same external-hook
 * pattern `RamRPG.kt` already uses for `mythicResolver` (`entityProfiles.mythicResolver = ...`).
 *
 * This is the ONLY seam that reaches `EntitySpawnListener`/`LootListener`'s band lookups: both are
 * constructed elsewhere (`EntitySpawnListener` directly in `RamRPG.kt`'s always-on bootstrap listeners,
 * `LootListener` in [LootModule]) and hold only an `EntityProfileRegistry` reference, so setting
 * `levelBandService` here (before any world tick / entity spawn can occur, since every module's
 * `setup()` runs during the same `enable()` call) is how they both reach the same [LevelBandService]
 * instance without B5 ever touching `RamRPG.kt` or those modules' construction sites.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.core.regions.LevelBandService
import dev.willram.ramrpg.core.services.EntityProfileRegistryImpl
import dev.willram.ramrpg.core.services.RpgServiceKeys
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDeathEvent
import java.util.logging.Logger

class MobModule(private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        val entityProfiles = ctx.service(RpgServiceKeys.ENTITY_PROFILES) as? EntityProfileRegistryImpl
            ?: run {
                // Should never happen -- RamRPG.kt always registers an EntityProfileRegistryImpl under
                // this key -- but never let region scaling silently no-op without a trace (same defensive
                // style ContentModule's ServiceContext-is-not-a-RamPlugin branch uses).
                Logger.getLogger("RamRPG")
                    .warning("MobModule: ENTITY_PROFILES is not an EntityProfileRegistryImpl; region level scaling skipped.")
                return
            }

        try {
            val levelBands = consumer.bind(LevelBandService.fromPackagedResource())
            entityProfiles.levelBandService = levelBands
            // Reclaim a mob's cached band on death. MONITOR runs after LootListener's NORMAL-priority
            // band read, so it never forces a re-resolve. Without this the non-transient band cache
            // grows unbounded over server uptime (WP-6.2 review nit). (Unload-despawn without death is a
            // documented residual — see the design note; the dominant turnover is killed mobs.)
            consumer.bind(
                Events.subscribe(EntityDeathEvent::class.java, EventPriority.MONITOR)
                    .handler { e -> levelBands.evict(e.entity) },
            )
        } catch (malformed: RuntimeException) {
            // A malformed bands.conf must not crash plugin enable -- mobs still spawn/loot unscaled
            // (levelBandService stays null) rather than taking the whole plugin down with them.
            Logger.getLogger("RamRPG").warning("MobModule: failed to load content/regions/bands.conf: ${malformed.message}")
        }
    }

    companion object {
        const val OWNER = "ramrpg-mob"
    }
}
