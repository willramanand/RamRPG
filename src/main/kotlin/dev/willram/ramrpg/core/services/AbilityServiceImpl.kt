/**
 * AbilityService implementation. Tracks per-player cooldowns by scope
 * and consumes mana before dispatching `Ability.execute`.
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramcore.cooldown.Cooldown
import dev.willram.ramcore.cooldown.CooldownKey
import dev.willram.ramcore.cooldown.CooldownTracker
import dev.willram.ramcore.cooldown.Cooldowns
import dev.willram.ramrpg.api.abilities.Ability
import dev.willram.ramrpg.api.abilities.AbilityContext
import dev.willram.ramrpg.api.abilities.AbilityRegistry
import dev.willram.ramrpg.api.abilities.AbilityResult
import dev.willram.ramrpg.api.abilities.AbilityService
import dev.willram.ramrpg.api.abilities.AbilityTrigger
import dev.willram.ramrpg.api.abilities.CooldownScope
import dev.willram.ramrpg.api.abilities.ResourceCost
import dev.willram.ramrpg.api.abilities.formatCooldown
import dev.willram.ramrpg.api.identity.AbilityKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.XpContext
import dev.willram.ramrpg.api.skills.XpSource
import dev.willram.ramrpg.core.storage.PlayerStore
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class AbilityRegistryImpl(
    private val backing: ContentRegistry<Ability> = ContentRegistry.create(Ability::class.java)
) : AbilityRegistry {
    override fun register(owner: String, ability: Ability) {
        backing.register(owner, ContentKey.of(ability.key.id, Ability::class.java), ability)
    }
    override fun unregisterOwner(owner: String): Int = backing.unregisterOwner(owner)
    override fun get(key: AbilityKey): Ability? = backing.get(key.id).orElse(null)
    override fun forTrigger(t: AbilityTrigger): List<Ability> = backing.entries().map { it.value() }
        .filter { ab -> ab.triggers.any { matches(it, t) } }
    override fun all(): Collection<Ability> = backing.entries().map { it.value() }

    private fun matches(declared: AbilityTrigger, fired: AbilityTrigger): Boolean = declared::class == fired::class
}

class AbilityServiceImpl(
    private val registry: AbilityRegistry,
    private val playerStore: PlayerStore,
    private val skillService: SkillService,
    /** Skill that gains XP equal to mana spent on successful ability fires. */
    private val manaXpSkill: SkillKey,
) : AbilityService {

    // One RamCore CooldownTracker per ability (its base timeout is the ability's cooldown). The
    // CooldownKey's second element is the scope discriminator: player uuid, item instance id, or a
    // GLOBAL constant. Backs cooldowns with RamCore instead of a hand-rolled timestamp map.
    private val trackers = ConcurrentHashMap<AbilityKey, CooldownTracker<CooldownKey>>()

    private fun trackerFor(ab: Ability): CooldownTracker<CooldownKey> =
        trackers.computeIfAbsent(ab.key) { Cooldowns.grouped(Cooldown.ofTicks(ab.cooldown.ticks)) }

    private fun scopeKey(scope: CooldownScope, abilityId: String, player: Player, itemInstanceId: java.util.UUID?): CooldownKey =
        when (scope) {
            CooldownScope.PLAYER -> CooldownKey.of(abilityId, player.uniqueId)
            CooldownScope.GLOBAL -> CooldownKey.of(abilityId, "global")
            // A null instance id (assignInstanceId = false) falls back to the player key, never a shared null.
            CooldownScope.ITEM -> CooldownKey.of(abilityId, itemInstanceId ?: player.uniqueId)
        }

    override fun remaining(player: Player, ability: AbilityKey): Duration {
        val tracker = trackers[ability] ?: return Duration.ZERO
        val ab = registry.get(ability) ?: return Duration.ZERO
        // No item in hand is known at this entry point; ITEM scope uses the player fallback key.
        val key = scopeKey(ab.cooldown.keyScope, ability.id.toString(), player, null)
        return Duration.ofMillis(tracker.remainingMillis(key))
    }

    override fun isDisabled(player: Player, ability: AbilityKey): Boolean =
        playerStore.require(player.uniqueId).disabledAbilities.contains(ability.id.toString())

    override fun setDisabled(player: Player, ability: AbilityKey, disabled: Boolean) {
        val data = playerStore.require(player.uniqueId)
        val id = ability.id.toString()
        val changed = if (disabled) data.disabledAbilities.add(id) else data.disabledAbilities.remove(id)
        if (changed) data.markDirty()
    }

    override fun tryFire(trigger: AbilityTrigger, ctx: AbilityContext): List<AbilityResult> {
        val results = ArrayList<AbilityResult>()
        for (ab in registry.forTrigger(trigger)) {
            if (isDisabled(ctx.player, ab.key)) {
                results += AbilityResult.Fail(Component.text("Ability disabled"))
                continue
            }
            val unlock = ab.unlockSkill
            if (unlock != null && skillService.level(ctx.player, unlock) < ab.unlockLevel) {
                results += AbilityResult.Fail(Component.text("Requires ${unlock.id.value()} ${ab.unlockLevel}"))
                continue
            }
            val tracker = trackerFor(ab)
            val cdKey = scopeKey(ab.cooldown.keyScope, ab.key.id.toString(), ctx.player, ctx.itemInstanceId)
            if (tracker.peek(cdKey).denied()) {
                val left = formatCooldown(Duration.ofMillis(tracker.remainingMillis(cdKey)))
                results += AbilityResult.Fail(Component.translatable("ramrpg.ability.on_cooldown", Component.text(left)))
                continue
            }
            if (ab.requirements.any { !it.met(ctx) }) {
                results += AbilityResult.Fail(Component.text("Requirements not met"))
                continue
            }
            val data = playerStore.require(ctx.player.uniqueId)
            val manaCost = ab.costs.filterIsInstance<ResourceCost.Mana>().sumOf { it.amount }
            if (manaCost > 0 && data.currentMana < manaCost) {
                results += AbilityResult.Fail(Component.text("Not enough mana"))
                continue
            }
            data.currentMana -= manaCost
            val res = ab.execute(ctx)
            if (res is AbilityResult.Success) {
                tracker.test(cdKey) // start the cooldown from now
                if (manaCost > 0) awardManaXp(ctx.player, ab.key, manaCost)
            } else if (res is AbilityResult.Fail) {
                // refund mana on execute failure (e.g., wrong tool)
                data.currentMana += manaCost
            }
            results += res
        }
        return results
    }

    private fun awardManaXp(player: Player, ability: AbilityKey, mana: Double) {
        val src = object : XpSource {
            override val key = XpSourceKey.of("ramrpg", "ability_mana_${ability.id.value()}")
            override val skill = manaXpSkill
            override fun xp(ctx: XpContext): Double = mana
        }
        skillService.addXp(player, src)
    }
}
