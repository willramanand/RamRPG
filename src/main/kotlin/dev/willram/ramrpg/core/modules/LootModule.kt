/**
 * WP-1.7b: the loot subsystem seam. Owns [LootListener] (EntityDeath -> RamCore LootGenerator ->
 * rolled RPG item drops), migrated out of `RamRPG.kt` from WP-1.1a/1.1b. Collaborators resolve from
 * the [ServiceContext]. WP-6.2 (region level scaling) modifies the LootListener class; its
 * registration stays here.
 *
 * WP-1.1b additionally constructs and binds [BossLootService] here (never `RamRPG.kt` -- B5) and
 * binds its expiry sweep as a Folia-safe repeating [dev.willram.ramrpg.core.platform.PlatformScheduler]
 * global timer (rule 4: never a raw Bukkit task).
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.core.listeners.LootListener
import dev.willram.ramrpg.core.loot.BossLootService
import dev.willram.ramrpg.core.services.RpgServiceKeys

class LootModule(private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        val profiles = ctx.service(RpgServiceKeys.ENTITY_PROFILES)
        val items = ctx.service(RpgServiceKeys.ITEM_INSTANCES)
        val defs = ctx.service(RpgServiceKeys.ITEM_DEFINITIONS)
        val platform = ctx.service(RpgServiceKeys.PLATFORM)

        val bossLoot = BossLootService(profiles)
        LootListener(profiles, items, defs, bossLoot).register(consumer)

        val sweep = platform.repeatGlobal(BossLootService.SWEEP_INTERVAL_TICKS) { bossLoot.sweepExpired() }
        consumer.bind(AutoCloseable { sweep.cancel() })
    }

    companion object {
        const val OWNER = "ramrpg-loot"
    }
}
