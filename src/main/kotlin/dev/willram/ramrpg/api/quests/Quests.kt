/** Quest framework: definitions + per-player progress tracking. */
package dev.willram.ramrpg.api.quests

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.SkillKey
import net.kyori.adventure.text.Component
import org.bukkit.Material

@JvmInline value class QuestKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = QuestKey(ContentId.of(ns, v)) }
}

sealed interface QuestGoal {
    val target: Int
    data class KillEntityProfile(val profileId: String, override val target: Int) : QuestGoal
    data class BreakBlocks(val material: Material, override val target: Int) : QuestGoal
    data class GainSkillXp(val skill: SkillKey, override val target: Int) : QuestGoal
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
)

interface QuestRegistry {
    fun register(owner: String, def: QuestDefinition)
    fun unregisterOwner(owner: String): Int
    fun get(key: QuestKey): QuestDefinition?
    fun all(): Collection<QuestDefinition>
}
