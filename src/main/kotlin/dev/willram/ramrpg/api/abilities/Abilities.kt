/**
 * Ability framework. Abilities declare [AbilityTrigger]s, [ResourceCost]s,
 * and [Cooldown]s. [AbilityService.tryFire] enforces cost/cooldown and
 * dispatches [Ability.execute] returning [AbilityResult].
 */
package dev.willram.ramrpg.api.abilities

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.effects.BlockMatcher
import dev.willram.ramrpg.api.identity.AbilityKey
import dev.willram.ramrpg.api.identity.SkillKey
import net.kyori.adventure.text.Component
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Duration
import java.util.Locale
import java.util.UUID

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
    /** Instance id of [item] when it is an RPG item, for [CooldownScope.ITEM] keying. Null falls back to PLAYER. */
    val itemInstanceId: UUID? get() = null
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
    /** Skill that gates this ability. Null = always unlocked. */
    val unlockSkill: SkillKey? get() = null
    /** Minimum level in [unlockSkill] required to use. Ignored when [unlockSkill] is null. */
    val unlockLevel: Int get() = 0
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
    fun isDisabled(player: Player, ability: AbilityKey): Boolean
    fun setDisabled(player: Player, ability: AbilityKey, disabled: Boolean)
    /**
     * Time left on [ability]'s cooldown for [player], or [Duration.ZERO] if ready. For
     * [CooldownScope.ITEM] this is the player-scoped fallback (no item in hand is known here).
     */
    fun remaining(player: Player, ability: AbilityKey): Duration
}

/** Renders a cooldown [Duration] for lore/HUD: "0s", "3.2s", "45s", "1m 5s". */
fun formatCooldown(remaining: Duration): String {
    val ms = remaining.toMillis()
    return when {
        ms <= 0L -> "0s"
        ms >= 60_000L -> "${ms / 60_000L}m ${(ms % 60_000L) / 1000L}s"
        ms >= 10_000L -> "${Math.round(ms / 1000.0)}s"
        else -> String.format(Locale.ROOT, "%.1fs", ms / 1000.0)
    }
}
