package dev.willram.ramrpg

import dev.willram.ramcore.packet.InMemoryPacketVisualTransport
import dev.willram.ramcore.packet.PacketVisualAction
import dev.willram.ramcore.testkit.FakeItemStack
import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.core.rendering.EntityItemVisualRenderer
import dev.willram.ramrpg.core.rendering.PacketItemRenderer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * WP-1.6b: dropped-item-entity and item-frame display items are re-rendered through RamCore's
 * packet-visual layer (dev.willram.ramcore.packet) - InMemoryPacketVisualTransport in particular -
 * instead of a hand-rolled, hardcoded entity-metadata index (rework.md item 4). Pure/off-server:
 * PacketItemRenderer is faked so no real ItemStack meta/ItemFactory access happens.
 */
class EntityDataRenderTest {

    private fun viewer(): Player = ProxyFakes.proxy(
        Player::class.java,
        mapOf("getUniqueId" to UUID.randomUUID(), "getName" to "Steve"),
    )

    /** A renderer that swaps [from] for [to] by reference, and leaves anything else untouched. */
    private fun swapRenderer(from: ItemStack, to: ItemStack): PacketItemRenderer = object : PacketItemRenderer {
        override fun render(viewer: Player, item: ItemStack): ItemStack = if (item === from) to else item
        override fun renderNested(viewer: Player, item: ItemStack): ItemStack = item
        override fun invalidate() {}
    }

    @Test
    fun `a changed entity item records a metadata preview operation on the transport`() {
        val dropped = FakeItemStack()
        val rendered = FakeItemStack()
        val transport = InMemoryPacketVisualTransport()
        val visuals = EntityItemVisualRenderer(swapRenderer(dropped, rendered), transport)
        val player = viewer()

        val op = visuals.renderEntityItem(player, 42, dropped)

        assertEquals(PacketVisualAction.METADATA_PREVIEW, op?.action())
        assertEquals(42, op?.entityId())
        assertSame(rendered, op?.data()?.get(EntityItemVisualRenderer.ITEM_METADATA_KEY))

        val sent = transport.sent()
        assertEquals(1, sent.size, "exactly one logical operation is sent for the change")
        assertEquals(player.uniqueId, sent[0].viewer().id())
        assertSame(op, sent[0].operation())
    }

    @Test
    fun `an unchanged entity item sends nothing and returns null`() {
        val untouched = FakeItemStack()
        val transport = InMemoryPacketVisualTransport()
        // The fake renderer never matches `untouched`, so render() returns it unchanged.
        val visuals = EntityItemVisualRenderer(swapRenderer(FakeItemStack(), FakeItemStack()), transport)

        val op = visuals.renderEntityItem(viewer(), 7, untouched)

        assertNull(op)
        assertTrue(transport.sent().isEmpty(), "nothing to preview when rendering made no change")
    }

    @Test
    fun `separate entities and viewers each get their own operation`() {
        val itemA = FakeItemStack()
        val itemB = FakeItemStack()
        val renderedA = FakeItemStack()
        val renderedB = FakeItemStack()
        val transport = InMemoryPacketVisualTransport()
        val renderer = object : PacketItemRenderer {
            override fun render(viewer: Player, item: ItemStack): ItemStack = when {
                item === itemA -> renderedA
                item === itemB -> renderedB
                else -> item
            }
            override fun renderNested(viewer: Player, item: ItemStack): ItemStack = item
            override fun invalidate() {}
        }
        val visuals = EntityItemVisualRenderer(renderer, transport)
        val viewerA = viewer()
        val viewerB = viewer()

        visuals.renderEntityItem(viewerA, 1, itemA)
        visuals.renderEntityItem(viewerB, 2, itemB)

        val sent = transport.sent()
        assertEquals(2, sent.size)
        assertEquals(1, sent[0].operation().entityId())
        assertEquals(2, sent[1].operation().entityId())
        assertEquals(viewerA.uniqueId, sent[0].viewer().id())
        assertEquals(viewerB.uniqueId, sent[1].viewer().id())
    }
}
