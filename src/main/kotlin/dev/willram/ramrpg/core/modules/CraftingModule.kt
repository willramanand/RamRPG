/**
 * WP-3.1b: populates the seam WP-1.7b reserved for the crafting engine. The two owner-scoped registries
 * ([dev.willram.ramrpg.core.services.RecipeRegistryImpl] / [dev.willram.ramrpg.core.services.StationRegistryImpl])
 * and the generic [dev.willram.ramrpg.core.services.CraftingServiceImpl] are constructed and registered
 * at load()-time in [dev.willram.ramrpg.RamRPG] (the orchestrator's merge-time B5 promotion -- RamCore's
 * ServiceRegistry refuses registration once `loadAll()` has run, so an enable()-time module cannot
 * register them, yet the content pipeline + WP-3.1c/3.3a/3.3c/3.3d resolve them via `ctx.service(...)`).
 * This module therefore only RESOLVES them and binds the enable()-time wiring:
 *
 *  - [dev.willram.ramrpg.core.listeners.StationBlockListener] (right-click a station block -> open the
 *    station menu, Folia-safe);
 *  - a [PlayerQuitEvent] handler that returns any mid-craft escrowed inputs (RamCore's `MenuSession`
 *    invalidates on quit without firing its close handler, so the menu's own return-on-close is not
 *    enough -- see [dev.willram.ramrpg.core.menus.StationMenu]).
 *
 * RamRPG.kt is otherwise untouched by the WP itself (B5); the load()-time trio construction is the
 * single documented orchestrator wiring exception. Recipes/stations still need
 * `ContentLoader.KNOWN_TYPES` + a `recipes`/`stations` registrar entry before any content reaches the
 * registries at runtime (tracked separately).
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.api.crafting.StationRegistry
import dev.willram.ramrpg.core.listeners.StationBlockListener
import dev.willram.ramrpg.core.services.CraftingServiceImpl
import dev.willram.ramrpg.core.services.RpgServiceKeys
import org.bukkit.event.player.PlayerQuitEvent

class CraftingModule(private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        val stations: StationRegistry = ctx.service(RpgServiceKeys.STATION_REGISTRY)
        // Resolved as the concrete impl: returnHeldOnQuit is a module-owned lifecycle hook, not part of
        // the CraftingService interface the key is typed as.
        val crafting = ctx.service(RpgServiceKeys.CRAFTING_SERVICE) as CraftingServiceImpl

        StationBlockListener(stations, crafting).register(consumer)

        // Return-on-quit: RamCore's MenuSession invalidate(false) on quit skips its close handler, so the
        // escrowed inputs must be returned from here or a disconnect would eat them.
        consumer.bind(
            Events.subscribe(PlayerQuitEvent::class.java).handler { e -> crafting.returnHeldOnQuit(e.player) },
        )
    }

    companion object {
        const val OWNER = "ramrpg-crafting"
    }
}
