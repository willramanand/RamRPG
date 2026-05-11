/** Routes Bukkit interact / break / hit / kill events to AbilityService. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.api.abilities.AbilityService
import dev.willram.ramrpg.api.abilities.AbilityTrigger
import dev.willram.ramrpg.api.effects.BlockMatchers
import dev.willram.ramrpg.builtin.abilities.BlockAwareAbilityContext
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.entity.Player

class AbilityListener(private val abilities: AbilityService) {
    fun register() {
        Events.subscribe(PlayerInteractEvent::class.java, EventPriority.HIGH).handler { e ->
            val p = e.player
            val item = e.item
            val trigger: AbilityTrigger = when (e.action) {
                Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK ->
                    if (p.isSneaking) AbilityTrigger.SneakRightClick else AbilityTrigger.RightClick
                Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> AbilityTrigger.LeftClick
                else -> return@handler
            }
            abilities.tryFire(trigger, BlockAwareAbilityContext(player = p, item = item))
        }
        Events.subscribe(BlockBreakEvent::class.java, EventPriority.HIGH).handler { e ->
            val ctx = BlockAwareAbilityContext(
                player = e.player,
                item = e.player.inventory.itemInMainHand,
                block = e.block,
            )
            abilities.tryFire(AbilityTrigger.BlockBreak(BlockMatchers.ANY), ctx)
        }
        Events.subscribe(EntityDamageByEntityEvent::class.java, EventPriority.MONITOR).handler { e ->
            val attacker = e.damager as? Player ?: return@handler
            val victim = e.entity as? org.bukkit.entity.LivingEntity ?: return@handler
            abilities.tryFire(AbilityTrigger.EntityHit, BlockAwareAbilityContext(
                player = attacker,
                item = attacker.inventory.itemInMainHand,
                target = victim,
                damageVictim = victim,
            ))
        }
        Events.subscribe(EntityDeathEvent::class.java).handler { e ->
            val killer = e.entity.killer ?: return@handler
            abilities.tryFire(AbilityTrigger.Killed, BlockAwareAbilityContext(
                player = killer,
                item = killer.inventory.itemInMainHand,
                target = e.entity,
                damageVictim = e.entity,
            ))
        }
    }
}
