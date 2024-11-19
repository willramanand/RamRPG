package dev.willram.ramrpg.skills.defense

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.Source
import dev.willram.ramrpg.source.defense.DefenseSource
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent


class DefenseListeners {

    companion object {
        fun register() {
            Events.subscribe(EntityDamageByEntityEvent::class.java)
                .handler { e ->
                    if (e.isCancelled) return@handler
                    if (e.entity !is Player) return@handler
                    if (e.entity == e.damager) return@handler
                    val source: Source = if (e.damager is Player) {
                        DefenseSource.PLAYER_DAMAGE
                    } else {
                        DefenseSource.MOB_DAMAGE
                    }
                    RamRPG.get().leveler.addXp(
                        e.entity as Player,
                        Skill.DEFENSE,
                        e.damage * RamRPG.get().sources.getXp(source)
                    )
                }

        }
    }
}