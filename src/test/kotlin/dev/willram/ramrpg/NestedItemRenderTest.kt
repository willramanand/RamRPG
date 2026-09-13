package dev.willram.ramrpg

import dev.willram.ramcore.testkit.FakeItemStack
import dev.willram.ramrpg.core.rendering.renderNestedItems
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-1.6b: [renderNestedItems] is the pure core PacketItemRendererImpl.renderNested drives against
 * a real BundleMeta/ShulkerBox inventory (bundle/shulker "render each contained stack" gap from
 * rework.md item 4). Building BundleMeta needs a live server's ItemFactory (see
 * RpgItemLootRewardTest), so this tests the list-diffing core directly with FakeItemStack payload
 * markers - opaque, server-free stand-ins for "a contained stack" - instead.
 */
class NestedItemRenderTest {

    @Test
    fun `unchanged contents return the exact same list instance`() {
        val items = listOf(FakeItemStack(), FakeItemStack())

        val result = renderNestedItems(items) { it }

        assertSame(items, result, "no reason to rebuild the list (or re-apply meta) when nothing changed")
    }

    @Test
    fun `each contained stack is rendered independently and only real changes propagate`() {
        val a = FakeItemStack()
        val b = FakeItemStack()
        val renderedA = FakeItemStack()
        val seen = mutableListOf<ItemStack>()

        val result = renderNestedItems(listOf(a, b)) { stack ->
            seen += stack
            if (stack === a) renderedA else stack
        }

        assertEquals(2, seen.size, "every contained stack is offered to the renderer")
        assertSame(a, seen[0])
        assertSame(b, seen[1])
        assertNotSame(listOf(a, b), result, "a real change forces a new list")
        assertSame(renderedA, result[0], "the changed stack is replaced")
        assertSame(b, result[1], "an untouched stack keeps its identity")
    }

    @Test
    fun `empty contents render to an empty list`() {
        val result = renderNestedItems(emptyList()) { it }

        assertTrue(result.isEmpty())
    }
}
