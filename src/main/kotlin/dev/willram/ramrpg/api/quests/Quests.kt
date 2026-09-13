/** Quest framework: definitions + per-player progress tracking. */
package dev.willram.ramrpg.api.quests

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.objective.ObjectiveAction
import dev.willram.ramcore.objective.ObjectiveDefinition
import dev.willram.ramcore.objective.ObjectiveTask
import dev.willram.ramrpg.api.identity.SkillKey
import net.kyori.adventure.text.Component
import org.bukkit.Material

@JvmInline value class QuestKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = QuestKey(ContentId.of(ns, v)) }
}

/**
 * A quest goal, mapped onto one RamCore [ObjectiveTask]. Progress is tracked by RamCore's
 * ObjectiveTracker, not by RamRPG. Targets are lowercased to satisfy the objective target format;
 * a `*` target (e.g. [KillEntityProfile] profileId "*") matches any event of that action.
 */
sealed interface QuestGoal {
    val target: Int
    fun toTask(): ObjectiveTask
    data class KillEntityProfile(val profileId: String, override val target: Int) : QuestGoal {
        override fun toTask(): ObjectiveTask = ObjectiveTask.of("kill", ObjectiveAction.KILL, profileId, target.toLong())
    }
    data class BreakBlocks(val material: Material, override val target: Int) : QuestGoal {
        override fun toTask(): ObjectiveTask = ObjectiveTask.of("break", ObjectiveAction.INTERACT_BLOCK, material.name.lowercase(), target.toLong())
    }
    data class GainSkillXp(val skill: SkillKey, override val target: Int) : QuestGoal {
        override fun toTask(): ObjectiveTask = ObjectiveTask.of("xp", ObjectiveAction.RUN_ACTION, skill.id.toString(), target.toLong())
    }
}

sealed interface QuestReward {
    data class Currency(val amount: Double) : QuestReward
    data class Xp(val skill: SkillKey, val amount: Double) : QuestReward
}

data class QuestDefinition(
    val key: QuestKey,
    val displayName: Component,
    val description: Component,
    val goal: QuestGoal,
    val rewards: List<QuestReward> = emptyList(),
    val daily: Boolean = false,
    /** Free-text grouping label (e.g. "combat", "gathering"). */
    val category: String = "general",
) {
    /** The RamCore objective this quest wraps; the ObjectiveTracker tracks its progress. */
    fun objective(): ObjectiveDefinition = ObjectiveDefinition.builder(key.id).task(goal.toTask()).build()
}

interface QuestRegistry {
    fun register(owner: String, def: QuestDefinition)
    fun unregisterOwner(owner: String): Int
    fun get(key: QuestKey): QuestDefinition?
    fun all(): Collection<QuestDefinition>
}
