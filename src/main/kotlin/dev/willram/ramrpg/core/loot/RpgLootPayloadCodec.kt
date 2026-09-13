/**
 * WP-1.1b: turns an [RpgItemPayload] (ItemKey + ItemInstanceInit) into a single JSON string and back,
 * the shape [dev.willram.ramcore.loot.LootPayloadCodec] needs to hand an
 * [dev.willram.ramcore.loot.InstancedLoot.persistentStore] a [dev.willram.ramrpg.core.loot.RpgItemPayload]
 * reward payload to persist. A pure Gson codec -- no Bukkit -- so it is unit-tested off-server
 * (RpgLootPayloadCodecRoundTripTest). Mirrors the compact-key DTO shape
 * [dev.willram.ramrpg.core.services.ItemInstanceServiceImpl.ItemDto] already uses to persist the same
 * fields to PDC.
 */
package dev.willram.ramrpg.core.loot

import com.google.gson.Gson
import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.loot.LootPayloadCodec
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemInstanceInit
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.items.SocketData
import java.util.UUID

class RpgLootPayloadCodec : LootPayloadCodec {

    override fun encode(payload: Any?): String? {
        if (payload == null) return null
        require(payload is RpgItemPayload) {
            "RpgLootPayloadCodec only encodes RpgItemPayload, got ${payload.javaClass.name}"
        }
        return GSON.toJson(Dto.from(payload))
    }

    override fun decode(encoded: String?): Any? {
        if (encoded == null) return null
        return GSON.fromJson(encoded, Dto::class.java).toDomain()
    }

    /** Compact-key wire shape: one JSON object per encoded reward payload. */
    private data class Dto(
        val item: String,
        val u: Int = 0,
        val r: String? = null,
        val s: List<SocketDto> = emptyList(),
        val rolls: Map<String, Double> = emptyMap(),
        val ench: Map<String, Int> = emptyMap(),
        val owner: String? = null,
        val assignId: Boolean = true,
        val seed: Long? = null,
    ) {
        fun toDomain(): RpgItemPayload = RpgItemPayload(
            item = ItemKey(ContentId.parse(item)),
            init = ItemInstanceInit(
                upgradeLevel = u,
                reforge = r?.let { ReforgeKey(ContentId.parse(it)) },
                sockets = s.map { SocketData(ContentId.parse(it.k), it.g?.let(ContentId::parse)) },
                customRolls = rolls.mapKeys { (k, _) -> StatKey(ContentId.parse(k)) },
                enchantments = ench.mapKeys { (k, _) -> EnchantmentKey(ContentId.parse(k)) },
                owner = owner?.let(UUID::fromString),
                assignInstanceId = assignId,
                rollSeed = seed,
            ),
        )

        companion object {
            fun from(p: RpgItemPayload): Dto = Dto(
                item = p.item.id.toString(),
                u = p.init.upgradeLevel,
                r = p.init.reforge?.id?.toString(),
                s = p.init.sockets.map { SocketDto(it.key.toString(), it.gem?.toString()) },
                rolls = p.init.customRolls.mapKeys { (k, _) -> k.id.toString() },
                ench = p.init.enchantments.mapKeys { (k, _) -> k.id.toString() },
                owner = p.init.owner?.toString(),
                assignId = p.init.assignInstanceId,
                seed = p.init.rollSeed,
            )
        }
    }

    private data class SocketDto(val k: String, val g: String? = null)

    companion object {
        private val GSON = Gson()
    }
}
