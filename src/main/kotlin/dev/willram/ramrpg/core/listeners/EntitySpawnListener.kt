/** Applies EntityProfile baseStats to spawned LivingEntities (HP/DAMAGE attribute, DEF in PDC). */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.pdc.PDCs
import dev.willram.ramcore.pdc.PdcKey
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.builtin.identity.RamStats
import dev.willram.ramrpg.core.regions.LevelBandService
import dev.willram.ramrpg.core.services.EntityProfileRegistryImpl
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
    fun register(consumer: TerminableConsumer) {
        consumer.bind(Events.subscribe(EntitySpawnEvent::class.java).handler { e ->
            val living = e.entity as? LivingEntity ?: return@handler
            val profile = profiles.resolve(living) ?: return@handler
            // WP-6.2: `profiles.resolve` (above) already resolved-and-cached this entity's level band
            // (EntityProfileRegistryImpl.resolve); reading it back here is a cache hit (RamCore Metadata
            // lookup only) -- NOT a second RegionRuleEngine.regionsAt call -- so applying statMultiplier
            // per spawn stays a one-time cost, never per-tick (rule 4).
            val band = (profiles as? EntityProfileRegistryImpl)?.levelBandService?.resolveAndCache(living)
            val hp = profile.baseStats[RamStats.HEALTH]?.let { LevelBandService.scale(it, band) }
            val def = profile.baseStats[RamStats.DEFENSE]?.let { LevelBandService.scale(it, band) }
            val dmg = profile.baseStats[RamStats.DAMAGE]?.let { LevelBandService.scale(it, band) }
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
        })
    }
}
