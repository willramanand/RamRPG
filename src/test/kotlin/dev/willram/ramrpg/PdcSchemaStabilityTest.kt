package dev.willram.ramrpg

import com.google.gson.Gson
import dev.willram.ramrpg.core.services.ItemInstanceServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
