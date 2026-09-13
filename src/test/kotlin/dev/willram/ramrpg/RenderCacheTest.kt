package dev.willram.ramrpg

import dev.willram.ramcore.testkit.FakeItemStack
import dev.willram.ramrpg.core.rendering.RenderCache
import dev.willram.ramrpg.core.rendering.RenderCacheKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
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
        assertNotEquals(base, base.copy(viewerStateHash = 99), "WP-lore: viewer state is part of the key")
    }

    @Test
    fun `a different viewer state on an otherwise identical key misses the cache`() {
        // WP-lore (caching approach B): requirement met/unmet and active set count are per-viewer-state,
        // folded into the key via viewerStateHash. When a player levels a skill or swaps a set piece, the
        // hash changes, the key misses, and the lore re-renders -- no unrelated cache invalidation needed.
        val cache = RenderCache()
        val before = RenderCacheKey("ramrpg:iron_sword", 1, 0, "en_us", 0, viewerStateHash = 10)
        val after = before.copy(viewerStateHash = 20)
        var calls = 0

        cache.get(before) { calls++; FakeItemStack() }
        cache.get(after) { calls++; FakeItemStack() }

        assertEquals(2, calls, "changed viewer state renders separately instead of serving stale lore")
    }

    @Test
    fun `cache key hashCode stable for equal keys`() {
        val a = RenderCacheKey("ramrpg:bow", 3, 42, "en_us", 7)
        val b = RenderCacheKey("ramrpg:bow", 3, 42, "en_us", 7)
        assertTrue(a.hashCode() == b.hashCode())
    }

    // WP-1.6b: the cache itself (not just its key) - keyed by (itemHash, locale) via RenderCacheKey.

    @Test
    fun `a cache hit runs the factory only once and hands out independent clones`() {
        val cache = RenderCache()
        val key = RenderCacheKey("ramrpg:iron_sword", 1, 0, "en_us", 0)
        var calls = 0

        val first = cache.get(key) { calls++; FakeItemStack() }
        val second = cache.get(key) { calls++; FakeItemStack() }

        assertEquals(1, calls, "the factory only runs once per key")
        assertNotSame(first, second, "each lookup hands out its own clone, never the cached instance")
    }

    @Test
    fun `a different locale on an otherwise identical key misses the cache`() {
        val cache = RenderCache()
        val en = RenderCacheKey("ramrpg:iron_sword", 1, 0, "en_us", 0)
        val fr = en.copy(viewerLocale = "fr_fr")
        var calls = 0

        cache.get(en) { calls++; FakeItemStack() }
        cache.get(fr) { calls++; FakeItemStack() }

        assertEquals(2, calls, "locale is part of the cache key, so each locale renders separately")
    }

    @Test
    fun `entries beyond capacity evict the least recently used key`() {
        val cache = RenderCache(cap = 1)
        val a = RenderCacheKey("a", 1, 0, "en_us", 0)
        val b = RenderCacheKey("b", 1, 0, "en_us", 0)
        var callsA = 0

        cache.get(a) { callsA++; FakeItemStack() }
        cache.get(b) { FakeItemStack() } // over capacity: evicts a
        cache.get(a) { callsA++; FakeItemStack() }

        assertEquals(2, callsA, "a was evicted, so its factory runs again")
        assertEquals(1, cache.size())
    }

    @Test
    fun `invalidateAll forces every key to re-render`() {
        val cache = RenderCache()
        val key = RenderCacheKey("ramrpg:iron_sword", 1, 0, "en_us", 0)
        var calls = 0
        cache.get(key) { calls++; FakeItemStack() }

        cache.invalidateAll()
        cache.get(key) { calls++; FakeItemStack() }

        assertEquals(2, calls)
        assertEquals(1, cache.size())
    }
}
