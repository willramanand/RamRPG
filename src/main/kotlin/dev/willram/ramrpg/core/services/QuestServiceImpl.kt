/** QuestService implementation. Tracks per-player progress; dispatches rewards on completion. */
package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramcore.objective.ObjectiveAction
import dev.willram.ramcore.objective.ObjectiveEvent
import dev.willram.ramcore.objective.ObjectiveProgressKey
import dev.willram.ramcore.objective.ObjectiveProgressStore
import dev.willram.ramcore.objective.ObjectiveSubject
import dev.willram.ramcore.objective.ObjectiveTracker
import dev.willram.ramcore.objective.Objectives
import dev.willram.ramcore.store.Stores
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.api.quests.QuestDefinition
import dev.willram.ramrpg.api.quests.QuestKey
import dev.willram.ramrpg.api.quests.QuestRegistry
import dev.willram.ramrpg.api.quests.QuestReward
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.XpContext
import dev.willram.ramrpg.api.skills.XpSource
import dev.willram.ramrpg.core.listeners.EconomyService
import dev.willram.ramrpg.core.storage.PlayerStore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import java.nio.file.Path

class QuestRegistryImpl(
    private val backing: ContentRegistry<QuestDefinition> = ContentRegistry.create(QuestDefinition::class.java)
) : QuestRegistry {
    override fun register(owner: String, def: QuestDefinition) {
        backing.register(owner, ContentKey.of(def.key.id, QuestDefinition::class.java), def)
    }
    override fun unregisterOwner(owner: String) = backing.unregisterOwner(owner)
    override fun get(key: QuestKey) = backing.get(key.id).orElse(null)
    override fun all(): Collection<QuestDefinition> = backing.entries().map { it.value() }
}

/**
 * Quest progress is delegated to a RamCore [ObjectiveTracker] backed by a file store; RamRPG no longer
 * tracks it on the player profile. Each quest wraps one objective ([QuestDefinition.objective]); game
 * events become [ObjectiveEvent]s, and rewards fire on the update that completes an objective. Only the
 * daily-reset stamp still lives on [PlayerRpgData].
 */
class QuestService(
    private val registry: QuestRegistry,
    private val skills: SkillService,
    private val economy: EconomyService,
    private val store: PlayerStore,
    questDir: Path,
    private val zone: java.time.ZoneId = java.time.ZoneId.of("UTC"),
) {
    private val tracker: ObjectiveTracker = Objectives.tracker(
        ObjectiveProgressStore.of(Stores.file(questDir, ObjectiveProgressKey.keyCodec(), ObjectiveProgressStore.codec()))
    )

    /** The objective tracker backing quest progress. */
    fun tracker(): ObjectiveTracker = tracker

    /** Register every quest's objective and restore persisted progress. Call after quests are registered. */
    fun registerObjectives() {
        for (q in registry.all()) tracker.register(q.objective())
        tracker.load()
    }

    private fun subject(player: Player): ObjectiveSubject = ObjectiveSubject.player(player.uniqueId)
    private fun dayIndex(ms: Long): Long =
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

    fun onEntityKill(player: Player, profileId: String) =
        fire(player, ObjectiveEvent.of(subject(player), ObjectiveAction.KILL, profileId))

    fun onBlockBreak(player: Player, mat: Material) =
        fire(player, ObjectiveEvent.of(subject(player), ObjectiveAction.INTERACT_BLOCK, mat.name.lowercase()))

    fun onSkillXp(player: Player, skill: SkillKey, amount: Int) {
        if (amount <= 0) return
        fire(player, ObjectiveEvent.of(subject(player), ObjectiveAction.RUN_ACTION, skill.id.toString()).amount(amount.toLong()))
    }

    private fun fire(player: Player, event: ObjectiveEvent) {
        for (update in tracker.apply(event)) {
            if (!update.objectiveCompleted()) continue
            val q = registry.get(QuestKey(update.definition().id())) ?: continue
            complete(player, q)
        }
    }

    private fun complete(player: Player, q: QuestDefinition) {
        player.sendMessage(Component.translatable("ramrpg.quest.complete", q.displayName).color(NamedTextColor.GOLD))
        for (r in q.rewards) when (r) {
            is QuestReward.Currency -> if (economy.enabled) economy.deposit(player, r.amount)
            is QuestReward.Xp -> {
                val src = object : XpSource {
                    override val key = XpSourceKey.of("ramrpg", "quest")
                    override val skill = r.skill
                    override fun xp(ctx: XpContext) = r.amount
                }
                skills.addXp(player, src)
            }
        }
    }

    fun progressOf(player: Player, key: QuestKey): Int {
        val q = registry.get(key) ?: return 0
        val prog = tracker.existingProgress(subject(player), key.id).orElse(null) ?: return 0
        return q.objective().tasks().sumOf { prog.current(it.id()) }.toInt()
    }

    fun completedBy(player: Player): Set<QuestKey> =
        registry.all()
            .filter { q -> tracker.existingProgress(subject(player), q.key.id).map { it.completed(q.objective()) }.orElse(false) }
            .map { it.key }
            .toSet()

    /** Reset progress for daily quests if the calendar day rolled over in the configured zone. */
    fun rolloverDailies(player: Player, nowMs: Long = System.currentTimeMillis()) {
        val data = store.require(player.uniqueId)
        val nowDay = dayIndex(nowMs)
        val lastDay = if (data.lastDailyReset == 0L) -1L else dayIndex(data.lastDailyReset)
        if (nowDay <= lastDay) return
        val subj = subject(player)
        for (q in registry.all().filter { it.daily }) tracker.reset(subj, q.key.id)
        data.lastDailyReset = nowMs
        data.markDirty()
    }

    /** Wipe progress for a single quest (e.g. /abandon). Returns whether the player had any. */
    fun abandon(player: Player, key: QuestKey): Boolean {
        val subj = subject(player)
        val existed = tracker.existingProgress(subj, key.id).isPresent
        tracker.reset(subj, key.id)
        return existed
    }
}
