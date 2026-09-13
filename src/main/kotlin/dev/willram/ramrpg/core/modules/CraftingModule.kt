/**
 * WP-1.7b: seam reserved for WP-3.1b (Station and Recipe API). Seeded empty and bound now so that WP-3.1b adds its
 * registrations here, never in `RamRPG.kt`. No behavior yet -- the constructor takes the
 * [ServiceContext] the future WP resolves its collaborators from.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule

class CraftingModule(@Suppress("unused") private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        // Reserved for WP-3.1b. Intentionally empty in WP-1.7b.
    }

    companion object {
        const val OWNER = "ramrpg-crafting"
    }
}
