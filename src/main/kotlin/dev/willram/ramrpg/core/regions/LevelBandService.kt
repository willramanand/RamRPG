/**
 * WP-6.2: region level scaling. A [LevelBand] is a level range plus a `statMultiplier` (applied to a
 * spawned mob's baseline stats), `tierWeights` (a forward-looking hook for mob-tier bias -- see
 * docs/design/6.2-level-bands.md; consumed today only as a loot `luck` bias, see [LootListener]) and an
 * optional `lootTableOverride` (a RamCore [LootTable] that replaces the mob's own drops while standing
 * in the band).
 *
 * [LevelBandService] resolves a location to a band using RamCore's region engine FIRST
 * ([RegionRuleEngine.regionsAt] over [RuleRegion]s built from [RegionSpec] via
 * [ContentRegistrar.toRuleRegion] -- rule 1, no parallel geometry system), then falls back to a
 * documented distance-from-world-spawn curve when no region matches (see [fallback]).
 *
 * Folia rule 4: [RegionRuleEngine.regionsAt] is cheap but not free, so a band is resolved and cached
 * on the entity exactly ONCE -- [resolveAndCache] checks the cache first and only calls [resolve] (and
 * therefore [RegionRuleEngine.regionsAt]) on a genuine cache miss (first resolve for that entity, or
 * the entity's cache was never primed -- e.g. it existed before this feature or its metadata expired).
 * It is never called per-tick or per-damage-instance.
 *
 * Cache mechanism: RamCore's in-memory [Metadata] (`dev.willram.ramcore.metadata`), keyed per-entity by
 * UUID -- the SAME "entity metadata" mechanism the roadmap earmarks for WP-6.1a's affixes, chosen over
 * PDC deliberately for consistency with that later WP (see docs/design/6.2-level-bands.md). It is NOT
 * persisted to disk: an entity that survives a server restart (loaded from a saved chunk, no
 * `EntitySpawnEvent`) simply re-resolves its band next time [resolveAndCache] runs for it (e.g. on
 * death, in [LootListener]) -- one extra `regionsAt` call for that one entity, never a recurring cost.
 */
package dev.willram.ramrpg.core.regions

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.content.ContentRegistrar
import dev.willram.ramcore.content.spec.RegionSpec
import dev.willram.ramcore.loot.LootTable
import dev.willram.ramcore.metadata.Metadata
import dev.willram.ramcore.metadata.MetadataKey
import dev.willram.ramcore.region.RegionRuleEngine
import dev.willram.ramcore.serialize.Position
import net.kyori.adventure.text.Component
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.sqrt

/**
 * A level band: [minLevel]/[maxLevel] is the player-level range it targets (docs/design/0.1-power-curve.md),
 * [statMultiplier] scales a spawned mob's baseline stats, [tierWeights] biases mob rarity tier
 * (tier names match [dev.willram.ramrpg.builtin.entities.BuiltinEntities]'s uncommon/rare/epic/legendary
 * -- consumed by future mob-variant work, WP-6.3a) and today folded into loot `luck`, and
 * [lootTableOverride] replaces a mob's own loot table while null (the shipped bands all ship null --
 * see docs/design/6.2-level-bands.md).
 */
data class LevelBand(
    val id: ContentId,
    val minLevel: Int,
    val maxLevel: Int,
    val statMultiplier: Double,
    val tierWeights: Map<String, Double> = emptyMap(),
    val lootTableOverride: LootTable? = null,
)

/**
 * Player-facing display name for a shipped band, translated via `ramrpg.level_band.<slug>` -- same
 * `Component.translatable(key, fallback)` pattern as [dev.willram.ramrpg.builtin.identity.BuiltinDamageTypes.displayName].
 * Not called by any listener yet (this WP has no chat/UI requirement -- see docs/design/6.2-level-bands.md);
 * ships ahead of a consumer the same way WP-2.3a shipped `resistance_true`'s lang entry ahead of full
 * consumption. An id outside the 5 shipped slugs (an operator's own `bands.conf` edit) falls back to the
 * raw id, same as [dev.willram.ramrpg.builtin.identity.BuiltinDamageTypes.displayName]'s `else` branch.
 */
fun levelBandDisplayName(band: LevelBand): Component = when (band.id.value()) {
    "band_1" -> Component.translatable("ramrpg.level_band.band_1", Component.text("Starter Lands"))
    "band_2" -> Component.translatable("ramrpg.level_band.band_2", Component.text("Early Frontier"))
    "band_3" -> Component.translatable("ramrpg.level_band.band_3", Component.text("Midlands"))
    "band_4" -> Component.translatable("ramrpg.level_band.band_4", Component.text("Far Wilds"))
    "band_5" -> Component.translatable("ramrpg.level_band.band_5", Component.text("Endlands"))
    else -> Component.text(band.id.value())
}

class LevelBandService internal constructor(
    private val engine: RegionRuleEngine,
    private val bandsById: Map<ContentId, LevelBand>,
    private val bandsByLevel: List<LevelBand>,
) : AutoCloseable {

    /** Every loaded band, ascending by [LevelBand.minLevel]. */
    fun bands(): List<LevelBand> = bandsByLevel

    fun band(id: ContentId): LevelBand? = bandsById[id]

    /**
     * PURE resolution core: the region engine first (highest-priority containing [RuleRegion] whose id
     * names a loaded band wins), then [fallback]. No Bukkit -- safe off-server, exercised directly by
     * `LevelBandResolutionTest`/`DistanceFallbackTest`.
     */
    fun resolve(position: Position, worldSpawn: Position): LevelBand? {
        for (region in engine.regionsAt(position)) {
            bandsById[region.id()]?.let { return it }
        }
        return fallback(position, worldSpawn)
    }

    /**
     * Distance-from-spawn fallback used when no registered region contains [position] (open world with
     * no hand-placed band region, or a world other than the one `bands.conf`'s region shapes name). Bands
     * are laid out as concentric [RING_WIDTH]-block rings ordered by [LevelBand.minLevel] ascending: ring
     * 0 is `[0, RING_WIDTH)`, ring 1 is `[RING_WIDTH, 2*RING_WIDTH)`, etc., clamped to the last band past
     * the outermost ring. Horizontal (X/Z) distance only -- Y is ignored, matching how the shipped
     * bands.conf spheres are also horizontal-distance rings in practice (radius >> world height).
     */
    private fun fallback(position: Position, worldSpawn: Position): LevelBand? {
        if (bandsByLevel.isEmpty()) return null
        val dx = position.x - worldSpawn.x
        val dz = position.z - worldSpawn.z
        val distance = sqrt(dx * dx + dz * dz)
        val index = (distance / RING_WIDTH).toInt().coerceIn(0, bandsByLevel.size - 1)
        return bandsByLevel[index]
    }

    /**
     * Resolve-once-cache-on-entity (rule 4). Returns the entity's already-cached band on a hit (no
     * [RegionRuleEngine.regionsAt] call); on a miss, resolves via [resolve] using the entity's current
     * location and its world's spawn location, caches the id in RamCore [Metadata], and returns it.
     * Called once at spawn ([dev.willram.ramrpg.core.listeners.EntitySpawnListener], via
     * [dev.willram.ramrpg.core.services.EntityProfileRegistryImpl.resolve]) and again, cache-hit only in
     * the common case, wherever else a band is needed (e.g. [dev.willram.ramrpg.core.listeners.LootListener]
     * at death).
     */
    fun resolveAndCache(entity: LivingEntity): LevelBand? {
        val map = Metadata.entities().provide(entity)
        map.getOrNull(BAND_METADATA_KEY)?.let { cachedId -> return bandsById[cachedId] }
        val worldSpawn = Position.of(entity.world.spawnLocation)
        val band = resolve(Position.of(entity.location), worldSpawn) ?: return null
        map.put(BAND_METADATA_KEY, band.id)
        return band
    }

    /**
     * WP-6.2: drop [entity]'s cached band so its per-entity [Metadata] map is reclaimed. The band cache
     * uses a non-transient `put`, which RamCore's housekeeping never evicts on its own (a non-empty map
     * is never `isEmpty()`), so [dev.willram.ramrpg.core.modules.MobModule] binds this to
     * `EntityRemoveFromWorldEvent` (death and unload) to keep entity metadata from growing unbounded
     * over server uptime.
     */
    fun evict(entity: Entity) {
        Metadata.entities().remove(entity.uniqueId)
    }

    /** Frees the private [RegionRuleEngine]'s registry. Bound by [dev.willram.ramrpg.core.modules.MobModule]. */
    override fun close() {
        engine.close()
    }

    companion object {
        const val OWNER: String = "ramrpg-level-bands"

        /** Blocks per distance-fallback ring; see [fallback]. Also the radius step the shipped
         *  `content/regions/bands.conf` spheres use, so the two mechanisms agree by construction. */
        const val RING_WIDTH: Double = 1000.0

        private val BAND_METADATA_KEY: MetadataKey<ContentId> =
            MetadataKey.create("ramrpg:level_band", ContentId::class.java)

        /** `value * band.statMultiplier`, or [value] unscaled when [band] is null. PURE -- exercised
         *  directly by `BandStatMultiplierTest`. */
        fun scale(value: Double, band: LevelBand?): Double =
            if (band != null) value * band.statMultiplier else value

        /**
         * Builds the production service from the packaged `content/regions/bands.conf` resource (this
         * WP does not wire an operator-editable `content/regions/` directory through
         * [dev.willram.ramrpg.core.config.ContentLoader] -- see docs/design/6.2-level-bands.md). Each
         * band's `region` block is a real [RegionSpec] turned into a real [RuleRegion] via
         * [ContentRegistrar.toRuleRegion] and registered into a private [RegionRuleEngine] this service
         * owns exclusively (no other RamRPG service shares a region engine today).
         */
        fun fromPackagedResource(classLoader: ClassLoader = LevelBandService::class.java.classLoader): LevelBandService {
            val engine = RegionRuleEngine()
            val bandsById = LinkedHashMap<ContentId, LevelBand>()
            val stream = classLoader.getResourceAsStream("content/regions/bands.conf")
            if (stream != null) {
                val root = stream.use { input ->
                    HoconConfigurationLoader.builder()
                        .source { BufferedReader(InputStreamReader(input, Charsets.UTF_8)) }
                        .build()
                        .load()
                }
                for ((_, child) in root.childrenMap()) {
                    val band = deserializeBand(child)
                    bandsById[band.id] = band
                    val regionSpec = RegionSpec.deserialize(child.node("region"))
                    engine.register(OWNER, ContentRegistrar.toRuleRegion(band.id, regionSpec))
                }
            }
            return LevelBandService(engine, bandsById, bandsById.values.sortedBy { it.minLevel })
        }

        private fun deserializeBand(node: ConfigurationNode): LevelBand {
            val rawId = node.node("id").getString()
                ?: throw IllegalStateException("bands.conf entry is missing 'id'")
            val minLevel = node.node("min-level").getInt(1)
            val tierWeights = LinkedHashMap<String, Double>()
            node.node("tier-weights").childrenMap().forEach { (key, value) ->
                tierWeights[key.toString()] = value.getDouble()
            }
            return LevelBand(
                id = ContentId.parse(rawId),
                minLevel = minLevel,
                maxLevel = node.node("max-level").getInt(minLevel),
                statMultiplier = node.node("stat-multiplier").getDouble(1.0),
                tierWeights = tierWeights,
                lootTableOverride = null,
            )
        }
    }
}
