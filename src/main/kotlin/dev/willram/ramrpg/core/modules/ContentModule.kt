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
 *
 * WP-1.5c extends this seam with the `/rpg validate` and `/rpg reload` capabilities:
 *  - [validate] loads a directory off-thread with the SAME loader + effect registries as the real
 *    pipeline and reports every [ValidationError] found. NO side effects: it never touches a registry,
 *    the renderer cache, or [snapshot].
 *  - [reload] loads `content/` off-thread, diffs it against [snapshot] using RamCore's [ContentDiff] /
 *    [ContentSnapshot] / [ContentHashing] (never hand-rolled -- rule 1), then -- unless `dryRun` --
 *    unregisters owner [OWNER] from EVERY registry this content pack touches (items, skills, enchants,
 *    entities, reforges, gems: all SIX, not a subset) and re-registers, invalidates the
 *    [PacketItemRenderer] cache, and marks every online player's stats dirty (`WORLD_CHANGED`).
 *  - Stat DEFINITIONS are the one thing this pack loads that reload deliberately does NOT touch:
 *    [dev.willram.ramrpg.api.stats.StatService.registerDefinition] takes no owner, so a HOCON stat
 *    cannot be owner-unregistered -- see docs/design/1.5c-reload-semantics.md for the full decision.
 *  - The `/rpg` command tree ([RpgCommand]) is registered here, directly through Paper's
 *    `LifecycleEvents.COMMANDS`, the same lifecycle hook `RamPlugin#onEnable` uses for
 *    `RamRPG.registerCommands` -- Paper allows multiple handlers for that event, so this reaches the
 *    server without ever touching `RamRPG.kt` (B5), exactly like the loader wiring above.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.RamPlugin
import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.content.ContentLoader as CoreContentLoader
import dev.willram.ramcore.exception.ValidationError
import dev.willram.ramcore.reload.ContentDiff
import dev.willram.ramcore.reload.ContentSnapshot
import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.StatDirtyReason
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.commands.RpgCommand
import dev.willram.ramrpg.core.config.ContentRegistrarRpg
import dev.willram.ramrpg.core.config.RpgContentLoadResult
import dev.willram.ramrpg.core.config.RpgContentLoader
import dev.willram.ramrpg.core.config.specs.EffectSpec
import dev.willram.ramrpg.core.effects.BlockMatcherRegistry
import dev.willram.ramrpg.core.effects.BuiltinEffectActions
import dev.willram.ramrpg.core.effects.EffectActionRegistry
import dev.willram.ramrpg.core.effects.EffectConditionRegistry
import dev.willram.ramrpg.core.effects.TriggeredEffectDispatcher
import dev.willram.ramrpg.core.platform.PlatformScheduler
import dev.willram.ramrpg.core.rendering.PacketItemRenderer
import dev.willram.ramrpg.core.services.RpgServiceKeys
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Bukkit
import java.io.File
import java.nio.file.Path

@Suppress("UnstableApiUsage")
class ContentModule(private val ctx: ServiceContext) : TerminableModule {

    private lateinit var platform: PlatformScheduler
    private lateinit var effectRegistries: EffectSpec.Registries
    private lateinit var registrar: ContentRegistrarRpg
    private lateinit var items: ItemDefinitionRegistry
    private lateinit var skills: SkillRegistry
    private lateinit var enchants: EnchantmentRegistry
    private lateinit var entities: EntityProfileRegistry
    private lateinit var reforges: ReforgeRegistry
    private lateinit var gems: GemRegistry
    private lateinit var stats: StatService
    private lateinit var renderer: PacketItemRenderer
    private lateinit var contentDir: File

    /**
     * The diff baseline for the NEXT `/rpg reload`: what was live as of the last successful load
     * (startup or a non-dry-run reload). Read AND written only from inside a `platform.runGlobal` block
     * (never from the async load), so -- like every other registry mutation in this module -- it never
     * needs its own synchronization: the global scheduler already serializes access.
     */
    private var snapshot: ContentSnapshot = ContentSnapshot.empty()

    /**
     * The content TYPE (directory name) of every id in [snapshot], as of the same load. [ContentSnapshot]
     * only carries id -> hash, so this side table is what lets [reload] report when a REMOVED id was a
     * `stats/` definition -- the one case `/rpg reload` cannot clean up (see [applyToRegistries]'s doc
     * and docs/design/1.5c-reload-semantics.md). Kept in lockstep with [snapshot]: written in the same
     * `runGlobal` blocks, never read off the global thread.
     */
    private var snapshotTypes: Map<ContentId, String> = emptyMap()

    override fun setup(consumer: TerminableConsumer) {
        platform = ctx.service(RpgServiceKeys.PLATFORM)

        // WP-1.5b: the three effect registries, populated with the builtin ids (data, not `when`s), then
        // wired to the effect parser. Built here so both the loader (which resolves item/enchant effect
        // bundles) and the dispatcher share one set of registrations.
        val actions = EffectActionRegistry()
        val conditions = EffectConditionRegistry()
        val matchers = BlockMatcherRegistry()
        BuiltinEffectActions.registerAll(actions, conditions, matchers, platform)
        effectRegistries = EffectSpec.Registries(actions, conditions, matchers)

        // WP-1.5b: the TriggeredEffect dispatcher. It owns Bukkit event subscriptions + a repeating tick
        // task, so it MUST bind through this module's TerminableConsumer (RamCore tears them down on
        // disable) and never through RamRPG.kt (B5). It starts empty; later WPs register holder effects.
        TriggeredEffectDispatcher(platform).bind(consumer)

        items = ctx.service(RpgServiceKeys.ITEM_DEFINITIONS)
        skills = ctx.service(RpgServiceKeys.SKILL_REGISTRY)
        enchants = ctx.service(RpgServiceKeys.ENCHANTMENTS)
        entities = ctx.service(RpgServiceKeys.ENTITY_PROFILES)
        reforges = ctx.service(RpgServiceKeys.REFORGES)
        gems = ctx.service(RpgServiceKeys.GEMS)
        stats = ctx.service(RpgServiceKeys.STATS)
        renderer = ctx.service(RpgServiceKeys.RENDERER)

        registrar = ContentRegistrarRpg(
            stats = stats,
            items = items,
            skills = skills,
            enchants = enchants,
            entities = entities,
            reforges = reforges,
            gems = gems,
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
        contentDir = File(plugin.dataFolder, "content")
        if (!contentDir.exists()) contentDir.mkdirs()

        // Load off the main thread (blocking file I/O), then apply registrations on the global thread
        // where the RPG registries expect their mutations.
        platform.runAsync {
            val result: RpgContentLoadResult = RpgContentLoader.load(contentDir.toPath(), effectRegistries)
            // WP-1.5c: also load through RamCore's raw loader so the FIRST `/rpg reload` has a real
            // baseline to diff against (what startup actually loaded), not an empty snapshot. Off-thread,
            // same as the typed load above.
            val raw = CoreContentLoader.load(contentDir.toPath())
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
                snapshot = ContentSnapshot.of(raw)
                snapshotTypes = raw.definitions().associate { it.id() to it.type() }
            }
        }

        // WP-1.5c: /rpg validate + /rpg reload. Registered directly through Paper's own command
        // lifecycle event -- the same one RamPlugin#onEnable subscribes to call RamRPG.registerCommands
        // -- rather than through that override, so this command reaches the server without ever adding
        // a line to RamRPG.kt (B5). Paper's LifecycleEventManager supports more than one COMMANDS
        // handler; this one fires alongside RamRPG's.
        val rpgCommand = RpgCommand(
            defaultDir = contentDir.toPath(),
            validate = ::validate,
            reload = ::reload,
        )
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            rpgCommand.register(event.registrar())
        }
    }

    /**
     * `/rpg validate [dir]`: loads [dir] off-thread with the same loader + effect registries the real
     * pipeline uses, and hands every [ValidationError] found to [onResult]. NO side effects -- registries,
     * the renderer cache and the reload baseline ([snapshot]) are all left untouched, so this is safe to
     * run repeatedly against arbitrary directories before committing a `/rpg reload`.
     */
    private fun validate(dir: Path, onResult: (List<ValidationError>) -> Unit) {
        platform.runAsync {
            val result = RpgContentLoader.load(dir, effectRegistries)
            onResult(result.errors())
        }
    }

    /**
     * `/rpg reload [--dry-run]`: loads the plugin's `content/` directory off-thread (both the raw
     * RamCore load, for the diff, and the typed [RpgContentLoader] load, for registration), then --
     * unless [dryRun] -- applies [applyToRegistries] and the renderer/stats side effects on the global
     * thread (Folia-safe), and advances [snapshot] so the NEXT reload diffs against what is now live. A
     * dry run computes and reports the same [ContentDiff] without touching anything.
     */
    private fun reload(dryRun: Boolean, onResult: (ReloadOutcome) -> Unit) {
        val dir = contentDir.toPath()
        platform.runAsync {
            val raw = CoreContentLoader.load(dir)
            val rpgResult = RpgContentLoader.load(dir, effectRegistries)
            platform.runGlobal {
                // snapshot/snapshotTypes are only ever read/written from inside a runGlobal block.
                val next = ContentSnapshot.of(raw)
                val diff = ContentDiff.data(snapshot, next, raw)
                // The one thing reload cannot clean up: a stat definition whose file was deleted. Looked
                // up against the OLD type table (snapshotTypes), since a removed id has no entry in the
                // new load (raw) to ask. See applyToRegistries's doc + docs/design/1.5c-reload-semantics.md.
                val statsOrphaned = diff.removed().count { id -> snapshotTypes[id] == RpgContentLoader.TYPE_STATS }

                val registrationErrors: List<ValidationError> = if (dryRun) {
                    emptyList()
                } else {
                    val errs = applyToRegistries(registrar, rpgResult, items, skills, enchants, entities, reforges, gems)
                    renderer.invalidate()
                    for (player in Bukkit.getOnlinePlayers()) stats.markDirty(player, StatDirtyReason.WORLD_CHANGED)
                    snapshot = next
                    snapshotTypes = raw.definitions().associate { it.id() to it.type() }
                    errs
                }

                onResult(
                    ReloadOutcome(
                        diff = diff,
                        loadErrors = rpgResult.errors(),
                        registrationErrors = registrationErrors,
                        applied = !dryRun,
                        statsOrphaned = statsOrphaned,
                    ),
                )
            }
        }
    }

    companion object {
        const val OWNER = "ramrpg-content"

        /**
         * Unregisters [owner] from EVERY OWNED registry the HOCON content pack touches -- items, skills,
         * enchants, entities, reforges, gems; all SIX, not a subset (the WP-1.5a review flagged that the
         * pre-existing `ramrpg-override` reload path in RamRPG.kt only unregisters a subset) -- then
         * re-registers everything in [result] under [owner] via [registrar]. Stat DEFINITIONS are
         * deliberately not in this parameter list: [dev.willram.ramrpg.api.stats.StatService] has no
         * `unregisterOwner`, so they cannot be scoped/removed here. [ContentRegistrarRpg.registerAll]
         * (which every content type shares -- rule 1 forbids forking a parallel stat registry just for
         * reload) still re-registers `result.stats` as part of this call; that upsert is safe (RamCore's
         * `StatService.registerDefinition` replaces by key, never throws, never duplicates), so ADDED and
         * CHANGED stat definitions genuinely take effect. What this cannot do is REMOVE one: a stat
         * whose file disappears from `content/stats/` keeps its last-registered definition live until the
         * server restarts. See docs/design/1.5c-reload-semantics.md for the full decision.
         *
         * Pure registry mutation with no Bukkit/threading dependency of its own, so
         * `ReloadUnregisterOwnerTest` calls this directly against fakes.
         */
        fun applyToRegistries(
            registrar: ContentRegistrarRpg,
            result: RpgContentLoadResult,
            items: ItemDefinitionRegistry,
            skills: SkillRegistry,
            enchants: EnchantmentRegistry,
            entities: EntityProfileRegistry,
            reforges: ReforgeRegistry,
            gems: GemRegistry,
            owner: String = OWNER,
        ): List<ValidationError> {
            items.unregisterOwner(owner)
            skills.unregisterOwner(owner)
            enchants.unregisterOwner(owner)
            entities.unregisterOwner(owner)
            reforges.unregisterOwner(owner)
            gems.unregisterOwner(owner)
            return registrar.registerAll(result, owner)
        }

        /**
         * `file:path: message` per [ValidationError] -- the WP-1.5c operator-facing contract for
         * `/rpg validate` and `/rpg reload`. Distinct from [ValidationError.describe]'s
         * `[file] path: message` (RamCore's own diagnostics-log form, e.g. the startup log above).
         *
         * Pure string formatting -- no reimplemented validation -- so `ValidateResultFormattingTest`
         * exercises it directly.
         */
        fun formatValidationError(error: ValidationError): String {
            val file = error.source() ?: "?"
            val path = error.path()
            return if (path.isNullOrEmpty()) "$file: ${error.message()}" else "$file:$path: ${error.message()}"
        }
    }
}

/**
 * The result of one `/rpg reload` (or `--dry-run`): the RamCore [ContentDiff], every [ValidationError]
 * from the load and (when [applied]) registration, whether it actually applied, and [statsOrphaned] --
 * how many `stats/` definitions in [ContentDiff.removed] are still live because
 * [dev.willram.ramrpg.api.stats.StatService] has no owner-scoped unregister (see
 * docs/design/1.5c-reload-semantics.md). Added/changed stat definitions are NOT counted here: those
 * apply immediately via [ContentRegistrarRpg]'s upsert, same as every other content type.
 */
data class ReloadOutcome(
    val diff: ContentDiff,
    val loadErrors: List<ValidationError>,
    val registrationErrors: List<ValidationError>,
    val applied: Boolean,
    val statsOrphaned: Int,
)
