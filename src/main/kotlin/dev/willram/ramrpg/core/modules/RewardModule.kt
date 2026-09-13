/**
 * WP-1.7b: the reward subsystem seam. Takes over the direct `RamRPG.kt` registration WP-1.2b added
 * (the N3 migration): builds RamCore's [RewardActionFactories] with the standard actions plus the
 * four RPG factories (`skill_xp`, `rpg_item`, and the `buff`/`perk_point` stubs). Built at enable()
 * because it must evaluate `EconomyService.ramCoreEconomy` -- a lazy Vault probe -- only after Vault
 * has had a chance to enable (which is why it was never keyed in `load()`).
 *
 * Nothing consumes [factories] yet (QuestService still carries its own reward model), so this module
 * simply anchors a populated registry that later reward-consuming WPs (4.1a buff, 5.1a perk) build on.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.reward.RewardActionFactories
import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.core.rewards.BuffRewardFactory
import dev.willram.ramrpg.core.rewards.PerkPointRewardFactory
import dev.willram.ramrpg.core.rewards.RpgItemRewardFactory
import dev.willram.ramrpg.core.rewards.SkillXpRewardFactory
import dev.willram.ramrpg.core.services.RpgServiceKeys

class RewardModule(private val ctx: ServiceContext) : TerminableModule {

    /** The populated reward-action registry. Exposed for later reward-consuming WPs. */
    var factories: RewardActionFactories? = null
        private set

    override fun setup(consumer: TerminableConsumer) {
        val economy = ctx.service(RpgServiceKeys.ECONOMY)
        val skillService = ctx.service(RpgServiceKeys.SKILL_SERVICE)
        val itemDefs = ctx.service(RpgServiceKeys.ITEM_DEFINITIONS)
        val itemInstances = ctx.service(RpgServiceKeys.ITEM_INSTANCES)
        val platform = ctx.service(RpgServiceKeys.PLATFORM)

        factories = RewardActionFactories.standard(economy.ramCoreEconomy)
            .register(SkillXpRewardFactory(skillService, platform))
            .register(RpgItemRewardFactory(itemDefs, itemInstances, platform))
            .register(BuffRewardFactory())
            .register(PerkPointRewardFactory())
    }

    companion object {
        const val OWNER = "ramrpg-reward"
    }
}
