package dev.willram.ramrpg

import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.stats.StatDefinition
import dev.willram.ramrpg.builtin.identity.BuiltinDamageTypes
import dev.willram.ramrpg.builtin.stats.ResistancesStage
import dev.willram.ramrpg.core.services.StatServiceImpl
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * WP-2.3a: `true`-typed damage ignores resistances entirely, even when a `resistance_true` stat is
 * present with a huge value. ResistancesStage must skip the true component outright, not merely apply
 * a formula that happens to be weak against it.
 */
class TrueDamageIgnoresResistancesTest {

    private fun player(): Player =
        ProxyFakes.proxy(Player::class.java, mapOf("getUniqueId" to UUID.randomUUID(), "getName" to "Steve"))

    @Test
    fun `a huge true resistance stat has no effect on true damage`() {
        val stats = StatServiceImpl()
        stats.registerDefinition(StatDefinition(BuiltinDamageTypes.resistanceStat(BuiltinDamageTypes.TRUE), Component.text("True Resistance"), defaultBase = 9999.0))

        val ctx = DamageContext(
            attacker = null,
            victim = player(),
            cause = EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            baseDamage = 100.0,
        )
        ctx.finalDamage = 100.0
        ctx.components[BuiltinDamageTypes.TRUE] = 100.0

        ResistancesStage(stats).apply(ctx)

        assertEquals(100.0, ctx.components.getValue(BuiltinDamageTypes.TRUE), 0.0001)
        assertEquals(100.0, ctx.finalDamage, 0.0001)
    }

    @Test
    fun `a mixed hit only exempts the true share, other components still resist normally`() {
        val stats = StatServiceImpl()
        stats.registerDefinition(StatDefinition(BuiltinDamageTypes.resistanceStat(BuiltinDamageTypes.TRUE), Component.text("True Resistance"), defaultBase = 9999.0))
        stats.registerDefinition(StatDefinition(BuiltinDamageTypes.resistanceStat(BuiltinDamageTypes.PHYSICAL), Component.text("Physical Resistance"), defaultBase = 100.0))

        val ctx = DamageContext(
            attacker = null,
            victim = player(),
            cause = EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            baseDamage = 150.0,
        )
        ctx.finalDamage = 150.0
        ctx.components[BuiltinDamageTypes.TRUE] = 50.0
        ctx.components[BuiltinDamageTypes.PHYSICAL] = 100.0

        ResistancesStage(stats).apply(ctx)

        // true share untouched
        assertEquals(50.0, ctx.components.getValue(BuiltinDamageTypes.TRUE), 0.0001)
        // physical: 100 resistance -> factor = 1 - 100/200 = 0.5
        assertEquals(50.0, ctx.components.getValue(BuiltinDamageTypes.PHYSICAL), 0.0001)
        assertEquals(100.0, ctx.finalDamage, 0.0001)
    }
}
