/**
 * WP-3.1b: populates the seam WP-1.7b reserved for the crafting engine. Everything the crafting
 * subsystem needs is constructed HERE from the [ServiceContext] and bound through the module's
 * [TerminableConsumer], so `RamRPG.kt` is never touched (B5):
 *
 *  - the two owner-scoped registries ([RecipeRegistryImpl] / [StationRegistryImpl]) and the generic
 *    [CraftingServiceImpl] (which applies ANY [dev.willram.ramrpg.api.crafting.RecipeOutcome] via 3.1a's
 *    pure [dev.willram.ramrpg.api.crafting.OutcomePlan] -- so WP-3.3a/3.3c/3.3d add content only);
 *  - [StationBlockListener] (right-click a station block -> open the station menu, Folia-safe);
 *  - a [PlayerQuitEvent] handler that returns any mid-craft escrowed inputs (RamCore's [MenuSession]
 *    invalidates on quit without firing its close handler, so the menu's own return-on-close is not
 *    enough -- see [dev.willram.ramrpg.core.menus.StationMenu]).
 *
 * SERVICE-KEY / CONTENT-PIPELINE GAP (reported). These three services are constructed during `enable()`
 * (this `setup()`), i.e. AFTER `load()`, and RamCore's ServiceRegistry refuses registration once
 * `loadAll()` has run -- so, exactly like WP-5.3's `SetRegistry`, they are wired LOCALLY here and are NOT
 * registered under [RpgServiceKeys.RECIPE_REGISTRY]/[RpgServiceKeys.STATION_REGISTRY]/[RpgServiceKeys.CRAFTING_SERVICE]
 * (those keys are declared for the orchestrator's merge-time promotion into `load()`; see their KDoc).
 * Consequently NOTHING can register recipes/stations from `content/` yet: that needs
 * `ContentLoader.KNOWN_TYPES` + a `recipes`/`stations` registrar entry (out of this WP's file scope) AND
 * the load()-time service wiring above. The engine + registries + generic outcome application are the
 * deliverable; the content-pipeline wiring is tracked separately (WP-3.1b report).
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.listeners.EconomyService
import dev.willram.ramrpg.core.listeners.ItemRequirementServices
import dev.willram.ramrpg.core.listeners.StationBlockListener
import dev.willram.ramrpg.core.rendering.PacketItemRenderer
import dev.willram.ramrpg.core.services.CraftingServiceImpl
import dev.willram.ramrpg.core.services.RecipeRegistryImpl
import dev.willram.ramrpg.core.services.RpgServiceKeys
import dev.willram.ramrpg.core.services.StationRegistryImpl
import org.bukkit.event.player.PlayerQuitEvent

class CraftingModule(private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        val itemDefs: ItemDefinitionRegistry = ctx.service(RpgServiceKeys.ITEM_DEFINITIONS)
        val itemInstances: ItemInstanceService = ctx.service(RpgServiceKeys.ITEM_INSTANCES)
        val economy: EconomyService = ctx.service(RpgServiceKeys.ECONOMY)
        val skillService: SkillService = ctx.service(RpgServiceKeys.SKILL_SERVICE)
        val skillRegistry: SkillRegistry = ctx.service(RpgServiceKeys.SKILL_REGISTRY)
        val stats: StatService = ctx.service(RpgServiceKeys.STATS)
        val renderer: PacketItemRenderer = ctx.service(RpgServiceKeys.RENDERER)

        // A real ItemRequirementServices (new registration site this WP owns, like SetModule) so recipe
        // requirement gating never runs fail-closed for a wiring gap.
        val requirementServices = ItemRequirementServices(skillRegistry, skillService, stats)

        val recipes = RecipeRegistryImpl()
        val stations = StationRegistryImpl()
        val crafting = CraftingServiceImpl(
            itemDefs = itemDefs,
            itemInstances = itemInstances,
            recipes = recipes,
            economy = economy,
            skillService = skillService,
            renderer = renderer,
            requirementServices = requirementServices,
        )

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
