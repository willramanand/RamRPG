/**
 * WP-1.7b: the economy subsystem seam. [dev.willram.ramrpg.core.listeners.EconomyService] itself is a
 * `load()`-time service (registered under [RpgServiceKeys.ECONOMY]), so there is no enable()-time
 * construction to migrate here today; this module resolves it to anchor the seam and is where future
 * economy-facing lifecycle (e.g. vendor money flows) attaches without touching `RamRPG.kt`.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.core.services.RpgServiceKeys

class EconomyModule(private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        // Resolve the economy service so a missing registration fails loudly here rather than later.
        ctx.service(RpgServiceKeys.ECONOMY)
    }

    companion object {
        const val OWNER = "ramrpg-economy"
    }
}
