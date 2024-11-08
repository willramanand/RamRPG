package dev.willram.ramrpg.ui

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.utils.Formatter
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import org.bukkit.Bukkit
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

class BossBar(private val plugin: RamRPG) {
    private val bossBars: MutableMap<Player, MutableMap<Skill, BossBar>> = HashMap()
    private val currentActions: MutableMap<Player, MutableMap<Skill, Int>> = HashMap()
    private val checkCurrentActions: MutableMap<Player, MutableMap<Skill, Int>> = HashMap()
    private val stayTime = 60L
    private val barStyle = BossBar.Overlay.PROGRESS

    fun load() {

        for (entry in bossBars.entries) {
            val bossBarsEntries = entry.value
            for (innerEntry in bossBarsEntries) {
                //innerEntry.value.isVisible = false
                //innerEntry.value.removeAll()
                innerEntry.value.viewers().removeAll { true }
            }
        }
        bossBars.clear()

        Events.subscribe(PlayerQuitEvent::class.java)
            .handler { e ->
                val player = e.player
                bossBars.remove(player)
                currentActions.remove(player)
                checkCurrentActions.remove(player)
            }
    }

    fun sendBossBar(player: Player, skill: Skill, currentXp: Double, levelXp: Double, level: Int, maxed: Boolean) {
        val color = getColor(skill)
        val style = getStyle(skill)
        var bossBar: BossBar?

        if (!bossBars.containsKey(player)) bossBars[player] = EnumMap(Skill::class.java)
        bossBar = bossBars[player]!![skill]
        // If player does not have a boss bar in that skill
        if (bossBar == null) {
            val nameValue: Component = if (!maxed) {
                if (plugin.conf.xpModifier > 1) {
                    MiniMessage.miniMessage().deserialize("<gold>${plugin.skills[skill].displayName} $level <gray>(${Formatter.decimalFormat(currentXp, 1)}/${Formatter.bigNumber(
                        levelXp.toLong()
                    )} XP) <gold>${plugin.conf.xpModifier}x")
                } else {
                    MiniMessage.miniMessage().deserialize("<gold>${plugin.skills[skill].displayName} $level <gray>(${Formatter.decimalFormat(currentXp, 1)}/${Formatter.bigNumber(
                        levelXp.toLong()
                    )} XP)")
                }
            } else {
                MiniMessage.miniMessage().deserialize("<gold>${plugin.skills[skill].displayName} $level <gray>(MAXED)")
            }
            var progress = 0f
            progress = if (progress in 0.0..1.0) {
                (currentXp / levelXp).toFloat()
            } else {
                1.0f
            }

            if (progress > 1.0f) {
                progress = 1.0f
            }

            bossBar = BossBar.bossBar(nameValue, progress, color, style)
            bossBar.addViewer(player)
            // Add to maps
            bossBars[player]!![skill] = bossBar
        } else {
            val nameValue: Component;
            if (!maxed) {
                if (plugin.conf.xpModifier > 1) {
                    bossBar.name()
                    nameValue = MiniMessage.miniMessage().deserialize("<gold>${plugin.skills[skill].displayName} $level <gray>(${Formatter.decimalFormat(currentXp, 1)}/${Formatter.bigNumber(
                        levelXp.toLong()
                    )} XP) <gold>${plugin.conf.xpModifier}x")
                } else {
                    nameValue = MiniMessage.miniMessage().deserialize("<gold>${plugin.skills[skill].displayName} $level <gray>(${Formatter.decimalFormat(currentXp, 1)}/${Formatter.bigNumber(
                        levelXp.toLong()
                    )} XP)")
                }
            } else {
                nameValue = MiniMessage.miniMessage().deserialize("<gold>${plugin.skills[skill].displayName} $level <gray>(MAXED)")
            }
            bossBar.name(nameValue)
            var progress = 0f
            progress = if (progress in 0.0..1.0) {
                (currentXp / levelXp).toFloat()
            } else {
                1.0f
            }

            if (progress > 1.0f) {
                progress = 1.0f
            }

            bossBar.progress(progress)
            bossBar.addViewer(player)
        }
        if (!currentActions.containsKey(player)) currentActions[player] = EnumMap(Skill::class.java)
        val currentAction = currentActions[player]!![skill]
        if (currentAction != null) {
            currentActions[player]!![skill] = currentAction + 1
        } else {
            currentActions[player]!![skill] = 0
        }
        scheduleHide(player, skill, bossBar)
    }

    fun incrementAction(player: Player, skill: Skill) {
        if (!checkCurrentActions.containsKey(player)) checkCurrentActions[player] = EnumMap(Skill::class.java)
        val currentAction = checkCurrentActions[player]!![skill]
        if (currentAction != null) {
            checkCurrentActions[player]!![skill] = currentAction + 1
        } else {
            checkCurrentActions[player]!![skill] = 0
        }
    }

    private fun scheduleHide(player: Player, skill: Skill, bossBar: BossBar) {
        val multiCurrentActions = currentActions[player] ?: return
        val currentAction = multiCurrentActions[skill]!!
        Schedulers.sync().runLater({
            val multiCurrentActionsInner = currentActions[player] ?: return@runLater
            if (currentAction == multiCurrentActionsInner[skill]) {
                //bossBar.isVisible = false
                bossBar.removeViewer(player)
                checkCurrentActions.remove(player)
            }
        }, stayTime)
    }


    private fun getColor(skill: Skill): BossBar.Color {
        return BossBar.Color.valueOf(plugin.skills[skill].barColor.name)
    }

    private fun getStyle(skill: Skill): BossBar.Overlay {
        return barStyle
    }

    fun getCurrentAction(player: Player, skill: Skill): Int {
        val multiCurrentActions: Map<Skill, Int>? = checkCurrentActions[player]
        if (multiCurrentActions != null) {
            return multiCurrentActions[skill]!!
        }
        return -1
    }
}