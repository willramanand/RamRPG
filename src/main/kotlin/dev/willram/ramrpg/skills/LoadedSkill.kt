package dev.willram.ramrpg.skills

import dev.willram.ramcore.configurate.objectmapping.ConfigSerializable
import dev.willram.ramcore.data.DataItem
import dev.willram.ramrpg.stats.Stat
import org.bukkit.boss.BarColor
import java.util.*

@ConfigSerializable
class LoadedSkill : DataItem() {
    var displayName: String = ""
    var desc: String = ""
    var barColor: BarColor = BarColor.BLUE
    var maxLvl: Int = 50
    var base: Double = 100.0
    var multiplier: Double = 100.0
    var stats: MutableList<Stat> = ArrayList()
}