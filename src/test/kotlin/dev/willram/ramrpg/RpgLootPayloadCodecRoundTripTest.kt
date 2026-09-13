package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemInstanceInit
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.items.SocketData
import dev.willram.ramrpg.core.loot.RpgItemPayload
import dev.willram.ramrpg.core.loot.RpgLootPayloadCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * WP-1.1b: [RpgLootPayloadCodec] is a pure Gson codec (no Bukkit), so it round-trips entirely
 * off-server: encode -> decode must reproduce the original [RpgItemPayload] exactly.
 */
class RpgLootPayloadCodecRoundTripTest {
    private val codec = RpgLootPayloadCodec()

    @Test
    fun `round-trips a minimal payload with default ItemInstanceInit`() {
        val payload = RpgItemPayload(ItemKey.of("ramrpg", "iron_sword"), ItemInstanceInit())

        val encoded = codec.encode(payload)

        assertEquals(payload, codec.decode(encoded))
    }

    @Test
    fun `round-trips every ItemInstanceInit field`() {
        val payload = RpgItemPayload(
            item = ItemKey.of("ramrpg", "rogue_blade"),
            init = ItemInstanceInit(
                upgradeLevel = 4,
                reforge = ReforgeKey(ContentId.of("ramrpg", "sharp")),
                sockets = listOf(
                    SocketData(ContentId.of("ramrpg", "socket_1"), ContentId.of("ramrpg", "ruby")),
                    SocketData(ContentId.of("ramrpg", "socket_2")),
                ),
                customRolls = mapOf(
                    StatKey.of("ramrpg", "damage") to 12.5,
                    StatKey.of("ramrpg", "crit_chance") to 0.1,
                ),
                enchantments = mapOf(EnchantmentKey.of("ramrpg", "sharpness") to 3),
                owner = UUID.randomUUID(),
                assignInstanceId = false,
                rollSeed = 42L,
            ),
        )

        val encoded = codec.encode(payload)

        assertEquals(payload, codec.decode(encoded))
    }

    @Test
    fun `encodes and decodes null as null`() {
        assertNull(codec.encode(null))
        assertNull(codec.decode(null))
    }

    @Test
    fun `rejects payloads that are not RpgItemPayload`() {
        assertThrows(IllegalArgumentException::class.java) { codec.encode("not a payload") }
    }
}
