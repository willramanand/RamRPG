package dev.willram.ramrpg.commands

import dev.willram.ramcore.commands.CommandContext
import dev.willram.ramcore.commands.RamCommand
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.stats.Stat

class SkillsRootCommand(private val plugin: RamRPG) : RamCommand(true, false, "ramrpg.skills", "") {

    init {

    }

    override fun perform(c: CommandContext) {
        val data = plugin.players.get(c.player()?.uniqueId)

        for (skill in Skill.entries) {
            c.msg("${plugin.skills.get(skill).displayName}: ${data.skillsLvl[skill]}")
        }

        for (stat in Stat.entries) {
            val statData = plugin.stats.get(stat)
            c.msg("${statData.prefix}${statData.displayName}${statData.symbol}: ${data.statPoints[stat]}")
        }
    }

    override fun tabCompletes(c: CommandContext): MutableList<String> {
        return mutableListOf()
    }


}