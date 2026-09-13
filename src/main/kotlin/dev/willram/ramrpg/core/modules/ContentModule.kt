/**
 * WP-1.7b: seam reserved for WP-1.5a (ContentLoader core). Seeded empty and bound now so that WP-1.5a adds its
 * registrations here, never in `RamRPG.kt`. No behavior yet -- the constructor takes the
 * [ServiceContext] the future WP resolves its collaborators from.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule

class ContentModule(@Suppress("unused") private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        // Reserved for WP-1.5a. Intentionally empty in WP-1.7b.
    }

    companion object {
        const val OWNER = "ramrpg-content"
    }
}
