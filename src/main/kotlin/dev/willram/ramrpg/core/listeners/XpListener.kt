/** Awards combat xp on EntityDeathEvent via EntityProfile lookup. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.XpContext
import dev.willram.ramrpg.api.skills.XpSource
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDeathEvent

class XpListener(
    private val profiles: EntityProfileRegistry,
    private val skills: SkillService,
    private val economy: EconomyService? = null,
) {
    fun register() {
        Events.subscribe(EntityDeathEvent::class.java).handler { e ->
            val killer: Player = e.entity.killer ?: return@handler
            val victim: LivingEntity = e.entity
            val profile = profiles.resolve(victim) ?: return@handler
            val skillKey = profile.skill ?: return@handler
            val srcKey = profile.xpSourceKey ?: return@handler
            val amount = profile.xpAmount
            if (amount <= 0) return@handler
            val src = object : XpSource {
                override val key: XpSourceKey = srcKey
                override val skill: SkillKey = skillKey
                override fun xp(ctx: XpContext): Double = amount
            }
            val bossBonus = if (profile.isBoss) 5.0 else 1.0
            skills.addXp(killer, src, victim, bossBonus)
            economy?.deposit(killer, amount * 0.25 * bossBonus)
            if (profile.isBoss) {
                val name = profile.displayName ?: profile.key.id.value().replace('_', ' ')
                org.bukkit.Bukkit.broadcast(
                    net.kyori.adventure.text.Component.text("")
                        .append(net.kyori.adventure.text.Component.text(killer.name, net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                        .append(net.kyori.adventure.text.Component.text(" slew ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                        .append(net.kyori.adventure.text.Component.text(name, net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE))
                )
            }
        }
    }
}
