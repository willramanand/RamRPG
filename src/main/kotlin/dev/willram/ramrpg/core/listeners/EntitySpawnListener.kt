/** Applies EntityProfile baseStats to spawned LivingEntities (HP/DAMAGE attribute, DEF in PDC). */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.pdc.PDCs
import dev.willram.ramcore.pdc.PdcKey
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.builtin.identity.RamStats
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.persistence.PersistentDataType

object EntityProfilePdc {
    val DEFENSE: PdcKey<Double, Double> = PdcKey.of("ramrpg", "mob_defense", PersistentDataType.DOUBLE)
    val DAMAGE: PdcKey<Double, Double> = PdcKey.of("ramrpg", "mob_damage", PersistentDataType.DOUBLE)
}

class EntitySpawnListener(private val profiles: EntityProfileRegistry) {
    fun register() {
        Events.subscribe(EntitySpawnEvent::class.java).handler { e ->
            val living = e.entity as? LivingEntity ?: return@handler
            val profile = profiles.resolve(living) ?: return@handler
            val hp = profile.baseStats[RamStats.HEALTH]
            val def = profile.baseStats[RamStats.DEFENSE]
            val dmg = profile.baseStats[RamStats.DAMAGE]
            Schedulers.run(living) {
                if (hp != null) {
                    living.getAttribute(Attribute.MAX_HEALTH)?.baseValue = hp
                    living.health = hp
                }
                if (dmg != null) {
                    living.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = dmg
                    PDCs.set(living, EntityProfilePdc.DAMAGE, dmg)
                }
                if (def != null) {
                    PDCs.set(living, EntityProfilePdc.DEFENSE, def)
                }
                if (profile.isBoss) {
                    val name = profile.displayName ?: profile.key.id.value().replace('_', ' ')
                    val color = NamedTextColor.LIGHT_PURPLE
                    living.customName(Component.text("✦ ", color)
                        .append(Component.text(name, color))
                        .append(Component.text(" ✦", color)))
                    living.isCustomNameVisible = true
                }
            }
        }
    }
}
