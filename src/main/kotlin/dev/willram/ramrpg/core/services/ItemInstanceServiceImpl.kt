/**
 * ItemInstanceService implementation. Persists ItemInstanceData as JSON
 * under PDC key `ramrpg:item`. Provides legacy fallback for the old
 * `ramrpg-item-type` string tag and a vanilla-wrapper material match.
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.pdc.PDCs
import dev.willram.ramcore.pdc.PdcKey
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemInstanceInit
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.items.ItemSchema
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.items.SocketData
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class ItemInstanceServiceImpl(
    private val registry: ItemDefinitionRegistry,
    private val migrator: ItemSchemaMigrator = ItemSchemaMigrator.NOOP,
) : ItemInstanceService {

    private val key: PdcKey<String, String> = PdcKey.of("ramrpg", "item", PersistentDataType.STRING)
    private val legacyTypeKey = NamespacedKey("ramrpg", "ramrpg-item-type")

    override fun identify(item: ItemStack?): ItemInstanceData? {
        if (item == null || item.type.isAir) return null
        read(item)?.let { return it }
        // vanilla wrapper fallback: match by Material on definitions opted in
        for (def in registry.all()) {
            if (def.allowVanillaWrapper && def.material == item.type) {
                return ItemInstanceData(ItemIdentity(def.key))
            }
        }
        return null
    }

    override fun read(item: ItemStack): ItemInstanceData? {
        val meta = item.itemMeta ?: return null
        if (PDCs.has(meta, key)) {
            val raw = PDCs.get(meta, key).orElse(null) ?: return null
            val dto = ItemDto.parse(raw) ?: return null
            return migrator.migrate(dto).toDomain()
        }
        // Legacy fallback: inspect old ramrpg-item-type tag
        val pdc = meta.persistentDataContainer
        if (pdc.has(legacyTypeKey, PersistentDataType.STRING)) {
            val legacyId = pdc.get(legacyTypeKey, PersistentDataType.STRING) ?: return null
            val ck = ContentId.of("ramrpg", legacyId.lowercase())
            val def = registry.get(ItemKey(ck)) ?: return null
            return ItemInstanceData(ItemIdentity(def.key, instanceId = UUID.randomUUID()))
        }
        return null
    }

    override fun create(def: ItemDefinition, init: ItemInstanceInit): ItemStack {
        val stack = ItemStack(def.material)
        val rolledRolls = rollStats(def, init)
        val data = ItemInstanceData(
            identity = ItemIdentity(def.key, instanceId = if (init.assignInstanceId) UUID.randomUUID() else null),
            upgradeLevel = init.upgradeLevel,
            reforge = init.reforge,
            sockets = init.sockets,
            customRolls = rolledRolls,
            enchantments = init.enchantments,
            owner = init.owner,
            // WP-2.1b: quality defaults to 0.5 for drops; an explicit init.quality (crafting, a later WP)
            // both positions the rolls above (see rollStats) and is stored here. Durability/maxDurability
            // seed to full via the ItemInstanceData defaults -- there is no definition-side durability
            // field yet (ItemSpec untouched this WP), and DRAIN is WP-2.2.
            quality = init.quality?.coerceIn(0.0, 1.0) ?: ItemInstanceData.DEFAULT_QUALITY,
        )
        return write(stack, data)
    }

    /**
     * Visible for testing. Rolls each [ItemDefinition.statRolls] entry into a concrete value.
     *
     * Precedence: an explicit [ItemInstanceInit.customRolls] wins; else, when [ItemInstanceInit.quality]
     * is set, each roll is positioned DETERMINISTICALLY by quality via [qualityScaledRoll]
     * (`min + quality*(max-min)`); else the historical RNG path applies, deterministic when
     * [ItemInstanceInit.rollSeed] is set (pinned by `StatRollTest`).
     */
    fun rollStats(def: ItemDefinition, init: ItemInstanceInit): Map<dev.willram.ramrpg.api.identity.StatKey, Double> {
        if (def.statRolls.isEmpty() || init.customRolls.isNotEmpty()) return init.customRolls
        init.quality?.let { q ->
            return def.statRolls.associate { roll -> roll.stat to qualityScaledRoll(roll.min, roll.max, q) }
        }
        val rng = init.rollSeed?.let { java.util.Random(it) } ?: java.util.Random()
        return def.statRolls.associate { roll ->
            val v = if (roll.min == roll.max) roll.min
            else roll.min + rng.nextDouble() * (roll.max - roll.min)
            roll.stat to v
        }
    }

    companion object {
        /**
         * WP-2.1b quality-scales-rolls formula. Quality `q` in `[0,1]` positions a stat roll
         * DETERMINISTICALLY within its band:
         *
         *     qualityScaledRoll(min, max, q) = min + q * (max - min)
         *
         * so `q=0.0 -> min` (worst), `q=1.0 -> max` (best), `q=0.5 -> midpoint`. It is the same
         * `[min,max]` line [rollStats]'s RNG samples with `rng.nextDouble()`, with quality supplying the
         * position explicitly -- so a crafter's quality and a drop's seeded roll live on one axis, not
         * two. Pure and deterministic (no RNG); `q` is coerced into `[0,1]`. See
         * `docs/design/2.1b-quality-durability.md`.
         */
        fun qualityScaledRoll(min: Double, max: Double, quality: Double): Double {
            val q = quality.coerceIn(0.0, 1.0)
            return min + q * (max - min)
        }
    }

    override fun write(item: ItemStack, data: ItemInstanceData): ItemStack {
        val out = item.clone()
        val meta = out.itemMeta ?: return out
        val dto = ItemDto.from(data)
        PDCs.set(meta, key, dto.toJson())
        out.itemMeta = meta
        return out
    }

    data class ItemDto(
        val v: Int,
        val k: String,
        val iid: String? = null,
        val u: Int = 0,
        val r: String? = null,
        val s: List<SocketDto> = emptyList(),
        val rolls: Map<String, Double> = emptyMap(),
        val ench: Map<String, Int> = emptyMap(),
        val owner: String? = null,
        val cn: String? = null,
        // WP-2.1b (schema v2). NULLABLE on purpose: Gson leaves an absent JSON field null (it does NOT
        // run Kotlin constructor defaults), so a v1 blob -- which omits these keys -- deserializes with
        // them null and toDomain() substitutes the ItemInstanceData schema defaults. That default-fill on
        // read is exactly why ItemSchemaMigrator stays a no-op (no V1->V2 engine, no fixture test).
        val q: Double? = null,
        val cb: String? = null,
        val mdur: Int? = null,
        val dur: Int? = null,
    ) {
        fun toJson(): String = GSON.toJson(this)
        fun toDomain(): ItemInstanceData {
            val maxDur = mdur ?: ItemInstanceData.DEFAULT_MAX_DURABILITY
            return ItemInstanceData(
                identity = ItemIdentity(ItemKey(ContentId.parse(k)), iid?.let(UUID::fromString), v),
                upgradeLevel = u,
                reforge = r?.let { ReforgeKey(ContentId.parse(it)) },
                sockets = s.map { SocketData(ContentId.parse(it.k), it.g?.let(ContentId::parse)) },
                customRolls = rolls.mapKeys { (k, _) -> StatKey(ContentId.parse(k)) },
                enchantments = ench.mapKeys { (k, _) -> EnchantmentKey(ContentId.parse(k)) },
                owner = owner?.let(UUID::fromString),
                customName = cn,
                quality = q ?: ItemInstanceData.DEFAULT_QUALITY,
                craftedBy = cb?.let(UUID::fromString),
                maxDurability = maxDur,
                durability = dur ?: maxDur,
            )
        }
        companion object {
            private val GSON = com.google.gson.Gson()
            fun parse(json: String): ItemDto? = runCatching { GSON.fromJson(json, ItemDto::class.java) }.getOrNull()
            fun from(d: ItemInstanceData): ItemDto = ItemDto(
                v = d.identity.schemaVersion.coerceAtLeast(ItemSchema.CURRENT),
                k = d.identity.key.id.toString(),
                iid = d.identity.instanceId?.toString(),
                u = d.upgradeLevel,
                r = d.reforge?.id?.toString(),
                s = d.sockets.map { SocketDto(it.key.toString(), it.gem?.toString()) },
                rolls = d.customRolls.mapKeys { (k, _) -> k.id.toString() },
                ench = d.enchantments.mapKeys { (k, _) -> k.id.toString() },
                owner = d.owner?.toString(),
                cn = d.customName,
                q = d.quality,
                cb = d.craftedBy?.toString(),
                mdur = d.maxDurability,
                dur = d.durability,
            )
        }
    }

    data class SocketDto(val k: String, val g: String? = null)
}

/**
 * Hook for transforming a parsed [ItemInstanceServiceImpl.ItemDto] before it becomes domain data.
 *
 * WP-2.1b deliberately keeps this a DOCUMENTED NO-OP. RamRPG is in-house and deploys to fresh servers,
 * so there is no old PDC data to migrate: the schema v1 -> v2 bump (quality / craftedBy / durability)
 * ships WITHOUT a migration engine and WITHOUT a checked-in v1 fixture test, per the standing
 * fresh-server / no-migration policy. The new v2 fields are simply absent from a v1 blob and fill from
 * the [dev.willram.ramrpg.api.items.ItemInstanceData] schema defaults inside
 * [ItemInstanceServiceImpl.ItemDto.toDomain] (nullable DTO fields -> defaults), so no default-filling
 * pass belongs here either. This seam remains only so a genuine future migration has a home.
 */
interface ItemSchemaMigrator {
    fun migrate(dto: ItemInstanceServiceImpl.ItemDto): ItemInstanceServiceImpl.ItemDto
    companion object {
        /** The documented no-op (see the interface KDoc): returns the DTO unchanged. */
        val NOOP: ItemSchemaMigrator = object : ItemSchemaMigrator {
            override fun migrate(dto: ItemInstanceServiceImpl.ItemDto) = dto
        }
    }
}
