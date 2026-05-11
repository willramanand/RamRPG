/** QuestService implementation. Tracks per-player progress; dispatches rewards on completion. */
package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.api.quests.QuestDefinition
import dev.willram.ramrpg.api.quests.QuestGoal
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
import org.bukkit.entity.Player

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

class QuestService(
    private val registry: QuestRegistry,
    private val skills: SkillService,
    private val economy: EconomyService,
    private val store: PlayerStore,
    private val zone: java.time.ZoneId = java.time.ZoneId.of("UTC"),
) {
    private fun dayIndex(ms: Long): Long =
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

    fun onEntityKill(player: Player, profileId: String) = bump(player) { goal ->
        (goal as? QuestGoal.KillEntityProfile)?.profileId == profileId
    }

    fun onBlockBreak(player: Player, mat: org.bukkit.Material) = bump(player) { goal ->
        (goal as? QuestGoal.BreakBlocks)?.material == mat
    }

    fun onSkillXp(player: Player, skill: SkillKey, amount: Int) = bumpBy(player, amount) { goal ->
        (goal as? QuestGoal.GainSkillXp)?.skill == skill
    }

    private fun bump(player: Player, matches: (QuestGoal) -> Boolean) = bumpBy(player, 1, matches)

    private fun bumpBy(player: Player, delta: Int, matches: (QuestGoal) -> Boolean) {
        if (delta <= 0) return
        val data = store.require(player.uniqueId)
        for (q in registry.all()) {
            val keyStr = q.key.id.toString()
            if (keyStr in data.questCompleted) continue
            if (!matches(q.goal)) continue
            val now = (data.questProgress[keyStr] ?: 0) + delta
            if (now >= q.goal.target) {
                data.questProgress.remove(keyStr)
                data.questCompleted.add(keyStr)
                data.markDirty()
                complete(player, q)
            } else {
                data.questProgress[keyStr] = now
                data.markDirty()
            }
        }
    }

    private fun complete(player: Player, q: QuestDefinition) {
        player.sendMessage(Component.text("Quest complete: ", NamedTextColor.GOLD).append(q.displayName))
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

    fun progressOf(player: Player, key: QuestKey): Int =
        store.get(player.uniqueId)?.questProgress?.get(key.id.toString()) ?: 0

    fun completedBy(player: Player): Set<QuestKey> =
        store.get(player.uniqueId)?.questCompleted
            ?.mapNotNull { runCatching { QuestKey(dev.willram.ramcore.content.ContentId.parse(it)) }.getOrNull() }
            ?.toSet() ?: emptySet()

    /** Clear progress + completed for daily quests if calendar day rolled in configured zone. */
    fun rolloverDailies(player: Player, nowMs: Long = System.currentTimeMillis()) {
        val data = store.require(player.uniqueId)
        val nowDay = dayIndex(nowMs)
        val lastDay = if (data.lastDailyReset == 0L) -1L else dayIndex(data.lastDailyReset)
        if (nowDay <= lastDay) return
        val dailyIds = registry.all().filter { it.daily }.map { it.key.id.toString() }.toSet()
        var changed = false
        for (id in dailyIds) {
            if (data.questCompleted.remove(id)) changed = true
            if (data.questProgress.remove(id) != null) changed = true
        }
        data.lastDailyReset = nowMs
        if (changed) data.markDirty()
    }

    /** Wipe progress + completion for a single quest (e.g. /abandon). */
    fun abandon(player: Player, key: QuestKey): Boolean {
        val data = store.require(player.uniqueId)
        val keyStr = key.id.toString()
        val a = data.questProgress.remove(keyStr) != null
        val b = data.questCompleted.remove(keyStr)
        val changed = a || b
        if (changed) data.markDirty()
        return changed
    }

}
