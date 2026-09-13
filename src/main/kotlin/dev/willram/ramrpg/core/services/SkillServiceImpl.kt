/**
 * SkillService implementation. Adds xp through curve, cascades level-ups,
 * fires onLevelUp / onXpGain callbacks for UI + persistence wiring.
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.XpContext
import dev.willram.ramrpg.api.skills.XpSource
import dev.willram.ramrpg.core.storage.PlayerStore
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class SkillServiceImpl(
    private val registry: SkillRegistry,
    private val store: PlayerStore,
    private val onLevelUp: (Player, SkillKey, Int) -> Unit = { _, _, _ -> },
    private val onXpGain: (Player, SkillKey, Double) -> Unit = { _, _, _ -> },
) : SkillService {

    /**
     * XP-gain reactions registered at enable()-time by subsystem modules (WP-1.7b). This is how the
     * per-player UI (BossBarUi, from UiModule) and quest progress (QuestModule) observe XP gains
     * without the plugin holding a reference to those enable-time objects: instead of routing the
     * reaction through a `RamRPG` field, each owning module calls [addXpGainListener] from its own
     * module setup. Fired after the constructor [onXpGain] callback, on the player's thread.
     */
    private val xpGainListeners = CopyOnWriteArrayList<(Player, SkillKey, Double) -> Unit>()

    /** Registers a reaction fired on every XP gain. See [xpGainListeners]. */
    fun addXpGainListener(listener: (Player, SkillKey, Double) -> Unit) {
        xpGainListeners += listener
    }

    override fun addXp(p: Player, src: XpSource, target: LivingEntity?, multiplier: Double) {
        val def = registry.get(src.skill) ?: return
        val ctx = object : XpContext {
            override val player = p; override val source = src
            override val target = target; override val multiplier = multiplier
        }
        val gained = src.xp(ctx) * multiplier
        if (gained <= 0) return
        val data = store.require(p.uniqueId)
        var lvl = data.getLevel(src.skill)
        var xp = data.getXp(src.skill) + gained
        while (lvl < def.maxLevel) {
            val needed = def.xpCurve.xpToReach(lvl)
            if (xp < needed) break
            xp -= needed
            lvl += 1
            onLevelUp(p, src.skill, lvl)
        }
        data.setLevel(src.skill, lvl)
        data.setXp(src.skill, xp)
        onXpGain(p, src.skill, gained)
        for (listener in xpGainListeners) listener(p, src.skill, gained)
    }

    override fun level(p: Player, skill: SkillKey): Int = store.get(p.uniqueId)?.getLevel(skill) ?: 1
    override fun xp(p: Player, skill: SkillKey): Double = store.get(p.uniqueId)?.getXp(skill) ?: 0.0
    override fun setLevel(p: Player, skill: SkillKey, level: Int) { store.require(p.uniqueId).setLevel(skill, level) }
}
