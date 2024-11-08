package dev.willram.ramrpg.levels

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.utils.TxtUtils
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.data.PlayerData
import dev.willram.ramrpg.events.SkillLevelUpEvent
import dev.willram.ramrpg.events.XpGainEvent
import dev.willram.ramrpg.skills.Skill
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import java.time.Duration

class Leveler(private val plugin: RamRPG) {

    private val xpReqs: XPReqs = XPReqs(plugin)

    fun loadLevelReqs() {
        xpReqs.loadXpRequirements()
    }

    //Method for adding xp with a defined amount
    fun addXp(player: Player, skill: Skill, amount: Double) {
        if (player.isInvulnerable || player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
        val playerData = plugin.players.get(player.uniqueId) ?: return
        if (amount == 0.0) return
        val event = XpGainEvent(player, skill, amount)
        Events.call(event)
        if (!event.isCancelled) {
            //Adds xp
            val modifier: Double = plugin.conf.xpModifier
            playerData.skillsXp[skill] = playerData.skillsXp[skill]!! + (modifier * event.amount)
            //Check if player leveled up
            checkLevelUp(player, skill)
            // Sends boss bar if enabled
            sendBossBar(player, skill, playerData)
        }
    }

    //Method for adding xp with a defined amount
    fun addUnmodifiedXp(player: Player, skill: Skill, amount: Double) {
        val data = plugin.players.get(player.uniqueId) ?: return
        if (amount == 0.0) return
        val event = XpGainEvent(player, skill, amount)
        Events.call(event)
        if (!event.isCancelled) {
            //Adds xp
            data.skillsXp[skill] = data.skillsXp[skill]!! + event.amount
            //Check if player leveled up
            checkLevelUp(player, skill)
            // Sends boss bar if enabled
            sendBossBar(player, skill, data)
        }
    }

    //Method for setting xp with a defined amount
    fun setXp(player: Player, skill: Skill, amount: Double) {
        val data = plugin.players.get(player.uniqueId) ?: return
        //Sets Xp
        data.skillsXp[skill] = amount
        //Check if player leveled up
        checkLevelUp(player, skill)
        // Sends boss bar if enabled
        sendBossBar(player, skill, data)
    }

    private fun sendBossBar(player: Player, skill: Skill, playerData: PlayerData) {
        // Check whether boss bar should update
        plugin.bossBar.incrementAction(player, skill)
        val currentAction: Int = plugin.bossBar.getCurrentAction(player, skill)
        if (currentAction != -1) {
            val level: Int = playerData.skillsLvl[skill]!!
            val notMaxed =
                xpReqs.getListSize(skill) > playerData.skillsLvl[skill]!! - 1 && level < xpReqs.getMaxLevel(skill)
            if (notMaxed) {
                plugin.bossBar.sendBossBar(
                    player,
                    skill,
                    playerData.skillsXp[skill]!!,
                    xpReqs.getXpRequired(skill, level + 1),
                    level,
                    false
                )
            } else {
                plugin.bossBar.sendBossBar(player, skill, 1.0, 1.0, level, true)
            }
        }
    }

    private fun checkLevelUp(player: Player, skill: Skill) {
        val playerData = plugin.players.get(player.uniqueId) ?: return
        val currentLevel: Int = playerData.skillsLvl[skill]!!
        val currentXp: Double = playerData.skillsXp[skill]!!
        if (currentLevel < xpReqs.getMaxLevel(skill)) { //Check max level options
            if (xpReqs.getListSize(skill) > currentLevel - 1) {
                if (currentXp >= xpReqs.getXpRequired(skill, currentLevel + 1)) {
                    levelUpSkill(player, playerData, skill)
                }
            }
        }
    }

    private fun levelUpSkill(player: Player, playerData: PlayerData, skill: Skill) {
        val currentXp: Double = playerData.skillsXp[skill]!!
        val level: Int = playerData.skillsLvl[skill]!! + 1

        playerData.skillsXp[skill] = currentXp - xpReqs.getXpRequired(skill, level)
        playerData.skillsLvl[skill] = level
        // Adds money rewards if enabled
        if (plugin.vaultEnabled) {
            val economy: Economy = plugin.econ!!
            val base = 10.0
            val multiplier = 5.0
            economy.depositPlayer(player, base + (multiplier * level))
        }
        val event = SkillLevelUpEvent(player, skill, level)
        Events.call(event)
        sendTitle(player, skill, level)
        playSound(player)
        getLevelUpMessage(player, playerData, skill)
        Schedulers.sync().runLater({
            checkLevelUp(player, skill)
        }, 20L)
    }

    private fun sendTitle(player: Player, skill: Skill, level: Int) {
        val title = Title.title(
            MiniMessage.miniMessage().deserialize("<gold>${plugin.skills[skill].displayName} <green>Level Up"),
            MiniMessage.miniMessage().deserialize("<gold>${level - 1} ➜ $level"),
            Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(3750), Duration.ofMillis(250))
        )
        player.showTitle(title)
    }

    private fun playSound(player: Player) {
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1f, 0.5f)
    }

    private fun getLevelUpMessage(player: Player, playerData: PlayerData, skill: Skill) {
        player.sendMessage(TxtUtils.generateHeaderComponent("${plugin.skills[skill].displayName} ${playerData.skillsLvl[skill]}"))
        for (stat in skill.stats) {
            val statPerLevel: Double = plugin.stats[stat].perLvl
            player.sendRichMessage("${plugin.stats[stat].prefix}${plugin.stats[stat].symbol} ${plugin.stats[stat].displayName}: + $statPerLevel")
        }
        player.sendMessage("")
    }

    val xpRequirements: XPReqs
        get() = xpReqs
}