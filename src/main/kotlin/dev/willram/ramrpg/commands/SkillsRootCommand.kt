package dev.willram.ramrpg.commands

import dev.willram.ramcore.commands.CommandContext
import dev.willram.ramcore.commands.RamCommand
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.stats.Stat
import dev.willram.ramrpg.ui.SkillsMenu

class SkillsRootCommand(private val plugin: RamRPG) : RamCommand(true, false, "ramrpg.skills", "") {
    override fun perform(c: CommandContext) {
        val menu = SkillsMenu(c.player()!!)
        menu.open()
    }

    override fun tabCompletes(c: CommandContext): MutableList<String> {
        return mutableListOf()
    }


}