/** BossBar showing current skill XP progress, restored from last-active on join. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.builtin.identity.RamSkills
import dev.willram.ramrpg.core.storage.PlayerStore
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BossBarUi(
    private val skills: SkillService,
    private val registry: SkillRegistry,
    private val store: PlayerStore,
) {
    private val bars = ConcurrentHashMap<UUID, BossBar>()
    private val activeSkill = ConcurrentHashMap<UUID, SkillKey>()

    fun register() {
        Events.subscribe(PlayerJoinEvent::class.java).handler { e ->
            store.get(e.player.uniqueId)?.lastActiveSkillId
                ?.let { runCatching { SkillKey(ContentId.parse(it)) }.getOrNull() }
                ?.let { update(e.player, it) }
        }
        Events.subscribe(PlayerQuitEvent::class.java).handler { e ->
            bars.remove(e.player.uniqueId)?.let { e.player.hideBossBar(it) }
            activeSkill.remove(e.player.uniqueId)
        }
        Events.subscribe(EntityDeathEvent::class.java).handler { e ->
            val killer = e.entity.killer ?: return@handler
            update(killer, RamSkills.COMBAT)
        }
    }

    fun onXpGain(player: Player, skill: SkillKey, @Suppress("UNUSED_PARAMETER") amount: Double) {
        update(player, skill)
    }

    fun shutdown() {
        for ((id, bar) in bars) {
            org.bukkit.Bukkit.getPlayer(id)?.hideBossBar(bar)
        }
        bars.clear()
        activeSkill.clear()
    }

    fun update(player: Player, skill: SkillKey) {
        val def = registry.get(skill) ?: return
        val bar = bars.getOrPut(player.uniqueId) {
            val b = BossBar.bossBar(Component.text(""), 0f, def.barColor, BossBar.Overlay.PROGRESS)
            player.showBossBar(b)
            b
        }
        val lvl = skills.level(player, skill)
        val xp = skills.xp(player, skill)
        val needed = def.xpCurve.xpToReach(lvl).coerceAtLeast(1.0)
        val pct = (xp / needed).toFloat().coerceIn(0f, 1f)
        bar.name(Component.text()
            .append(def.displayName.color(NamedTextColor.YELLOW))
            .append(Component.text(" Lv $lvl  "))
            .append(Component.text("${"%.0f".format(xp)}/${"%.0f".format(needed)}", NamedTextColor.GRAY))
            .build())
        bar.color(def.barColor)
        bar.progress(pct)
        activeSkill[player.uniqueId] = skill
        store.get(player.uniqueId)?.let {
            it.lastActiveSkillId = skill.id.toString()
            it.markDirty()
        }
    }
}
