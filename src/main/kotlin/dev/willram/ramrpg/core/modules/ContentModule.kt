/**
 * WP-1.5a: the content subsystem seam (reserved empty by WP-1.7b). Wires the HOCON authoring path:
 * [RpgContentLoader] reads `content/<type>/` into pure specs, and [ContentRegistrarRpg] registers them
 * into the RPG registries this module resolves from its [ServiceContext]. This is an ALTERNATIVE
 * authoring path alongside the Kotlin builtin registrations (which run earlier in `enable()`);
 * WP-1.5d later moves the builtins themselves to `.conf`.
 *
 * Folia-safe (rule 4): RamCore's loader does blocking file I/O, so the load runs off the main thread
 * via [dev.willram.ramrpg.core.platform.PlatformScheduler.runAsync]; the registry mutations are then
 * applied back on the global thread. All collaborators come from the service registry -- nothing is
 * added to `RamRPG.kt` (B5).
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.RamPlugin
import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.core.config.ContentRegistrarRpg
import dev.willram.ramrpg.core.config.RpgContentLoadResult
import dev.willram.ramrpg.core.config.RpgContentLoader
import dev.willram.ramrpg.core.config.specs.EffectSpec
import dev.willram.ramrpg.core.effects.BlockMatcherRegistry
import dev.willram.ramrpg.core.effects.BuiltinEffectActions
import dev.willram.ramrpg.core.effects.EffectActionRegistry
import dev.willram.ramrpg.core.effects.EffectConditionRegistry
import dev.willram.ramrpg.core.effects.TriggeredEffectDispatcher
import dev.willram.ramrpg.core.services.RpgServiceKeys
import java.io.File

class ContentModule(private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        val platform = ctx.service(RpgServiceKeys.PLATFORM)

        // WP-1.5b: the three effect registries, populated with the builtin ids (data, not `when`s), then
        // wired to the effect parser. Built here so both the loader (which resolves item/enchant effect
        // bundles) and the dispatcher share one set of registrations.
        val actions = EffectActionRegistry()
        val conditions = EffectConditionRegistry()
        val matchers = BlockMatcherRegistry()
        BuiltinEffectActions.registerAll(actions, conditions, matchers, platform)
        val effectRegistries = EffectSpec.Registries(actions, conditions, matchers)

        // WP-1.5b: the TriggeredEffect dispatcher. It owns Bukkit event subscriptions + a repeating tick
        // task, so it MUST bind through this module's TerminableConsumer (RamCore tears them down on
        // disable) and never through RamRPG.kt (B5). It starts empty; later WPs register holder effects.
        TriggeredEffectDispatcher(platform).bind(consumer)

        val registrar = ContentRegistrarRpg(
            stats = ctx.service(RpgServiceKeys.STATS),
            items = ctx.service(RpgServiceKeys.ITEM_DEFINITIONS),
            skills = ctx.service(RpgServiceKeys.SKILL_REGISTRY),
            enchants = ctx.service(RpgServiceKeys.ENCHANTMENTS),
            entities = ctx.service(RpgServiceKeys.ENTITY_PROFILES),
            reforges = ctx.service(RpgServiceKeys.REFORGES),
            gems = ctx.service(RpgServiceKeys.GEMS),
        )

        // The ServiceContext at runtime IS the RamRPG plugin (RpgModules.all(this) passes it, and
        // RamPlugin implements ServiceContext). We need its data folder + logger, which ServiceContext
        // does not expose; a guarded cast is the only seam-preserving way to reach them without editing
        // RamRPG.kt (B5) or RpgModules.
        val plugin = ctx as? RamPlugin ?: run {
            // Should never happen (RpgModules.all(this) passes the plugin), but never disable content
            // loading silently: leave a trace if the ServiceContext is ever not the plugin.
            java.util.logging.Logger.getLogger("RamRPG")
                .warning("ContentModule: ServiceContext is not a RamPlugin; HOCON content/ loading skipped.")
            return
        }
        val contentDir = File(plugin.dataFolder, "content")
        if (!contentDir.exists()) contentDir.mkdirs()

        // Load off the main thread (blocking file I/O), then apply registrations on the global thread
        // where the RPG registries expect their mutations.
        platform.runAsync {
            val result: RpgContentLoadResult = RpgContentLoader.load(contentDir.toPath(), effectRegistries)
            platform.runGlobal {
                val errors = result.errors() + registrar.registerAll(result, OWNER)
                if (errors.isEmpty()) {
                    val loaded = result.definitions().size
                    if (loaded > 0) {
                        plugin.log("<green>Content: loaded <yellow>$loaded<green> definition(s) from content/")
                    }
                } else {
                    plugin.log("<red>Content: <yellow>${errors.size}<red> error(s) while loading content/:")
                    for (error in errors) plugin.log("<red> - <gray>${error.describe()}")
                }
            }
        }
    }

    companion object {
        const val OWNER = "ramrpg-content"
    }
}
