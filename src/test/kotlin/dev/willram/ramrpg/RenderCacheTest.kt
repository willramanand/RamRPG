package dev.willram.ramrpg

import dev.willram.ramrpg.core.rendering.RenderCacheKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RenderCacheTest {

    @Test
    fun `cache key equality covers all fields`() {
        val base = RenderCacheKey("ramrpg:iron_sword", 1, 0, "en_us", 0)
        assertEquals(base, RenderCacheKey("ramrpg:iron_sword", 1, 0, "en_us", 0))
        assertNotEquals(base, base.copy(itemKeyId = "ramrpg:gold_sword"))
        assertNotEquals(base, base.copy(schemaVersion = 2))
        assertNotEquals(base, base.copy(instanceHash = 1))
        assertNotEquals(base, base.copy(viewerLocale = "fr_fr"))
        assertNotEquals(base, base.copy(definitionRev = 1))
    }

    @Test
    fun `cache key hashCode stable for equal keys`() {
        val a = RenderCacheKey("ramrpg:bow", 3, 42, "en_us", 7)
        val b = RenderCacheKey("ramrpg:bow", 3, 42, "en_us", 7)
        assertTrue(a.hashCode() == b.hashCode())
    }
}
