package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.crafting.OutcomePlan
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.plan
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.SocketData
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.builtin.identity.RamStats
import dev.willram.ramrpg.builtin.sockets.BuiltinGems
import dev.willram.ramrpg.core.config.specs.RecipeSpec
import dev.willram.ramrpg.core.crafting.SocketOutcomes
import dev.willram.ramrpg.core.services.GemRegistryImpl
import dev.willram.ramrpg.core.services.socketStatsFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * WP-3.3d: `insert_gem` sets the gem at a socket index and `remove_gem` clears it, each producing the
 * correct pure [OutcomePlan.Modify] -- and, crucially, the gem's stats keep flowing through the UNCHANGED
 * [socketStatsFor] provider path (no bespoke socket-crafting stat path). Also proves the shipped
 * `sockets.conf` insert recipes reuse EXISTING builtin [GemKey] ids and the documented costs. PURE -- no
 * live server; [socketStatsFor] is exercised directly off the plan-produced instance.
 */
class GemInsertRemoveTest {

    private val slotType = ContentId.of("ramrpg", "generic")
    private val ruby = GemKey.of("ramrpg", "ruby")

    private val state = object : ItemRequirementState {
        override fun skillLevel(skill: dev.willram.ramrpg.api.identity.SkillKey): Int = 0
        override fun statValue(stat: StatKey): Double = 0.0
    }

    private fun instance(sockets: List<SocketData>): ItemInstanceData =
        ItemInstanceData(identity = ItemIdentity(ItemKey.of("ramrpg", "test_item")), sockets = sockets)

    /** Every shipped recipe in the packaged sockets.conf, parsed through the production deserializer. */
    private val recipes: List<RecipeSpec> =
        childrenOf("content/recipes/sockets.conf").map { RecipeSpec.deserialize(it) }

    @Test
    fun `insert gem plan sets the gem at the socket index`() {
        val withSocket = instance(listOf(SocketData(slotType, gem = null)))
        val plan = RecipeOutcome.InsertGem(ruby, socketIndex = 0).plan(withSocket, 0.0) as OutcomePlan.Modify
        assertEquals(ruby.id, plan.result.sockets[0].gem)
        assertEquals(slotType, plan.result.sockets[0].key) // slot type/id preserved
    }

    @Test
    fun `remove gem plan clears the gem at the socket index`() {
        val filled = instance(listOf(SocketData(slotType, gem = ruby.id)))
        val plan = RecipeOutcome.RemoveGem(socketIndex = 0).plan(filled, 0.0) as OutcomePlan.Modify
        assertNull(plan.result.sockets[0].gem)
        assertEquals(slotType, plan.result.sockets[0].key) // the empty slot itself remains
    }

    @Test
    fun `insert into an occupied socket is rejected and an empty one is allowed`() {
        val filled = listOf(SocketData(slotType, gem = ruby.id))
        val empty = listOf(SocketData(slotType, gem = null))
        assertEquals(SocketOutcomes.SocketOpError.SOCKET_OCCUPIED, SocketOutcomes.validateInsertGem(filled, 0))
        assertNull(SocketOutcomes.validateInsertGem(empty, 0))
    }

    @Test
    fun `remove from an empty socket is rejected and a filled one is allowed`() {
        val empty = listOf(SocketData(slotType, gem = null))
        val filled = listOf(SocketData(slotType, gem = ruby.id))
        assertEquals(SocketOutcomes.SocketOpError.SOCKET_EMPTY, SocketOutcomes.validateRemoveGem(empty, 0))
        assertNull(SocketOutcomes.validateRemoveGem(filled, 0))
    }

    @Test
    fun `an out-of-range index is rejected by validation and by the plan`() {
        val one = instance(listOf(SocketData(slotType, gem = null)))
        assertEquals(SocketOutcomes.SocketOpError.INDEX_OUT_OF_RANGE, SocketOutcomes.validateInsertGem(one.sockets, 2))
        assertEquals(SocketOutcomes.SocketOpError.INDEX_OUT_OF_RANGE, SocketOutcomes.validateRemoveGem(one.sockets, 2))
        assertNull(RecipeOutcome.InsertGem(ruby, socketIndex = 2).plan(one, 0.0))
        assertNull(RecipeOutcome.RemoveGem(socketIndex = 2).plan(one, 0.0))
    }

    @Test
    fun `socket stat provider output is unchanged whether a gem was set by a craft or directly`() {
        val gems = GemRegistryImpl().also { BuiltinGems.registerAll(it) }
        val expected = listOf(
            StatModifier(RamStats.STRENGTH, 5.0, ModifierOperation.ADD, ModifierSource(SourceType.SOCKET, ruby.id)),
        )

        // Gem set by an insert_gem craft ...
        val emptySocket = instance(listOf(SocketData(slotType, gem = null)))
        val crafted = (RecipeOutcome.InsertGem(ruby, socketIndex = 0).plan(emptySocket, 0.0) as OutcomePlan.Modify).result
        val viaCraft = socketStatsFor(def = null, data = crafted, state = state, gems = gems)

        // ... produces byte-identical modifiers to a gem set directly on the instance.
        val direct = instance(listOf(SocketData(slotType, gem = ruby.id)))
        val viaDirect = socketStatsFor(def = null, data = direct, state = state, gems = gems)

        assertEquals(viaDirect, viaCraft)
        assertEquals(expected, viaCraft)

        // Removing the gem withdraws exactly that contribution again.
        val removed = (RecipeOutcome.RemoveGem(socketIndex = 0).plan(crafted, 0.0) as OutcomePlan.Modify).result
        assertTrue(socketStatsFor(def = null, data = removed, state = state, gems = gems).isEmpty())
    }

    @Test
    fun `every shipped insert_gem reuses a registered builtin gem key and the insert cost`() {
        val gems = GemRegistryImpl().also { BuiltinGems.registerAll(it) }
        val inserts = recipes.filter { it.outcome is RecipeOutcome.InsertGem }
        assertTrue(inserts.isNotEmpty(), "sockets.conf must define insert_gem recipes")
        for (recipe in inserts) {
            val gem = (recipe.outcome as RecipeOutcome.InsertGem).gem
            assertNotNull(gems.get(gem), "insert_gem ${recipe.key} references unregistered gem $gem")
            assertEquals(SocketOutcomes.insertGemCost(), recipe.cost, "insert_gem ${recipe.key} cost must match the model")
        }
    }

    @Test
    fun `shipped remove_gem encodes the remove cost`() {
        val remove = recipes.single { it.outcome is RecipeOutcome.RemoveGem }
        assertEquals(SocketOutcomes.removeGemCost(), remove.cost)
    }

    private companion object {
        fun childrenOf(resource: String): List<ConfigurationNode> {
            val stream = GemInsertRemoveTest::class.java.classLoader.getResourceAsStream(resource)
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
