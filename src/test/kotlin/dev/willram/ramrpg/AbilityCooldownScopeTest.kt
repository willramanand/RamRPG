package dev.willram.ramrpg

import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.abilities.Ability
import dev.willram.ramrpg.api.abilities.AbilityContext
import dev.willram.ramrpg.api.abilities.AbilityResult
import dev.willram.ramrpg.api.abilities.AbilityTrigger
import dev.willram.ramrpg.api.abilities.Cooldown
import dev.willram.ramrpg.api.abilities.CooldownScope
import dev.willram.ramrpg.api.identity.AbilityKey
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.builtin.identity.RamSkills
import dev.willram.ramrpg.core.services.AbilityRegistryImpl
import dev.willram.ramrpg.core.services.AbilityServiceImpl
import dev.willram.ramrpg.core.storage.InMemoryPlayerStore
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * WP-1.3: ability cooldowns are backed by RamCore's CooldownTracker, keyed by scope. Uses a long (5s)
 * cooldown so it stays active for the microseconds the test runs (RamCore's Cooldown reads wall-clock
 * time and has no injectable clock); the assertions are about scope separation, not expiry timing.
 */
class AbilityCooldownScopeTest {

    private val KEY = AbilityKey.of("test", "cd")

    private fun ability(scope: CooldownScope) = object : Ability {
        override val key = KEY
        override val triggers = listOf<AbilityTrigger>(AbilityTrigger.RightClick)
        override val cooldown = Cooldown(ticks = 100L, keyScope = scope) // 100 ticks = 5s
        override fun execute(ctx: AbilityContext) = AbilityResult.Success
    }

    private fun service(scope: CooldownScope): AbilityServiceImpl {
        val registry = AbilityRegistryImpl()
        registry.register("test", ability(scope))
        return AbilityServiceImpl(registry, InMemoryPlayerStore(), ProxyFakes.stub(SkillService::class.java), RamSkills.SORCERY)
    }

    private fun player(id: UUID): Player = ProxyFakes.proxy(Player::class.java, mapOf("getUniqueId" to id))

    private fun ctx(p: Player, itemInstance: UUID? = null) = object : AbilityContext {
        override val player = p
        override val item = null
        override val target = null
        override val damageVictim = null
        override val itemInstanceId = itemInstance
    }

    private fun fire(svc: AbilityServiceImpl, p: Player, itemInstance: UUID? = null): AbilityResult =
        svc.tryFire(AbilityTrigger.RightClick, ctx(p, itemInstance)).single()

    @Test
    fun `player scope separates by player`() {
        val svc = service(CooldownScope.PLAYER)
        val a = player(UUID.randomUUID())
        val b = player(UUID.randomUUID())

        assertTrue(fire(svc, a) is AbilityResult.Success)
        assertTrue(fire(svc, a) is AbilityResult.Fail, "same player is on cooldown")
        assertTrue(fire(svc, b) is AbilityResult.Success, "a different player has their own cooldown")

        assertTrue(svc.remaining(a, KEY).toMillis() > 0)
        assertEquals(0L, svc.remaining(player(UUID.randomUUID()), KEY).toMillis(), "a player who never fired is ready")
    }

    @Test
    fun `item scope separates by instance and falls back to player when null`() {
        val svc = service(CooldownScope.ITEM)
        val a = player(UUID.randomUUID())
        val item1 = UUID.randomUUID()
        val item2 = UUID.randomUUID()

        assertTrue(fire(svc, a, item1) is AbilityResult.Success)
        assertTrue(fire(svc, a, item1) is AbilityResult.Fail, "same item instance is on cooldown")
        assertTrue(fire(svc, a, item2) is AbilityResult.Success, "a different item instance is separate")
        assertTrue(fire(svc, a, null) is AbilityResult.Success, "null instance falls back to a distinct player key")
    }

    @Test
    fun `global scope is shared across players`() {
        val svc = service(CooldownScope.GLOBAL)
        val a = player(UUID.randomUUID())
        val b = player(UUID.randomUUID())

        assertTrue(fire(svc, a) is AbilityResult.Success)
        assertTrue(fire(svc, b) is AbilityResult.Fail, "global cooldown is shared across all players")
    }
}
