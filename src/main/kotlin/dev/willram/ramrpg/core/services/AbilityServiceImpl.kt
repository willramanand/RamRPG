/**
 * AbilityService implementation. Tracks per-player cooldowns by scope
 * and consumes mana before dispatching `Ability.execute`.
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramrpg.api.abilities.Ability
import dev.willram.ramrpg.api.abilities.AbilityContext
import dev.willram.ramrpg.api.abilities.AbilityRegistry
import dev.willram.ramrpg.api.abilities.AbilityResult
import dev.willram.ramrpg.api.abilities.AbilityService
import dev.willram.ramrpg.api.abilities.AbilityTrigger
import dev.willram.ramrpg.api.abilities.CooldownScope
import dev.willram.ramrpg.api.abilities.ResourceCost
import dev.willram.ramrpg.api.identity.AbilityKey
import dev.willram.ramrpg.core.storage.PlayerStore
import net.kyori.adventure.text.Component
import java.util.UUID
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
) : AbilityService {

    private data class CdKey(val player: UUID, val ability: AbilityKey, val scope: CooldownScope, val item: UUID?)
    private val cooldowns = ConcurrentHashMap<CdKey, Long>()

    override fun tryFire(trigger: AbilityTrigger, ctx: AbilityContext): List<AbilityResult> {
        val now = System.currentTimeMillis()
        val results = ArrayList<AbilityResult>()
        for (ab in registry.forTrigger(trigger)) {
            val cdKey = CdKey(ctx.player.uniqueId, ab.key, ab.cooldown.keyScope, null)
            val readyAt = cooldowns[cdKey] ?: 0L
            if (readyAt > now) {
                results += AbilityResult.Fail(Component.text("On cooldown"))
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
                cooldowns[cdKey] = now + ab.cooldown.ticks * 50
            }
            results += res
        }
        return results
    }
}
