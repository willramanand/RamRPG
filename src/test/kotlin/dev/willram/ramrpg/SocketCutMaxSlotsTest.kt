package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.crafting.OutcomePlan
import dev.willram.ramrpg.api.crafting.RecipeCost
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.plan
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.RarityRules
import dev.willram.ramrpg.api.items.SocketData
import dev.willram.ramrpg.core.config.specs.RecipeSpec
import dev.willram.ramrpg.core.crafting.SocketOutcomes
import net.kyori.adventure.text.TranslatableComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * WP-3.3d: an `add_socket` craft may never push an item past its per-item socket-slot CAP. The cap is the
 * EXISTING shared [RarityRules.socketCap] ladder (COMMON 1 .. MYTHIC 6), reused -- not forked -- via
 * [SocketOutcomes]. The crux: [RecipeOutcome.plan] (api/) only appends sockets and does NOT know the cap,
 * so [SocketOutcomes.validateAddSocket] is the gate a cut must pass first; these tests prove the gate
 * stops a cut the raw plan would happily perform. Also asserts the shipped `cut_socket` recipe encodes the
 * documented base cost. PURE -- no live server (only the [Rarity]/[ContentId] value types are touched).
 */
class SocketCutMaxSlotsTest {

    private val slotType = ContentId.of("ramrpg", "generic")

    private fun instance(socketCount: Int): ItemInstanceData =
        ItemInstanceData(
            identity = ItemIdentity(ItemKey.of("ramrpg", "test_item")),
            sockets = List(socketCount) { SocketData(slotType, gem = null) },
        )

    /** Every shipped recipe in the packaged sockets.conf, parsed through the production deserializer. */
    private val recipes: List<RecipeSpec> =
        childrenOf("content/recipes/sockets.conf").map { RecipeSpec.deserialize(it) }

    @Test
    fun `slot cap ladder is the shared RarityRules ladder, not a fork`() {
        for (r in Rarity.entries) {
            assertEquals(RarityRules.socketCap(r), SocketOutcomes.slotCap(r), "cap for $r must match RarityRules")
        }
    }

    @Test
    fun `canAddSockets respects the cap`() {
        assertEquals(true, SocketOutcomes.canAddSockets(currentSlots = 0, count = 1, cap = 1))
        assertEquals(false, SocketOutcomes.canAddSockets(currentSlots = 1, count = 1, cap = 1))
        assertEquals(true, SocketOutcomes.canAddSockets(currentSlots = 2, count = 2, cap = 4))
        assertEquals(false, SocketOutcomes.canAddSockets(currentSlots = 2, count = 3, cap = 4))
        assertEquals(false, SocketOutcomes.canAddSockets(currentSlots = 0, count = 0, cap = 4)) // non-positive
    }

    @Test
    fun `validateAddSocket rejects a non-positive count`() {
        assertEquals(SocketOutcomes.SocketOpError.NON_POSITIVE_COUNT, SocketOutcomes.validateAddSocket(instance(0).sockets, 0, Rarity.EPIC))
    }

    @Test
    fun `add socket cannot exceed the cap even though plan would append past it`() {
        // COMMON caps at ONE socket. An item already holding one is at the cap.
        val full = instance(1)
        assertEquals(
            SocketOutcomes.SocketOpError.SLOT_CAP_EXCEEDED,
            SocketOutcomes.validateAddSocket(full.sockets, count = 1, rarity = Rarity.COMMON),
        )
        // Proof the cap lives in the helper: the raw outcome plan (which does NOT cap) would append anyway.
        val ungated = RecipeOutcome.AddSocket(slotType, count = 1).plan(full, 0.0) as OutcomePlan.Modify
        assertEquals(2, ungated.result.sockets.size)
    }

    @Test
    fun `a cut within the cap is allowed and the plan appends exactly the requested slots`() {
        val two = instance(2)
        // EPIC caps at 4: cutting two more is exactly at the cap.
        assertNull(SocketOutcomes.validateAddSocket(two.sockets, count = 2, rarity = Rarity.EPIC))
        val plan = RecipeOutcome.AddSocket(slotType, count = 2).plan(two, 0.0) as OutcomePlan.Modify
        assertEquals(4, plan.result.sockets.size)
        assertEquals(listOf(SocketData(slotType), SocketData(slotType)), plan.result.sockets.drop(2))
        // One more than the cap is rejected.
        assertEquals(
            SocketOutcomes.SocketOpError.SLOT_CAP_EXCEEDED,
            SocketOutcomes.validateAddSocket(two.sockets, count = 3, rarity = Rarity.EPIC),
        )
    }

    @Test
    fun `remainingSlots is clamped at zero`() {
        assertEquals(3, SocketOutcomes.remainingSlots(currentSlots = 1, cap = 4))
        assertEquals(0, SocketOutcomes.remainingSlots(currentSlots = 6, cap = 4)) // never negative
    }

    @Test
    fun `add socket cost scales with the number of existing slots`() {
        assertEquals(RecipeCost(money = 500.0, experienceLevels = 3), SocketOutcomes.addSocketCost(0))
        assertEquals(RecipeCost(money = 1000.0, experienceLevels = 3), SocketOutcomes.addSocketCost(1))
        assertEquals(RecipeCost(money = 1500.0, experienceLevels = 3), SocketOutcomes.addSocketCost(2))
    }

    @Test
    fun `shipped cut_socket recipe encodes the base cut cost and a cap-agnostic outcome`() {
        val cut = recipes.single { it.outcome is RecipeOutcome.AddSocket }
        val outcome = cut.outcome as RecipeOutcome.AddSocket
        assertEquals(slotType, outcome.socketType)
        assertEquals(1, outcome.count)
        // Cost mirrors SocketOutcomes.addSocketCost(0) exactly -- content and helper share one source of truth.
        assertEquals(SocketOutcomes.addSocketCost(0), cut.cost)
    }

    @Test
    fun `slot-cap error maps to its socket lang key`() {
        val msg = SocketOutcomes.message(SocketOutcomes.SocketOpError.SLOT_CAP_EXCEEDED)
        assertEquals("ramrpg.crafting.socket.cap_reached", (msg as TranslatableComponent).key())
    }

    private companion object {
        fun childrenOf(resource: String): List<ConfigurationNode> {
            val stream = SocketCutMaxSlotsTest::class.java.classLoader.getResourceAsStream(resource)
                ?: error("shipped resource not on the classpath: $resource")
            val root = stream.use {
                HoconConfigurationLoader.builder()
                    .source { BufferedReader(InputStreamReader(it, Charsets.UTF_8)) }
                    .build()
                    .load()
            }
            return root.childrenMap().values.toList()
        }
    }
}
