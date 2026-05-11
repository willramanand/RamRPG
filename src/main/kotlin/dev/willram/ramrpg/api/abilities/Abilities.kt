/**
 * Ability framework. Abilities declare [AbilityTrigger]s, [ResourceCost]s,
 * and [Cooldown]s. [AbilityService.tryFire] enforces cost/cooldown and
 * dispatches [Ability.execute] returning [AbilityResult].
 */
package dev.willram.ramrpg.api.abilities

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.effects.BlockMatcher
import dev.willram.ramrpg.api.identity.AbilityKey
import net.kyori.adventure.text.Component
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

sealed interface AbilityTrigger {
    data object RightClick : AbilityTrigger
    data object LeftClick : AbilityTrigger
    data object SneakRightClick : AbilityTrigger
    data object EntityHit : AbilityTrigger
    data object Killed : AbilityTrigger
    data object Damaged : AbilityTrigger
    data class BlockBreak(val matcher: BlockMatcher) : AbilityTrigger
    data class PassiveTick(val periodTicks: Int) : AbilityTrigger
    data class BossSignal(val signal: ContentId) : AbilityTrigger
}

sealed interface ResourceCost {
    data class Mana(val amount: Double) : ResourceCost
    data class Health(val amount: Double) : ResourceCost
}

enum class CooldownScope { PLAYER, ITEM, GLOBAL }

data class Cooldown(val ticks: Long, val keyScope: CooldownScope = CooldownScope.PLAYER)

interface Requirement { fun met(ctx: AbilityContext): Boolean }

interface AbilityContext {
    val player: Player
    val item: ItemStack?
    val target: Entity?
    val damageVictim: LivingEntity?
}

sealed interface AbilityResult {
    data object Success : AbilityResult
    data class Fail(val reason: Component) : AbilityResult
}

interface Ability {
    val key: AbilityKey
    val triggers: List<AbilityTrigger>
    val requirements: List<Requirement> get() = emptyList()
    val costs: List<ResourceCost> get() = emptyList()
    val cooldown: Cooldown
    fun execute(ctx: AbilityContext): AbilityResult
}

interface AbilityRegistry {
    fun register(owner: String, ability: Ability)
    fun unregisterOwner(owner: String): Int
    fun get(key: AbilityKey): Ability?
    fun forTrigger(t: AbilityTrigger): List<Ability>
    fun all(): Collection<Ability>
}

interface AbilityService {
    fun tryFire(trigger: AbilityTrigger, ctx: AbilityContext): List<AbilityResult>
}
