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
        )
        return write(stack, data)
    }

    /** Visible for testing. Deterministic when [ItemInstanceInit.rollSeed] set. */
    fun rollStats(def: ItemDefinition, init: ItemInstanceInit): Map<dev.willram.ramrpg.api.identity.StatKey, Double> {
        if (def.statRolls.isEmpty() || init.customRolls.isNotEmpty()) return init.customRolls
        val rng = init.rollSeed?.let { java.util.Random(it) } ?: java.util.Random()
        return def.statRolls.associate { roll ->
            val v = if (roll.min == roll.max) roll.min
            else roll.min + rng.nextDouble() * (roll.max - roll.min)
            roll.stat to v
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
    ) {
        fun toJson(): String = GSON.toJson(this)
        fun toDomain(): ItemInstanceData = ItemInstanceData(
            identity = ItemIdentity(ItemKey(ContentId.parse(k)), iid?.let(UUID::fromString), v),
            upgradeLevel = u,
            reforge = r?.let { ReforgeKey(ContentId.parse(it)) },
            sockets = s.map { SocketData(ContentId.parse(it.k), it.g?.let(ContentId::parse)) },
            customRolls = rolls.mapKeys { (k, _) -> StatKey(ContentId.parse(k)) },
            enchantments = ench.mapKeys { (k, _) -> EnchantmentKey(ContentId.parse(k)) },
            owner = owner?.let(UUID::fromString),
            customName = cn,
        )
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
            )
        }
    }

    data class SocketDto(val k: String, val g: String? = null)
}

interface ItemSchemaMigrator {
    fun migrate(dto: ItemInstanceServiceImpl.ItemDto): ItemInstanceServiceImpl.ItemDto
    companion object {
        val NOOP: ItemSchemaMigrator = object : ItemSchemaMigrator {
            override fun migrate(dto: ItemInstanceServiceImpl.ItemDto) = dto
        }
    }
}
