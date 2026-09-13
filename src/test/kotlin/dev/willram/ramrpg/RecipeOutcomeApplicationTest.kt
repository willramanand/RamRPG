package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.crafting.CraftContext
import dev.willram.ramrpg.api.crafting.CraftPlanner
import dev.willram.ramrpg.api.crafting.CraftResult
import dev.willram.ramrpg.api.crafting.OutcomePlan
import dev.willram.ramrpg.api.crafting.QualityRoll
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeKey
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.RecipeOutcomeKind
import dev.willram.ramrpg.api.crafting.StationKey
import dev.willram.ramrpg.api.crafting.plan
import dev.willram.ramrpg.api.crafting.requiresTargetItem
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.items.SocketData
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.core.config.specs.RecipeSpec
import dev.willram.ramrpg.core.config.specs.StationSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.StringReader

/**
 * WP-3.1a: each [RecipeOutcome] computes the correct PURE [OutcomePlan] -- assert the plan, never a
 * mutated [org.bukkit.inventory.ItemStack]. Runtime application of Reforge/Socket/Enchant is deferred
 * (WP-3.3c/3.3d/3.4b), but the plan for each already records the right key/value here. Also covers the
 * HOCON [StationSpec]/[RecipeSpec] parse of outcomes.
 */
class RecipeOutcomeApplicationTest {

    private val blade = ItemKey.of("ramrpg", "legendary_blade")
    private val steel = ItemKey.of("ramrpg", "steel_sword")

    private fun instance(
        durability: Int = 500,
        maxDurability: Int = 500,
        upgradeLevel: Int = 0,
        quality: Double = ItemInstanceData.DEFAULT_QUALITY,
        sockets: List<SocketData> = emptyList(),
        enchantments: Map<EnchantmentKey, Int> = emptyMap(),
        reforge: ReforgeKey? = null,
    ) = ItemInstanceData(
        identity = ItemIdentity(blade),
        upgradeLevel = upgradeLevel,
        reforge = reforge,
        sockets = sockets,
        enchantments = enchantments,
        quality = quality,
        maxDurability = maxDurability,
        durability = durability,
    )

    @Test
    fun `new item produces its output key at the rolled quality`() {
        val plan = RecipeOutcome.NewItem(steel, count = 3).plan(input = null, quality = 0.73)
        plan as OutcomePlan.Create
        assertEquals(steel, plan.item)
        assertEquals(0.73, plan.quality)
        assertEquals(3, plan.count)
    }

    @Test
    fun `transmute produces the target and preserves the input quality`() {
        val preserved = RecipeOutcome.Transmute(steel).plan(input = instance(quality = 0.8), quality = 0.1)
        preserved as OutcomePlan.Create
        assertEquals(steel, preserved.item)
        assertEquals(0.8, preserved.quality) // preserves input, ignores the passed roll

        val noInput = RecipeOutcome.Transmute(steel).plan(input = null, quality = 0.1) as OutcomePlan.Create
        assertEquals(ItemInstanceData.DEFAULT_QUALITY, noInput.quality)
    }

    @Test
    fun `upgrade adds to the input upgrade level`() {
        val plan = RecipeOutcome.UpgradeInput(upgradeLevels = 2).plan(instance(upgradeLevel = 1), 0.0)
        plan as OutcomePlan.Modify
        assertEquals(3, plan.result.upgradeLevel)
    }

    @Test
    fun `repair fills to max or adds a capped amount`() {
        val full = RecipeOutcome.Repair(amount = null).plan(instance(durability = 100, maxDurability = 500), 0.0)
        assertEquals(500, (full as OutcomePlan.Modify).result.durability)

        val partial = RecipeOutcome.Repair(amount = 50).plan(instance(durability = 100, maxDurability = 500), 0.0)
        assertEquals(150, (partial as OutcomePlan.Modify).result.durability)

        val overCap = RecipeOutcome.Repair(amount = 1000).plan(instance(durability = 100, maxDurability = 500), 0.0)
        assertEquals(500, (overCap as OutcomePlan.Modify).result.durability)
    }

    @Test
    fun `enchant records the enchantment and level`() {
        val sharp = EnchantmentKey.of("ramrpg", "sharpness")
        val plan = RecipeOutcome.Enchant(sharp, level = 4).plan(instance(), 0.0)
        assertEquals(4, (plan as OutcomePlan.Modify).result.enchantments[sharp])
    }

    @Test
    fun `reforge records the reforge key (runtime deferred to WP-3_3c)`() {
        val sharp = ReforgeKey(ContentId.of("ramrpg", "sharp"))
        val plan = RecipeOutcome.Reforge(sharp).plan(instance(), 0.0)
        assertEquals(sharp, (plan as OutcomePlan.Modify).result.reforge)
    }

    @Test
    fun `insert gem records the gem at the socket index (runtime deferred to WP-3_3d)`() {
        val ruby = GemKey.of("ramrpg", "ruby")
        val withSocket = instance(sockets = listOf(SocketData(ContentId.of("ramrpg", "slot"), gem = null)))
        val plan = RecipeOutcome.InsertGem(ruby, socketIndex = 0).plan(withSocket, 0.0)
        assertEquals(ruby.id, (plan as OutcomePlan.Modify).result.sockets[0].gem)
    }

    @Test
    fun `insert gem with an out-of-range index is invalid`() {
        val ruby = GemKey.of("ramrpg", "ruby")
        assertNull(RecipeOutcome.InsertGem(ruby, socketIndex = 2).plan(instance(sockets = emptyList()), 0.0))
    }

    @Test
    fun `add socket appends empty slots of the given type (runtime deferred to WP-3_3d)`() {
        val slot = ContentId.of("ramrpg", "generic")
        val before = instance(sockets = listOf(SocketData(ContentId.of("ramrpg", "existing"), gem = null)))
        val plan = RecipeOutcome.AddSocket(socketType = slot, count = 2).plan(before, 0.0)
        val result = (plan as OutcomePlan.Modify).result
        assertEquals(3, result.sockets.size) // 1 existing + 2 appended
        assertEquals(listOf(SocketData(slot, gem = null), SocketData(slot, gem = null)), result.sockets.drop(1))
    }

    @Test
    fun `remove gem clears the gem at the socket index (runtime deferred to WP-3_3d)`() {
        val ruby = ContentId.of("ramrpg", "ruby")
        val filled = instance(sockets = listOf(SocketData(ContentId.of("ramrpg", "slot"), gem = ruby)))
        val plan = RecipeOutcome.RemoveGem(socketIndex = 0).plan(filled, 0.0)
        assertNull((plan as OutcomePlan.Modify).result.sockets[0].gem)
    }

    @Test
    fun `remove gem with an out-of-range index is invalid`() {
        assertNull(RecipeOutcome.RemoveGem(socketIndex = 5).plan(instance(sockets = emptyList()), 0.0))
    }

    @Test
    fun `requiresTargetItem is true only for input-transforming outcomes`() {
        assertFalse(RecipeOutcome.NewItem(steel).requiresTargetItem)
        assertFalse(RecipeOutcome.Transmute(steel).requiresTargetItem)
        assertTrue(RecipeOutcome.UpgradeInput().requiresTargetItem)
        assertTrue(RecipeOutcome.Repair().requiresTargetItem)
        assertTrue(RecipeOutcome.Enchant(EnchantmentKey.of("ramrpg", "x")).requiresTargetItem)
        assertTrue(RecipeOutcome.Reforge(ReforgeKey(ContentId.of("ramrpg", "x"))).requiresTargetItem)
        assertTrue(RecipeOutcome.InsertGem(GemKey.of("ramrpg", "x")).requiresTargetItem)
        assertTrue(RecipeOutcome.AddSocket(ContentId.of("ramrpg", "x")).requiresTargetItem)
        assertTrue(RecipeOutcome.RemoveGem().requiresTargetItem)
    }

    @Test
    fun `craft planner stamps the deterministic quality roll onto a new item`() {
        val recipe = Recipe(
            key = RecipeKey.of("ramrpg", "make_steel"),
            station = StationKey.of("ramrpg", "smithing"),
            inputs = emptyList(),
            outcome = RecipeOutcome.NewItem(steel, qualitySkill = SkillKey.of("ramrpg", "combat")),
        )
        val ctx = CraftContext(requirementState = emptyState(), skillLevel = 60, seed = 123L)
        val result = CraftPlanner.plan(recipe, ctx) as CraftResult.Success
        val create = result.outcome as OutcomePlan.Create
        assertEquals(QualityRoll(60, 123L), create.quality)
        assertEquals(result.quality, create.quality)
    }

    // ---- HOCON outcome/station parsing ------------------------------------------------------------

    @Test
    fun `station spec parses blocks, rows and permitted kinds`() {
        val spec = StationSpec.deserialize(
            hocon(
                """
                id = "ramrpg:smithing"
                name = "<gold>Smithing"
                blocks = ["smithing_table"]
                rows = 5
                permitted-kinds = ["UPGRADE_INPUT", "REPAIR", "REFORGE", "INSERT_GEM", "ADD_SOCKET", "REMOVE_GEM"]
                """,
            ),
        )
        assertEquals(StationKey.of("ramrpg", "smithing"), spec.key)
        assertEquals(listOf("SMITHING_TABLE"), spec.blockMaterials)
        assertEquals(5, spec.rows)
        assertEquals(
            setOf(
                RecipeOutcomeKind.UPGRADE_INPUT, RecipeOutcomeKind.REPAIR, RecipeOutcomeKind.REFORGE,
                RecipeOutcomeKind.INSERT_GEM, RecipeOutcomeKind.ADD_SOCKET, RecipeOutcomeKind.REMOVE_GEM,
            ),
            spec.permittedKinds,
        )
    }

    @Test
    fun `recipe spec parses each outcome form`() {
        assertTrue(outcomeOf("{ type = new_item, item = \"ramrpg:steel_sword\", count = 2 }") is RecipeOutcome.NewItem)
        assertTrue(outcomeOf("{ type = upgrade_input, upgrade-levels = 2 }") is RecipeOutcome.UpgradeInput)
        assertTrue(outcomeOf("{ type = repair, amount = 200 }") is RecipeOutcome.Repair)
        assertTrue(outcomeOf("{ type = enchant, enchant = \"ramrpg:sharpness\", level = 3 }") is RecipeOutcome.Enchant)
        assertTrue(outcomeOf("{ type = reforge, reforge = \"ramrpg:sharp\" }") is RecipeOutcome.Reforge)
        assertTrue(outcomeOf("{ type = insert_gem, gem = \"ramrpg:ruby\", index = 1 }") is RecipeOutcome.InsertGem)
        assertTrue(outcomeOf("{ type = add_socket, socket-type = \"ramrpg:generic\", count = 2 }") is RecipeOutcome.AddSocket)
        assertTrue(outcomeOf("{ type = remove_gem, index = 3 }") is RecipeOutcome.RemoveGem)
        assertTrue(outcomeOf("{ type = transmute, item = \"ramrpg:mystery_box\" }") is RecipeOutcome.Transmute)

        val insert = outcomeOf("{ type = insert_gem, gem = \"ramrpg:ruby\", index = 1 }") as RecipeOutcome.InsertGem
        assertEquals(GemKey.of("ramrpg", "ruby"), insert.gem)
        assertEquals(1, insert.socketIndex)

        val add = outcomeOf("{ type = add_socket, socket-type = \"ramrpg:generic\", count = 2 }") as RecipeOutcome.AddSocket
        assertEquals(ContentId.of("ramrpg", "generic"), add.socketType)
        assertEquals(2, add.count)

        val remove = outcomeOf("{ type = remove_gem, index = 3 }") as RecipeOutcome.RemoveGem
        assertEquals(3, remove.socketIndex)
    }

    private fun outcomeOf(outcomeBlock: String): RecipeOutcome =
        RecipeSpec.deserialize(
            hocon(
                """
                id = "ramrpg:r"
                station = "ramrpg:smithing"
                inputs = []
                outcome $outcomeBlock
                """,
            ),
        ).outcome

    private fun emptyState() = object : ItemRequirementState {
        override fun skillLevel(skill: SkillKey) = 0
        override fun statValue(stat: dev.willram.ramrpg.api.identity.StatKey) = 0.0
    }

    private fun hocon(text: String): ConfigurationNode =
        HoconConfigurationLoader.builder()
            .source { BufferedReader(StringReader(text.trimIndent())) }
            .build()
            .load()
}
