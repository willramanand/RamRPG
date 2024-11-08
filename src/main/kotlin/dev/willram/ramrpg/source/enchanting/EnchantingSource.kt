package dev.willram.ramrpg.source.enchanting

import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source

enum class EnchantingSource(override val defaultXp: Double) : Source {
    WEAPON_PER_LEVEL(50.0),
    ARMOR_PER_LEVEL(75.0),
    TOOL_PER_LEVEL(60.0),
    BOOK_PER_LEVEL(40.0);

    override val skill: Skill
        get() = Skill.ENCHANTING
}