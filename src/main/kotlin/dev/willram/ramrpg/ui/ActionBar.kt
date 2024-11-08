package dev.willram.ramrpg.ui

import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.scheduler.Task
import dev.willram.ramcore.utils.Formatter
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.data.PlayerData
import dev.willram.ramrpg.stats.Stat
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable

class ActionBar(private val plugin: RamRPG) {
    private val currentAction = HashMap<Player, Int>()
    private val isPaused = HashSet<Player>()
    private val timer = HashMap<Player, Int>()

    fun startUpdateActionBar() {
        Schedulers.async().runRepeating({ _: Task ->
            for (player in Bukkit.getOnlinePlayers()) {
                if (!currentAction.containsKey(player)) {
                    currentAction[player] = 0
                }
                if (!isPaused.contains(player)) {
                    val playerData = plugin.players.get(player.uniqueId)
                    if (playerData != null) {
                        sendActionBar(player, "<red>${getHp(player)}/${getMaxHp(player)}❤                <aqua>${getMana(playerData)}/${getMaxMana(playerData)}۞")
                    }
                }
            }
        }, 0L, 2L)
        Schedulers.async().runRepeating({ _: Task ->
            for (player in Bukkit.getOnlinePlayers()) {
                val time = timer[player]
                if (time != null) {
                    if (time != 0) {
                        timer[player] = time - 1
                    }
                } else {
                    timer[player] = 0
                }
            }
        }, 0L, 2L)
    }

    fun sendAbilityActionBar(player: Player, message: String) {
        val playerData = plugin.players.get(player.uniqueId) ?: return
        sendActionBar(player, "<red>${getHp(player)}/${getMaxHp(player)}❤    <gold>${message}   <aqua>${getMana(playerData)}/${getMaxMana(playerData)}۞")
        setPaused(player, 40)
    }

    private fun getHp(player: Player): String {
        return Math.round(player.health).toString()
    }

    private fun getMaxHp(player: Player): String {
        val attribute = player.getAttribute(Attribute.MAX_HEALTH)
        if (attribute != null) {
            return Formatter.decimalFormat(attribute.value, 1)
        }
        return ""
    }

    private fun getMana(playerData: PlayerData): String {
        return Formatter.decimalFormat(playerData.currentMana, 1)
    }

    private fun getMaxMana(playerData: PlayerData): String {
        return Formatter.decimalFormat(playerData.statPoints[Stat.WISDOM]!!, 1)
    }

    private fun sendActionBar(player: Player, message: String) {
        player.sendActionBar(MiniMessage.miniMessage().deserialize(message))
    }

    fun setPaused(player: Player, ticks: Int) {
        isPaused.add(player)
        val action = currentAction[player]
        if (action != null) {
            currentAction[player] = action + 1
        } else {
            currentAction[player] = 0
        }
        val thisAction = currentAction[player]!!
        object : BukkitRunnable() {
            override fun run() {
                val actionBarCurrentAction = currentAction[player]
                if (actionBarCurrentAction != null) {
                    if (thisAction == actionBarCurrentAction) {
                        isPaused.remove(player)
                    }
                }
            }
        }.runTaskLater(plugin, ticks.toLong())
    }

    fun resetActionBar(player: Player) {
        currentAction.remove(player)
        isPaused.remove(player)
        timer.clear()
    }

    fun resetActionBars() {
        currentAction.clear()
        isPaused.clear()
        timer.clear()
    }
}