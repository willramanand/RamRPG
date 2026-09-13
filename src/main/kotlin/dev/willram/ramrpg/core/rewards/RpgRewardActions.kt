/**
 * RewardAction implementations for the reward types RamRPG registers into RamCore's
 * [dev.willram.ramcore.reward.RewardActionFactories] extension point (WP-1.2b). Every action here
 * follows the same Folia-safe split as the rest of RamRPG: the factory's `create()` validates params
 * off-thread (see [RpgRewardFactories]), and `apply()` only ever mutates player/world state by
 * dispatching onto the player's TaskContext through [PlatformScheduler] -- never directly, and never
 * on a raw Bukkit scheduler.
 *
 * [UnimplementedRewardAction] backs the `buff` and `perk_point` stub factories: it always fails both
 * `validate()` and `apply()` so a content author can never end up with a reward that silently does
 * nothing. Because [dev.willram.ramcore.reward.RewardEngine] validates every entry in a plan before
 * executing any of them, one stub entry blocks the whole plan from running -- intentional per WP-1.2b
 * rule 8, and documented in docs/design/1.2b-reward-types.md.
 */
package dev.willram.ramrpg.core.rewards

import dev.willram.ramcore.reward.RewardAction
import dev.willram.ramcore.reward.RewardContext
import dev.willram.ramcore.reward.RewardOutcome
import dev.willram.ramcore.reward.RewardSubjects
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.XpContext
import dev.willram.ramrpg.api.skills.XpSource
import dev.willram.ramrpg.core.platform.PlatformScheduler

/** Grants skill XP through [SkillService.addXp]. Registered as the `skill_xp` reward type. */
class SkillXpRewardAction(
    private val skillService: SkillService,
    private val platform: PlatformScheduler,
    private val skill: SkillKey,
    private val amount: Double,
) : RewardAction {

    override fun validate(context: RewardContext): List<String> =
        if (RewardSubjects.onlinePlayer(context).isEmpty()) {
            listOf("$TYPE reward needs an online player subject")
        } else {
            emptyList()
        }

    override fun apply(context: RewardContext): RewardOutcome {
        val player = RewardSubjects.onlinePlayer(context).orElse(null)
            ?: return RewardOutcome.failed(TYPE, "subject is not online")
        platform.runForPlayer(player) { skillService.addXp(player, xpSource()) }
        return RewardOutcome.success(TYPE)
    }

    private fun xpSource(): XpSource = object : XpSource {
        override val key: XpSourceKey = SOURCE_KEY
        override val skill: SkillKey = this@SkillXpRewardAction.skill
        override fun xp(ctx: XpContext): Double = amount
    }

    companion object {
        const val TYPE = "skill_xp"
        val SOURCE_KEY: XpSourceKey = XpSourceKey.of("ramrpg", "reward")
    }
}

/**
 * Gives a fresh [ItemInstanceService.create] instance of [definition] (its own instance id and stat
 * rolls, not a shared template). Registered as the `rpg_item` reward type; RamCore's built-in `item`
 * action deliberately stays consumer-owned because building an RPG item instance needs this server's
 * [ItemInstanceService].
 */
class RpgItemRewardAction(
    private val itemInstances: ItemInstanceService,
    private val platform: PlatformScheduler,
    private val definition: ItemDefinition,
    private val count: Int,
) : RewardAction {

    override fun validate(context: RewardContext): List<String> =
        if (RewardSubjects.onlinePlayer(context).isEmpty()) {
            listOf("$TYPE reward needs an online player subject")
        } else {
            emptyList()
        }

    override fun apply(context: RewardContext): RewardOutcome {
        val player = RewardSubjects.onlinePlayer(context).orElse(null)
            ?: return RewardOutcome.failed(TYPE, "subject is not online")
        platform.runForPlayer(player) {
            val stack = itemInstances.create(definition).also { it.amount = count }
            player.inventory.addItem(stack).values.forEach { leftover ->
                player.world.dropItemNaturally(player.location, leftover)
            }
        }
        return RewardOutcome.success(TYPE)
    }

    companion object { const val TYPE = "rpg_item" }
}

/**
 * Backs a stub reward type ([RpgRewardFactories]'s `buff`/`perk_point` factories) that is not wired to
 * a real system yet. Never applies anything: `validate()` always reports [reason], so
 * [dev.willram.ramcore.reward.RewardEngine] refuses the whole plan rather than silently no-opping one
 * entry.
 */
class UnimplementedRewardAction(private val type: String, private val reason: String) : RewardAction {
    override fun validate(context: RewardContext): List<String> = listOf(reason)
    override fun apply(context: RewardContext): RewardOutcome = RewardOutcome.failed(type, reason)
}
