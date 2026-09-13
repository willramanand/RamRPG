package dev.willram.ramrpg

import dev.willram.ramcore.reward.RewardContext
import dev.willram.ramcore.reward.RewardOutcome
import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemInstanceInit
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.core.rewards.RpgItemRewardFactory
import dev.willram.ramrpg.core.services.ItemDefinitionRegistryImpl
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.BasicConfigurationNode
import org.spongepowered.configurate.ConfigurationNode

/**
 * WP-1.2b: the `rpg_item` RewardAction contract, entirely off-server.
 *
 * As in [SkillXpRewardTest], `validate()`/`apply()` never touch Bukkit statics for the subjects used
 * here. Applying the action dispatches onto the player's TaskContext via [PlatformScheduler]. Unlike
 * `skill_xp`, the dispatched task cannot be RUN under a unit test: it calls
 * [ItemInstanceService.create], which builds a real [ItemStack] and therefore needs `Bukkit.getItemFactory()`
 * (M10 -- the same reason WP-1.1a's loot tests assert at the ItemDto layer rather than a built stack).
 * So [RecordingScheduler.execute] is turned off and this asserts the Folia-safe *dispatch* -- the grant
 * is submitted to the player's context and never runs inline; the actual create+inventory grant is a
 * SMOKE_TEST.md line, not a unit test. Count/param parsing is covered by RpgRewardFactoryParseTest.
 */
class RpgItemRewardTest {

    private val ironSword = ItemDefinition(
        key = ItemKey.of("ramrpg", "iron_sword"),
        displayName = Component.text("Iron Sword"),
        material = Material.IRON_SWORD,
        rarity = Rarity.COMMON,
        categories = setOf(ItemCategory.SWORD),
    )

    private lateinit var scheduler: RecordingScheduler
    private lateinit var itemInstances: RecordingItemInstances
    private lateinit var itemDefs: ItemDefinitionRegistryImpl
    private lateinit var factory: RpgItemRewardFactory

    @BeforeEach
    fun setUp() {
        scheduler = RecordingScheduler().apply { execute = false } // building the ItemStack needs a live server
        itemInstances = RecordingItemInstances()
        itemDefs = ItemDefinitionRegistryImpl().apply { register("test", ironSword) }
        factory = RpgItemRewardFactory(itemDefs, itemInstances, scheduler)
    }

    private fun params(item: String = "ramrpg:iron_sword", count: Int? = null): ConfigurationNode =
        BasicConfigurationNode.root().apply {
            node("item").set(item)
            if (count != null) node("count").set(count)
        }

    @Test
    fun `validate fails without an online player subject`() {
        val action = factory.create(params())
        val errors = action.validate(RewardContext.of("test"))
        assertFalse(errors.isEmpty())
    }

    @Test
    fun `validate passes with an online player subject`() {
        val action = factory.create(params())
        val player = ProxyFakes.stub(Player::class.java)
        val errors = action.validate(RewardContext.of("test").withSubject(player))
        assertTrue(errors.isEmpty(), errors.toString())
    }

    @Test
    fun `apply fails when the subject is not online, without dispatching or creating`() {
        val action = factory.create(params())
        val outcome = action.apply(RewardContext.of("test"))
        assertEquals(RewardOutcome.failed("rpg_item", "subject is not online"), outcome)
        assertEquals(0, scheduler.runCount)
        assertEquals(0, itemInstances.createCalls)
    }

    @Test
    fun `apply dispatches the grant to the player's TaskContext and reports success`() {
        val action = factory.create(params(count = 3))
        val player = ProxyFakes.stub(Player::class.java)

        val outcome = action.apply(RewardContext.of("test").withSubject(player))

        assertEquals(RewardOutcome.success("rpg_item"), outcome)
        assertEquals(1, scheduler.runCount)
        assertEquals(player, scheduler.lastPlayer)
        // the grant is dispatched to the player's context, never executed inline (Folia rule): the
        // ItemStack build only happens on that context, so nothing is created synchronously here.
        assertEquals(0, itemInstances.createCalls)
    }
}

/**
 * An [ItemInstanceService] test double. [create] is only reachable when a scheduler actually runs the
 * dispatched task; these tests keep it un-run (see the class doc), so it records calls but is never
 * expected to build a stack off-server.
 */
class RecordingItemInstances : ItemInstanceService {
    var createCalls = 0
    var lastDefinition: ItemDefinition? = null

    override fun create(def: ItemDefinition, init: ItemInstanceInit): ItemStack {
        createCalls++
        lastDefinition = def
        return ItemStack(def.material)
    }

    override fun identify(item: ItemStack?): dev.willram.ramrpg.api.items.ItemInstanceData? = null
    override fun read(item: ItemStack): dev.willram.ramrpg.api.items.ItemInstanceData? = null
    override fun write(item: ItemStack, data: dev.willram.ramrpg.api.items.ItemInstanceData): ItemStack = item
}
