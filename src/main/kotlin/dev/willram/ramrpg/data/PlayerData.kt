package dev.willram.ramrpg.data

import dev.willram.ramcore.configurate.objectmapping.ConfigSerializable
import dev.willram.ramcore.data.DataItem
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.stats.Stat
import org.bukkit.entity.Player
import java.util.*

@ConfigSerializable
class PlayerData : DataItem() {
    var currentMana = 0.0
    val skillsLvl: MutableMap<Skill, Int> = EnumMap(Skill.entries.associateWith { 1 })
    val skillsXp: MutableMap<Skill, Double> = EnumMap(Skill.entries.associateWith { 0.0 })

    @Transient
    var statPoints: MutableMap<Stat, Double> = EnumMap(Stat.entries.associateWith { 0.0 })

    @Transient
    var player: Player? = null
}