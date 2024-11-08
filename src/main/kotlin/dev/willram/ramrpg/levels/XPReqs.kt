package dev.willram.ramrpg.levels

import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import java.util.*


class XPReqs(private val plugin: RamRPG) {

    private var skillXpRequirements: MutableMap<Skill, List<Double>> = EnumMap(Skill::class.java)
    private var skillXpBase: MutableMap<Skill, Double> = EnumMap(Skill::class.java)
    private var skillXpMultiplier: MutableMap<Skill, Double> = EnumMap(Skill::class.java)
    private var skillMaxLvls: MutableMap<Skill, Int> = EnumMap(Skill::class.java)

    fun loadXpRequirements() {
        for (skill in Skill.entries) {
            skillMaxLvls[skill] = plugin.skills[skill].maxLvl
            skillXpBase[skill] = plugin.skills[skill].base
            skillXpMultiplier[skill] = plugin.skills[skill].multiplier
        }
        addXpRequirements()
    }

    private fun addXpRequirements() {
        for (skill in Skill.entries) {
            val skillList: MutableList<Double> = ArrayList()
            val base = skillXpBase.getOrDefault(skill, 100.0)
            val mult = skillXpMultiplier.getOrDefault(skill, 100.0)
            for (i in 0 until skillMaxLvls[skill]!! - 1) {
                skillList.add(calculateXpforLevel(i, base, mult))
            }
            skillXpRequirements[skill] = skillList
        }
    }

    fun getXpRequired(skill: Skill, level: Int): Double {
        val skillList = skillXpRequirements[skill]
        if (skillList != null) {
            return if (skillList.size > level - 2) {
                skillList[level - 2]
            } else {
                0.0
            }
        }
        return 0.0
    }

    fun getListSize(skill: Skill): Int {
        val skillList = skillXpRequirements[skill]
        if (skillList != null) {
            return skillList.size
        }
        return 0
    }

    fun calculateXpforLevel(lvl: Int, base: Double, mult: Double): Double {
        return (mult * lvl) + base
    }

    fun getMaxLevel(skill: Skill): Int {
        return skillMaxLvls[skill]!!
    }
}