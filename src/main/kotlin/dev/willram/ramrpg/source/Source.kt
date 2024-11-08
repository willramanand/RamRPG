package dev.willram.ramrpg.source

import dev.willram.ramrpg.skills.Skill

interface Source {
    val skill: Skill
    val defaultXp: Double
}