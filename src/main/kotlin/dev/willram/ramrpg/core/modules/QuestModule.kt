/**
 * WP-1.7b: the quest subsystem seam (from WP-1.2a). Registers the builtin quest definitions, wires
 * the quest objectives, binds [QuestProgressListener], and observes skill-XP gains so XP-based quest
 * objectives advance -- the latter through [SkillServiceImpl.addXpGainListener] rather than a plugin
 * field, so the reaction is owned by this module.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramrpg.builtin.quests.BuiltinQuests
import dev.willram.ramrpg.core.listeners.QuestProgressListener
import dev.willram.ramrpg.core.services.RpgServiceKeys
import dev.willram.ramrpg.core.services.SkillServiceImpl

class QuestModule(private val ctx: ServiceContext) : TerminableModule {

    override fun setup(consumer: TerminableConsumer) {
        val questRegistry = ctx.service(RpgServiceKeys.QUEST_REGISTRY)
        val quests = ctx.service(RpgServiceKeys.QUESTS)
        val profiles = ctx.service(RpgServiceKeys.ENTITY_PROFILES)
        val skillService = ctx.service(RpgServiceKeys.SKILL_SERVICE)

        BuiltinQuests.registerAll(questRegistry)
        quests.registerObjectives()
        QuestProgressListener(quests, profiles).register(consumer)

        (skillService as? SkillServiceImpl)?.addXpGainListener { p, k, amt ->
            quests.onSkillXp(p, k, amt.toInt())
        }
    }

    companion object {
        const val OWNER = "ramrpg-quest"
    }
}
