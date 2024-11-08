package dev.willram.ramrpg.skills

import dev.willram.ramrpg.skills.combat.CombatListeners
import dev.willram.ramrpg.skills.defense.DefenseListeners
import dev.willram.ramrpg.skills.mining.MiningListeners

class SkillListeners {

    companion object {
        fun register() {
            CombatListeners.register()
            DefenseListeners.register()
            MiningListeners.register()
        }
    }
}