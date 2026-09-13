/**
 * WP-1.5a: the RamRPG content loader. It is a thin RPG-semantics layer *over* RamCore's
 * [dev.willram.ramcore.content.ContentLoader] / [dev.willram.ramcore.content.SpecLoader] pipeline --
 * it never re-implements HOCON parsing, `extends:` inheritance, duplicate/cycle detection, or error
 * aggregation. RamCore owns that plumbing; RamRPG owns only the mapping from a merged node to a pure
 * RPG spec (one [ContentDeserializer] per content type), plus one RamRPG-specific policy RamCore
 * deliberately leaves open: an unknown content directory is an error here (see [load]).
 *
 * The directory name is the content type: `items/`, `enchants/`, `entities/`, `reforges/`, `gems/`,
 * `skills/`, `stats/`, `sets/` (WP-5.3). Every error carries its [dev.willram.ramcore.content.SourceRef]
 * (file + path) and errors AGGREGATE -- three broken files yield three [ValidationError]s, never a
 * thrown exception. Unlike the other six, `sets/` is parsed here but registered by `SetModule`, not
 * [ContentRegistrarRpg] -- see [RpgContentLoadResult.sets]'s KDoc.
 *
 * Spec types stay PURE (no Bukkit) so they unit-test off-server; the single Bukkit dependency an item
 * carries (its `Material`) is resolved by [ContentRegistrarRpg] on the server, not here. This whole
 * type is Bukkit-free and does blocking file I/O only through RamCore's loader, so callers must run
 * [load] off the main thread (see [dev.willram.ramrpg.core.modules.ContentModule]).
 */
package dev.willram.ramrpg.core.config

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentDeserializer
import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.content.ContentLoadResult
import dev.willram.ramcore.content.ContentLoader
import dev.willram.ramcore.content.SourceRef
import dev.willram.ramcore.content.SpecLoadResult
import dev.willram.ramcore.content.SpecLoader
import dev.willram.ramcore.exception.ValidationError
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.core.config.specs.EffectSpec
import dev.willram.ramrpg.core.config.specs.EnchantSpec
import dev.willram.ramrpg.core.config.specs.EntityProfileSpec
import dev.willram.ramrpg.core.config.specs.GemSpec
import dev.willram.ramrpg.core.config.specs.ItemSpec
import dev.willram.ramrpg.core.config.specs.RecipeSpec
import dev.willram.ramrpg.core.config.specs.ReforgeSpec
import dev.willram.ramrpg.core.config.specs.SetSpec
import dev.willram.ramrpg.core.config.specs.SkillSpec
import dev.willram.ramrpg.core.config.specs.StationSpec
import dev.willram.ramrpg.core.config.specs.StatSpec
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path

/** Marker for every RPG content spec produced by [RpgContentLoader]; carries its parsed id. */
interface RpgContentSpec {
    val id: ContentId
}

/**
 * The outcome of one [RpgContentLoader.load]: the typed specs that loaded cleanly, grouped by type,
 * plus every [ValidationError] found across parsing, deserialization, and unknown-type detection. Like
 * RamCore's own result types, it never throws -- inspect [errors].
 */
class RpgContentLoadResult internal constructor(
    val stats: List<StatSpec>,
    val items: List<ItemSpec>,
    val skills: List<SkillSpec>,
    val enchants: List<EnchantSpec>,
    val entities: List<EntityProfileSpec>,
    val reforges: List<ReforgeSpec>,
    val gems: List<GemSpec>,
    /** WP-5.3: parsed `sets/` entries. See [ContentRegistrarRpg] note -- unlike the six types above,
     *  registering these into a live registry is `SetModule`'s job, not `ContentRegistrarRpg`'s. */
    val sets: List<SetSpec> = emptyList(),
    /** WP-3.1d: parsed `recipes/` entries; the registrar resolves each into a live
     *  [dev.willram.ramrpg.api.crafting.Recipe] and registers it into the RecipeRegistry. */
    val recipes: List<RecipeSpec> = emptyList(),
    /** WP-3.1d: parsed `stations/` entries; the registrar resolves each into a live
     *  [dev.willram.ramrpg.api.crafting.Station] and registers it into the StationRegistry. */
    val stations: List<StationSpec> = emptyList(),
    private val errorList: List<ValidationError>,
    private val sources: Map<ContentId, SourceRef> = emptyMap(),
) {
    /** Every spec that loaded cleanly, across all types, in a single flat list. */
    fun definitions(): List<RpgContentSpec> =
        ArrayList<RpgContentSpec>(stats.size + items.size + skills.size + enchants.size +
            entities.size + reforges.size + gems.size + sets.size + recipes.size + stations.size).apply {
            addAll(stats); addAll(items); addAll(skills); addAll(enchants)
            addAll(entities); addAll(reforges); addAll(gems); addAll(sets)
            addAll(recipes); addAll(stations)
        }

    /** Every error, each with a [dev.willram.ramcore.content.SourceRef] (file + path). */
    fun errors(): List<ValidationError> = errorList

    /** The [SourceRef] (file + path) a loaded definition came from, for registration-time errors. */
    fun sourceOf(id: ContentId): SourceRef? = sources[id]

    fun successful(): Boolean = errorList.isEmpty()
}

/**
 * Loads files under `content/<type>/` (`.conf`, `.yml`, `.yaml`) into pure RPG spec types.
 *
 * The load is stateless; [load] is safe to call repeatedly (for `/rpg reload`, WP-1.5c).
 */
object RpgContentLoader {

    const val TYPE_STATS = "stats"
    const val TYPE_ITEMS = "items"
    const val TYPE_SKILLS = "skills"
    const val TYPE_ENCHANTS = "enchants"
    const val TYPE_ENTITIES = "entities"
    const val TYPE_REFORGES = "reforges"
    const val TYPE_GEMS = "gems"
    /** WP-5.3: armor set definitions -- parsed here (mirroring items/enchants), registered by `SetModule`. */
    const val TYPE_SETS = "sets"
    /** WP-3.1d: crafting recipes -- parsed here, resolved + registered into the RecipeRegistry by [ContentRegistrarRpg]. */
    const val TYPE_RECIPES = "recipes"
    /** WP-3.1d: crafting stations -- parsed here, resolved + registered into the StationRegistry by [ContentRegistrarRpg]. */
    const val TYPE_STATIONS = "stations"

    /** The directory names this loader recognises. A directory not in this set is an error. */
    val KNOWN_TYPES: Set<String> = linkedSetOf(
        TYPE_STATS, TYPE_ITEMS, TYPE_SKILLS, TYPE_ENCHANTS, TYPE_ENTITIES, TYPE_REFORGES, TYPE_GEMS, TYPE_SETS,
        TYPE_RECIPES, TYPE_STATIONS,
    )

    /**
     * Loads and deserializes all content under [root].
     *
     * Uses RamCore's [ContentLoader] to parse + merge (`extends:`), then RamCore's [SpecLoader] to run
     * one [ContentDeserializer] per type with source-tagged error aggregation. RamCore's SpecLoader
     * intentionally *ignores* a directory with no registered deserializer (so a project can load only
     * the types it cares about); RamRPG instead treats such a directory as authoring error, so the
     * final error list = (parse + inheritance + deserialize errors from RamCore) + (unknown-type
     * errors added here). Never throws.
     *
     * WP-1.5b: items and enchants may carry an `effects = [...]` bundle. [effects] supplies the three
     * registries those bundles resolve against (actions / conditions / block-matchers). It is optional:
     * `load(root)` (no registries) parses everything EXCEPT effects, which keeps every effect-free
     * caller working; ContentModule passes the builtin-populated registries so effect bundles resolve
     * and any unknown action/condition/matcher id aggregates as a source-tagged [ValidationError].
     */
    fun load(root: Path, effects: EffectSpec.Registries? = null): RpgContentLoadResult {
        // 1. RamCore parses files, resolves `extends:` deep-merge, and collects parse / missing-parent
        //    / cycle / duplicate-id errors -- each already carrying its SourceRef. Never throws.
        val content: ContentLoadResult = ContentLoader.load(root)

        // 2. RamCore deserializes each merged node via the per-type deserializer, catching every
        //    ContentDeserializeException as a ValidationError.at(file, path, message). The result's
        //    errors() already carry over content.errors(), so we do not add them again.
        val specResult: SpecLoadResult = SpecLoader.create()
            .deserializer(TYPE_STATS, ContentDeserializer { StatSpec.deserialize(it) })
            .deserializer(TYPE_ITEMS, ContentDeserializer { ItemSpec.deserialize(it, effects) })
            .deserializer(TYPE_SKILLS, ContentDeserializer { SkillSpec.deserialize(it) })
            .deserializer(TYPE_ENCHANTS, ContentDeserializer { EnchantSpec.deserialize(it, effects) })
            .deserializer(TYPE_ENTITIES, ContentDeserializer { EntityProfileSpec.deserialize(it) })
            .deserializer(TYPE_REFORGES, ContentDeserializer { ReforgeSpec.deserialize(it) })
            .deserializer(TYPE_GEMS, ContentDeserializer { GemSpec.deserialize(it) })
            .deserializer(TYPE_SETS, ContentDeserializer { SetSpec.deserialize(it, effects) })
            .deserializer(TYPE_RECIPES, ContentDeserializer { RecipeSpec.deserialize(it) })
            .deserializer(TYPE_STATIONS, ContentDeserializer { StationSpec.deserialize(it) })
            .deserialize(content)

        // 3. RamRPG policy: a parsed definition whose type (directory) we do not recognise is an error
        //    that NAMES the directory, rather than being silently dropped. One error per offending
        //    entry, so these aggregate exactly like every other error.
        val unknownTypeErrors: List<ValidationError> = content.definitions()
            .filter { it.type() !in KNOWN_TYPES }
            .map { def ->
                ValidationError.at(
                    def.source().file(),
                    def.source().path().ifEmpty { def.id().toString() },
                    "unknown content type '${def.type()}'; expected a directory named one of ${KNOWN_TYPES.joinToString(", ")}",
                )
            }

        val errors = specResult.errors() + unknownTypeErrors

        // Every parsed definition's SourceRef, so registration-time errors (e.g. unknown material,
        // detectable only with Bukkit in ContentRegistrarRpg) can still cite the file + path the loader
        // already knows, keeping the "every error carries file and path" promise.
        val sources: Map<ContentId, SourceRef> = content.definitions().associate { it.id() to it.source() }

        return RpgContentLoadResult(
            stats = specResult.ofType(TYPE_STATS, StatSpec::class.java),
            items = specResult.ofType(TYPE_ITEMS, ItemSpec::class.java),
            skills = specResult.ofType(TYPE_SKILLS, SkillSpec::class.java),
            enchants = specResult.ofType(TYPE_ENCHANTS, EnchantSpec::class.java),
            entities = specResult.ofType(TYPE_ENTITIES, EntityProfileSpec::class.java),
            reforges = specResult.ofType(TYPE_REFORGES, ReforgeSpec::class.java),
            gems = specResult.ofType(TYPE_GEMS, GemSpec::class.java),
            sets = specResult.ofType(TYPE_SETS, SetSpec::class.java),
            recipes = specResult.ofType(TYPE_RECIPES, RecipeSpec::class.java),
            stations = specResult.ofType(TYPE_STATIONS, StationSpec::class.java),
            errorList = errors,
            sources = sources,
        )
    }

    /**
     * WP-1.5d: resource paths, relative to the packaged `content/` classpath root bundled inside the
     * plugin jar (`src/main/resources/content/...`), that mirror the on-disk `content/<type>/<file>`
     * layout [load] reads from an operator's data folder. Each WP that packages a new default content
     * file appends its path here; WP-1.5d packages only the builtin items (rule 9 -- this WP does not
     * touch other content types) -- see docs/design/1.5d-builtin-content.md.
     *
     * Each packaged file is ONE HOCON OBJECT whose top-level keys are per-entry slugs (arbitrary --
     * only each child's own `id` field matters to the loader), not the top-level ARRAY the equivalent
     * `.yml` content file could use: Configurate's [HoconConfigurationLoader] parses a `.conf` file's
     * `Config` root as an object and rejects a bare array ("has type LIST rather than object at file
     * root") even though a YAML content file's root CAN be a list -- see `builtin.conf`'s own header
     * comment. [extractPackagedContent] splits each keyed child back into its own real,
     * independently-valid `<type>/<slug>.conf` file before RamCore's ContentLoader ever sees it.
     */
    private val PACKAGED_CONTENT: List<String> = listOf("items/builtin.conf", "sets/builtin.conf")

    /**
     * WP-3.1d: the crafting content pack -- the shipped material items plus every recipe/station conf.
     * Kept SEPARATE from [PACKAGED_CONTENT] on purpose: [loadPackaged] (and thus
     * [dev.willram.ramrpg.builtin.items.BuiltinItems] / [dev.willram.ramrpg.core.modules.SetModule]) reads
     * [PACKAGED_CONTENT] and registers its `items`/`sets` programmatically, so folding the material items
     * in there would (a) double-register every id -- `SimpleContentRegistry.register` throws on a
     * duplicate id even across owners -- and (b) break the parity tests that pin `loadPackaged().items` at
     * the 65 builtin weapons/armor. Instead [dev.willram.ramrpg.core.modules.ContentModule] extracts THIS
     * list into the operator's `content/` on first run and loads it through the normal [load] pipeline
     * exactly once, registering everything under its single content OWNER so `/rpg reload` cleans it up.
     *
     * `items/materials.conf` is included because the refine recipes' `new_item` outcomes reference those
     * ids and the registrar validates that the output [dev.willram.ramrpg.api.items.ItemDefinition] exists
     * -- they are registered nowhere else. See docs/design/3.1d-crafting-pipeline.md.
     */
    private val PACKAGED_CRAFTING_CONTENT: List<String> = listOf(
        "items/materials.conf",
        "recipes/materials.conf",
        "recipes/smithing_upgrade.conf",
        "recipes/reforge.conf",
        "recipes/sockets.conf",
        "stations/smithing.conf",
        // WP-3.1e: the crafting bench (permits NEW_ITEM/TRANSMUTE) so the refine recipes in
        // recipes/materials.conf -- re-pointed here from smithing -- are craftable, not DISALLOWED_OUTCOME.
        "stations/crafting_bench.conf",
    )

    /**
     * WP-3.1d: the packaged crafting confs [dev.willram.ramrpg.core.modules.ContentModule] extracts into an
     * operator's `content/` on first run so the shipped recipes/stations both load AND become editable.
     * Exposed (vs. the private [PACKAGED_CRAFTING_CONTENT]) so the module names one list, not each path.
     */
    fun packagedCraftingContent(): List<String> = PACKAGED_CRAFTING_CONTENT

    /**
     * WP-1.5d: first-run resource extraction. For every path in [paths] (default [PACKAGED_CONTENT]),
     * parses the packaged HOCON object (via Configurate's own load/save node API -- no hand-rolled
     * parsing) and writes each top-level child as its own `<dataContentDir>/<type>/<slug>.conf` file,
     * UNLESS that destination already exists -- so an operator's edited (or deliberately deleted) copy is
     * never clobbered on a later startup, and only missing/new entries are (re-)written on an upgrade.
     * Returns the paths actually written, for logging. WP-3.1d passes [PACKAGED_CRAFTING_CONTENT] to seed
     * the crafting pack.
     *
     * Blocking classpath + file I/O, exactly like [load] -- callers run this off the main thread (see
     * this file's header doc and [dev.willram.ramrpg.core.modules.ContentModule]).
     */
    fun extractPackagedContent(
        dataContentDir: Path,
        classLoader: ClassLoader = RpgContentLoader::class.java.classLoader,
        paths: List<String> = PACKAGED_CONTENT,
    ): List<Path> {
        val extracted = ArrayList<Path>()
        for (relative in paths) {
            val type = relative.substringBefore('/')
            val root = classLoader.getResourceAsStream("content/$relative")?.let { input ->
                HoconConfigurationLoader.builder()
                    .source { BufferedReader(InputStreamReader(input, Charsets.UTF_8)) }
                    .build()
                    .load()
            } ?: continue
            for ((slug, child) in root.childrenMap()) {
                val dest = dataContentDir.resolve(type).resolve("$slug.conf")
                if (Files.exists(dest)) continue
                Files.createDirectories(dest.parent)
                HoconConfigurationLoader.builder().path(dest).build().save(child)
                extracted.add(dest)
            }
        }
        return extracted
    }

    /**
     * WP-1.5d: the "parity path" -- loads packaged content DIRECTLY from the plugin jar's classpath,
     * without requiring (or touching) an operator's data folder. RamCore's [ContentLoader] only reads a
     * real filesystem directory (`Files.isDirectory`, see its source), so this extracts
     * [PACKAGED_CONTENT] into a throwaway temp directory via [extractPackagedContent] and then
     * delegates to [load] -- no parsing/deserialization logic is duplicated, so this stays byte-for-byte
     * consistent with the real `content/` pipeline. The temp directory is always cleaned up.
     *
     * Used by [dev.willram.ramrpg.builtin.items.BuiltinItems] (the programmatic entry point, rule 7)
     * and by parity tests that run off-server.
     */
    fun loadPackaged(
        effects: EffectSpec.Registries? = null,
        classLoader: ClassLoader = RpgContentLoader::class.java.classLoader,
    ): RpgContentLoadResult {
        val tempRoot = Files.createTempDirectory("ramrpg-packaged-content")
        try {
            extractPackagedContent(tempRoot, classLoader)
            return load(tempRoot, effects)
        } finally {
            tempRoot.toFile().deleteRecursively()
        }
    }
}

/**
 * Shared, PURE node-reading helpers for the spec deserializers. Kept here (not in a new file) because
 * WP-1.5a's file set is fixed and specs may not add sibling files; `internal` keeps it main-module
 * scoped. Every helper throws [ContentDeserializeException] on a malformed value so [SpecLoader] tags
 * it with the offending file + path. No Bukkit is touched -- only Configurate and Adventure -- so the
 * specs that call these stay off-server-testable.
 */
internal object SpecNodes {

    private val MM: MiniMessage = MiniMessage.miniMessage()

    /** The entry id from `id`. Always present by the time a deserializer runs (ContentLoader has
     *  already parsed + validated it before building the ContentDefinition), but re-read defensively. */
    fun requireId(node: ConfigurationNode): ContentId {
        val raw = node.node("id").getString()
        if (raw.isNullOrBlank()) throw ContentDeserializeException("entry is missing 'id'")
        return parseId(raw, "id")
    }

    /** An optional namespaced id at [key], or null when absent/blank. */
    fun optionalId(node: ConfigurationNode, key: String): ContentId? {
        val raw = node.node(key).getString()
        if (raw.isNullOrBlank()) return null
        return parseId(raw, key)
    }

    /** A required namespaced id at [key] (a sub-field, not the entry's own `id`). */
    fun requireIdAt(node: ConfigurationNode, key: String): ContentId =
        optionalId(node, key) ?: throw ContentDeserializeException("missing '$key'")

    fun requiredString(node: ConfigurationNode, key: String, what: String = key): String {
        val raw = node.node(key).getString()
        if (raw.isNullOrBlank()) throw ContentDeserializeException("missing '$what'")
        return raw.trim()
    }

    fun optionalString(node: ConfigurationNode, key: String): String? =
        node.node(key).getString()?.takeIf { it.isNotBlank() }?.trim()

    /** MiniMessage -> Component at [key], or null when absent. */
    fun component(node: ConfigurationNode, key: String): Component? =
        node.node(key).getString()?.let { MM.deserialize(it) }

    fun componentOr(node: ConfigurationNode, key: String, fallback: Component): Component =
        component(node, key) ?: fallback

    /** Each list element as a MiniMessage line. Missing key -> empty list. */
    fun componentList(node: ConfigurationNode, key: String): List<Component> =
        node.node(key).childrenList().mapNotNull { it.getString() }.map { MM.deserialize(it) }

    fun stringList(node: ConfigurationNode, key: String): List<String> =
        node.node(key).childrenList().mapNotNull { it.getString()?.trim() }

    /** A `<statId> = <double>` map at [key] -> StatKey -> amount. Missing key -> empty map. */
    fun statAmounts(node: ConfigurationNode, key: String): Map<StatKey, Double> {
        val out = LinkedHashMap<StatKey, Double>()
        node.node(key).childrenMap().forEach { (rawKey, value) ->
            out[StatKey(parseId(rawKey.toString(), "$key key"))] = value.getDouble()
        }
        return out
    }

    fun doubleOr(node: ConfigurationNode, key: String, fallback: Double): Double {
        val n = node.node(key)
        return if (n.virtual()) fallback else n.getDouble()
    }

    fun doubleOrNull(node: ConfigurationNode, key: String): Double? {
        val n = node.node(key)
        return if (n.virtual()) null else n.getDouble()
    }

    fun intOr(node: ConfigurationNode, key: String, fallback: Int): Int {
        val n = node.node(key)
        return if (n.virtual()) fallback else n.getInt()
    }

    fun intOrNull(node: ConfigurationNode, key: String): Int? {
        val n = node.node(key)
        return if (n.virtual()) null else n.getInt()
    }

    fun boolOr(node: ConfigurationNode, key: String, fallback: Boolean): Boolean {
        val n = node.node(key)
        return if (n.virtual()) fallback else n.getBoolean()
    }

    /** A named ("green") or hex ("#33aa55") Adventure colour at [key], or [fallback] when absent. */
    fun colorOr(node: ConfigurationNode, key: String, fallback: TextColor): TextColor {
        val raw = node.node(key).getString()?.takeIf { it.isNotBlank() }?.trim() ?: return fallback
        val parsed = if (raw.startsWith("#")) TextColor.fromHexString(raw)
        else NamedTextColor.NAMES.value(raw.lowercase())
        return parsed ?: throw ContentDeserializeException("unknown colour '$raw'")
    }

    inline fun <reified E : Enum<E>> enumOr(node: ConfigurationNode, key: String, fallback: E, what: String): E {
        val raw = node.node(key).getString()?.takeIf { it.isNotBlank() } ?: return fallback
        return parseEnum(raw, what)
    }

    inline fun <reified E : Enum<E>> requiredEnum(node: ConfigurationNode, key: String, what: String): E {
        val raw = node.node(key).getString()
        if (raw.isNullOrBlank()) throw ContentDeserializeException("missing '$key'")
        return parseEnum(raw, what)
    }

    inline fun <reified E : Enum<E>> enumList(node: ConfigurationNode, key: String, what: String): List<E> =
        stringList(node, key).map { parseEnum<E>(it, what) }

    inline fun <reified E : Enum<E>> parseEnum(raw: String, what: String): E =
        try {
            enumValueOf<E>(raw.trim().uppercase())
        } catch (unknown: IllegalArgumentException) {
            throw ContentDeserializeException("unknown $what '$raw'")
        }

    fun parseId(raw: String, what: String): ContentId =
        try {
            ContentId.parse(raw.trim())
        } catch (invalid: RuntimeException) {
            throw ContentDeserializeException("invalid $what '$raw': ${invalid.message}")
        }
}
