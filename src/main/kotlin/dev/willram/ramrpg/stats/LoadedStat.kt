package dev.willram.ramrpg.stats

import dev.willram.ramcore.configurate.objectmapping.ConfigSerializable
import dev.willram.ramcore.data.DataItem

@ConfigSerializable
class LoadedStat : DataItem() {
    var base: Double = 0.0
    var perLvl: Double = 0.0
    var displayName: String = ""
    var modifierName: String = ""
    var symbol: String = ""
    var prefix: String = ""
}