package dev.willram.ramrpg.skills

import dev.willram.ramrpg.skills.agility.AgilityListeners
import dev.willram.ramrpg.skills.alchemy.AlchemyListeners
import dev.willram.ramrpg.skills.combat.CombatListeners
import dev.willram.ramrpg.skills.cooking.CookingListeners
import dev.willram.ramrpg.skills.defense.DefenseListeners
import dev.willram.ramrpg.skills.enchanting.EnchantingListeners
import dev.willram.ramrpg.skills.excavation.ExcavationListeners
import dev.willram.ramrpg.skills.farming.FarmingListeners
import dev.willram.ramrpg.skills.fishing.FishingListeners
import dev.willram.ramrpg.skills.mining.MiningListeners
import dev.willram.ramrpg.skills.woodcutting.WoodcuttingListeners

class SkillListeners {

    companion object {
        fun register() {
            AgilityListeners.register()
            AlchemyListeners.register()
            CombatListeners.register()
            CookingListeners.register()
            DefenseListeners.register()
            EnchantingListeners.register()
            ExcavationListeners.register()
            FarmingListeners.register()
            FishingListeners.register()
            MiningListeners.register()
            WoodcuttingListeners.register()
        }
    }
}