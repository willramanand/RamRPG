/**
 * WP-1.7b: the loot subsystem seam. Owns [LootListener] (EntityDeath -> RamCore LootGenerator ->
 * rolled RPG item drops), migrated out of `RamRPG.kt` from WP-1.1a/1.1b. Collaborators resolve from
 * the [ServiceContext]. WP-6.2 (region level scaling) modifies the LootListener class; its
 * registration stays here.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.core.listeners.LootListener
import dev.willram.ramrpg.core.services.RpgServiceKeys

class LootModule(private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        val profiles = ctx.service(RpgServiceKeys.ENTITY_PROFILES)
        val items = ctx.service(RpgServiceKeys.ITEM_INSTANCES)
        val defs = ctx.service(RpgServiceKeys.ITEM_DEFINITIONS)
        LootListener(profiles, items, defs).register(consumer)
    }

    companion object {
        const val OWNER = "ramrpg-loot"
    }
}
