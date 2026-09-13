package dev.willram.ramrpg

import com.google.gson.Gson
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.core.services.ItemInstanceServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class PdcSchemaStabilityTest {
    @Test
    fun `unknown fields ignored on parse`() {
        val raw = """{"v":1,"k":"ramrpg:iron_sword","u":2,"futureField":"ignored","extras":[1,2,3]}"""
        val dto = ItemInstanceServiceImpl.ItemDto.parse(raw)!!
        assertEquals(1, dto.v)
        assertEquals("ramrpg:iron_sword", dto.k)
        assertEquals(2, dto.u)
    }

    @Test
    fun `missing version still parses`() {
        val raw = """{"k":"ramrpg:wooden_sword"}"""
        val dto = ItemInstanceServiceImpl.ItemDto.parse(raw)!!
        // Gson default for primitive int is 0; toDomain coerces to >= CURRENT when written
        assertEquals(0, dto.v)
        assertEquals("ramrpg:wooden_sword", dto.k)
    }

    @Test
    fun `serialize produces stable shape`() {
        val gson = Gson()
        val obj = gson.fromJson("""{"v":1,"k":"ramrpg:bow"}""", ItemInstanceServiceImpl.ItemDto::class.java)
        val out = gson.toJson(obj)
        assertEquals(true, out.contains("\"v\":1"))
        assertEquals(true, out.contains("\"k\":\"ramrpg:bow\""))
    }

    @Test
    fun `WP-2_1b schema v2 fields round-trip through ItemDto forward`() {
        // Forward (v2 -> v2) round-trip of the new quality / craftedBy / durability fields:
        // write -> read must equal the original. Not a v1 -> v2 upgrade assertion (fresh-server policy).
        val crafter = UUID.randomUUID()
        val data = ItemInstanceData(
            identity = ItemIdentity(ItemKey.of("ramrpg", "rogue_blade"), instanceId = UUID.randomUUID()),
            quality = 0.83,
            craftedBy = crafter,
            maxDurability = 640,
            durability = 421,
        )
        val back = ItemInstanceServiceImpl.ItemDto.parse(ItemInstanceServiceImpl.ItemDto.from(data).toJson())!!.toDomain()
        assertEquals(0.83, back.quality)
        assertEquals(crafter, back.craftedBy)
        assertEquals(640, back.maxDurability)
        assertEquals(421, back.durability)
    }
}
