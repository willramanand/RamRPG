package dev.willram.ramrpg

import dev.willram.ramcore.reward.RewardContext
import dev.willram.ramcore.reward.RewardOutcome
import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.XpContext
import dev.willram.ramrpg.api.skills.XpSource
import dev.willram.ramrpg.core.platform.Cancellable
import dev.willram.ramrpg.core.platform.PlatformScheduler
import dev.willram.ramrpg.core.rewards.SkillXpRewardFactory
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.BasicConfigurationNode
import org.spongepowered.configurate.ConfigurationNode

/**
 * WP-1.2b: the `skill_xp` RewardAction contract, entirely off-server.
 *
 * [dev.willram.ramcore.reward.RewardSubjects.onlinePlayer] short-circuits on an empty subject before
 * ever reaching `Bukkit.getPlayer`, and a [Player] subject resolves without touching Bukkit statics at
 * all -- so `validate()`/`apply()` are safe to call directly here. Applying the action never grants XP
 * inline; it dispatches onto the player's TaskContext via [PlatformScheduler] (Folia-safety rule: the
 * factory validates off-thread, the action executes on the player's context). [RecordingScheduler]
 * runs the dispatched task synchronously so the test asserts the *grant that actually happens* --
 * `SkillService.addXp` with the right skill and amount -- not merely that a task was submitted.
 */
class SkillXpRewardTest {

    private lateinit var scheduler: RecordingScheduler
    private lateinit var skills: RecordingSkillService
    private lateinit var factory: SkillXpRewardFactory

    @BeforeEach
    fun setUp() {
        scheduler = RecordingScheduler()
        skills = RecordingSkillService()
        factory = SkillXpRewardFactory(skills, scheduler)
    }

    private fun params(skill: String = "ramrpg:combat", amount: Double = 25.0): ConfigurationNode =
        BasicConfigurationNode.root().apply {
            node("skill").set(skill)
            node("amount").set(amount)
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
    fun `apply fails when the subject is not online, without dispatching or granting`() {
        val action = factory.create(params())
        val outcome = action.apply(RewardContext.of("test"))
        assertEquals(RewardOutcome.failed("skill_xp", "subject is not online"), outcome)
        assertEquals(0, scheduler.runCount)
        assertEquals(0, skills.addXpCalls)
    }

    @Test
    fun `apply dispatches to the player's TaskContext and grants the configured skill xp`() {
        val action = factory.create(params(skill = "ramrpg:combat", amount = 25.0))
        val player = ProxyFakes.stub(Player::class.java)

        val outcome = action.apply(RewardContext.of("test").withSubject(player))

        assertEquals(RewardOutcome.success("skill_xp"), outcome)
        assertEquals(1, scheduler.runCount)
        assertEquals(player, scheduler.lastPlayer)
        // the dispatched task actually ran and granted XP through SkillService -- delete or corrupt
        // the addXp call, the skill, or the amount and this fails.
        assertEquals(1, skills.addXpCalls)
        assertEquals(player, skills.lastPlayer)
        val src = skills.lastSource!!
        assertEquals("ramrpg:combat", src.skill.toString())
        assertEquals(25.0, src.xp(ProxyFakes.stub(XpContext::class.java)))
    }
}

/**
 * A [PlatformScheduler] test double that records dispatch to a player and, when [execute] is true
 * (the default), runs the task synchronously so tests can assert the effect the dispatched work has,
 * not just that it was queued. `skill_xp` executes it to prove the grant actually runs; `rpg_item`
 * turns it off because building the [org.bukkit.inventory.ItemStack] the grant hands to the inventory
 * needs a live server (M10), so that path is asserted only for dispatch here and end-to-end in SMOKE_TEST.md.
 */
class RecordingScheduler : PlatformScheduler {
    var lastPlayer: Player? = null
    var runCount = 0
    var execute = true

    override fun runGlobal(task: Runnable) {}
    override fun runAsync(task: Runnable) {}
    override fun runForEntity(entity: Entity, task: Runnable) {}
    override fun runForPlayer(player: Player, task: Runnable) {
        lastPlayer = player
        runCount++
        if (execute) task.run()
    }
    override fun runAtLocation(loc: Location, task: Runnable) {}
    override fun runLaterGlobal(delayTicks: Long, task: Runnable) {}
    override fun runForPlayerLater(player: Player, delayTicks: Long, task: Runnable) {}
    override fun repeatGlobal(periodTicks: Long, task: () -> Unit): Cancellable = throw UnsupportedOperationException()
    override fun repeatForEntity(e: Entity, periodTicks: Long, task: () -> Unit): Cancellable = throw UnsupportedOperationException()
}

/** A [SkillService] test double that records every [addXp] call for assertion. */
class RecordingSkillService : SkillService {
    var addXpCalls = 0
    var lastPlayer: Player? = null
    var lastSource: XpSource? = null

    override fun addXp(p: Player, src: XpSource, target: LivingEntity?, multiplier: Double) {
        addXpCalls++
        lastPlayer = p
        lastSource = src
    }

    override fun level(p: Player, skill: SkillKey): Int = 0
    override fun xp(p: Player, skill: SkillKey): Double = 0.0
    override fun setLevel(p: Player, skill: SkillKey, level: Int) {}
}
