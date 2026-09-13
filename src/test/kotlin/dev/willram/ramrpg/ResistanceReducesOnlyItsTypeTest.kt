package dev.willram.ramrpg

import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.stats.StatDefinition
import dev.willram.ramrpg.builtin.identity.BuiltinDamageTypes
import dev.willram.ramrpg.builtin.stats.ResistancesStage
import dev.willram.ramrpg.core.services.StatServiceImpl
import net.kyori.adventure.text.Component
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * WP-2.3a: RESISTANCES (1000, just before ARMOR_MITIGATION) reduces only the component matching its
 * own damage type. A fire resistance must never leak into the frost or physical share of the same hit.
 */
class ResistanceReducesOnlyItsTypeTest {

    private fun player(): Player =
        ProxyFakes.proxy(Player::class.java, mapOf("getUniqueId" to UUID.randomUUID(), "getName" to "Steve"))

    private fun context(victim: LivingEntity, components: Map<dev.willram.ramrpg.api.identity.DamageTypeKey, Double>): DamageContext {
        val total = components.values.sum()
        val ctx = DamageContext(
            attacker = null,
            victim = victim,
            cause = EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            baseDamage = total,
        )
        ctx.finalDamage = total
        ctx.components.putAll(components)
        return ctx
    }

    @Test
    fun `fire resistance reduces only the fire component`() {
        val stats = StatServiceImpl()
        stats.registerDefinition(StatDefinition(BuiltinDamageTypes.resistanceStat(BuiltinDamageTypes.FIRE), Component.text("Fire Resistance"), defaultBase = 50.0))

        val victim = player()
        val ctx = context(
            victim,
            mapOf(
                BuiltinDamageTypes.FIRE to 100.0,
                BuiltinDamageTypes.FROST to 50.0,
                BuiltinDamageTypes.PHYSICAL to 25.0,
            ),
        )

        ResistancesStage(stats).apply(ctx)

        // 50 resistance -> factor = 1 - 50/(50+100) = 2/3
        assertEquals(100.0 * (2.0 / 3.0), ctx.components.getValue(BuiltinDamageTypes.FIRE), 0.0001)
        assertEquals(50.0, ctx.components.getValue(BuiltinDamageTypes.FROST), 0.0001)
        assertEquals(25.0, ctx.components.getValue(BuiltinDamageTypes.PHYSICAL), 0.0001)
        assertEquals(ctx.components.values.sum(), ctx.finalDamage, 0.0001)
    }

    @Test
    fun `zero resistance leaves the component untouched`() {
        val stats = StatServiceImpl()
        val victim = player()
        val ctx = context(victim, mapOf(BuiltinDamageTypes.PHYSICAL to 40.0))

        ResistancesStage(stats).apply(ctx)

        assertEquals(40.0, ctx.components.getValue(BuiltinDamageTypes.PHYSICAL), 0.0001)
        assertEquals(40.0, ctx.finalDamage, 0.0001)
    }

    @Test
    fun `non-player victims have no resistance data source and pass components through unreduced`() {
        val stats = StatServiceImpl()
        stats.registerDefinition(StatDefinition(BuiltinDamageTypes.resistanceStat(BuiltinDamageTypes.FIRE), Component.text("Fire Resistance"), defaultBase = 90.0))

        val mobVictim = ProxyFakes.stub(LivingEntity::class.java)
        val ctx = context(mobVictim, mapOf(BuiltinDamageTypes.FIRE to 100.0))

        ResistancesStage(stats).apply(ctx)

        assertEquals(100.0, ctx.components.getValue(BuiltinDamageTypes.FIRE), 0.0001)
        assertEquals(100.0, ctx.finalDamage, 0.0001)
    }
}
