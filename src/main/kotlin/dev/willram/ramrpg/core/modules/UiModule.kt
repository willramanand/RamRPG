/**
 * WP-1.7b: the UI subsystem seam. Owns the per-player HUD holders [ActionBarUi] and [BossBarUi],
 * previously private `lateinit` fields on the plugin. Each is bound to the terminable consumer both
 * as an event subscriber (its join/quit subscriptions) and as an [AutoCloseable] (its per-player
 * scheduler tasks), so RamCore tears them down on disable -- no manual `shutdown()` in `disable()`.
 *
 * BossBarUi observes skill-XP gains through [SkillServiceImpl.addXpGainListener] rather than a plugin
 * callback. The stat/skill GUIs ([dev.willram.ramrpg.core.listeners.StatsGui],
 * [dev.willram.ramrpg.core.listeners.SkillsGui]) are stateless objects opened on demand by the
 * `/skills` command; they need no registration of their own.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.core.listeners.ActionBarUi
import dev.willram.ramrpg.core.listeners.BossBarUi
import dev.willram.ramrpg.core.listeners.ItemRequirementServices
import dev.willram.ramrpg.core.listeners.requirementStateFor
import dev.willram.ramrpg.core.rendering.PacketItemRendererImpl
import dev.willram.ramrpg.core.services.RpgServiceKeys
import dev.willram.ramrpg.core.services.SkillServiceImpl

class UiModule(private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        val stats = ctx.service(RpgServiceKeys.STATS)
        val playerStore = ctx.service(RpgServiceKeys.PLAYER_STORE)
        val platform = ctx.service(RpgServiceKeys.PLATFORM)
        val skillService = ctx.service(RpgServiceKeys.SKILL_SERVICE)
        val skillRegistry = ctx.service(RpgServiceKeys.SKILL_REGISTRY)

        val actionBar = consumer.bind(ActionBarUi(stats, playerStore, platform))
        actionBar.register(consumer)

        val bossBar = consumer.bind(BossBarUi(skillService, skillRegistry, playerStore))
        bossBar.register(consumer)
        (skillService as? SkillServiceImpl)?.addXpGainListener(bossBar::onXpGain)

        // WP-lore: surface per-viewer requirement lore (met green / unmet red) in the packet renderer.
        // The renderer is constructed in RamRPG.load() before these services exist (B5: RamRPG.kt is
        // frozen), so it is wired HERE via a settable hook -- the module-injected-hook pattern
        // EntityProfileRegistryImpl.levelBandService established. requirementStateFor is the SAME
        // no-self-satisfaction path WP-2.1c's StatProviders use, so a lore line agrees with whether the
        // item is actually inert for the viewer.
        (ctx.service(RpgServiceKeys.RENDERER) as? PacketItemRendererImpl)?.let { renderer ->
            val requirementServices = ItemRequirementServices(skillRegistry, skillService, stats)
            renderer.requirementStateHook = { viewer -> requirementStateFor(viewer, requirementServices) }
            renderer.skillNameLookup = { key -> skillRegistry.get(key)?.displayName }
        }
    }

    companion object {
        const val OWNER = "ramrpg-ui"
    }
}
