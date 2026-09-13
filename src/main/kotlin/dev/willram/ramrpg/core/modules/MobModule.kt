/**
 * WP-1.7b: seam reserved for WP-6.2 (region level scaling and custom mobs). Seeded empty and bound now so that WP-6.2 adds its
 * registrations here, never in `RamRPG.kt`. No behavior yet -- the constructor takes the
 * [ServiceContext] the future WP resolves its collaborators from.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule

class MobModule(@Suppress("unused") private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        // Reserved for WP-6.2. Intentionally empty in WP-1.7b.
    }

    companion object {
        const val OWNER = "ramrpg-mob"
    }
}
