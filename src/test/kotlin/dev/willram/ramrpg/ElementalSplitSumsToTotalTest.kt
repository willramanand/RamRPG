package dev.willram.ramrpg

import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamageTag
import dev.willram.ramrpg.builtin.identity.BuiltinDamageTypes
import dev.willram.ramrpg.builtin.stats.ElementalBreakdownStage
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * WP-2.3a: ELEMENTAL_BREAKDOWN (600) is the one and only stage that seeds
 * [DamageContext.components]. Whatever it seeds must sum back to the incoming total -- it partitions
 * damage, it never invents or drops any.
 */
class ElementalSplitSumsToTotalTest {

    private fun victim(): LivingEntity = ProxyFakes.stub(LivingEntity::class.java)

    private fun context(finalDamage: Double, tags: Set<DamageTag> = emptySet()): DamageContext {
        val ctx = DamageContext(
            attacker = null,
            victim = victim(),
            cause = EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            baseDamage = finalDamage,
        )
        ctx.finalDamage = finalDamage
        ctx.tags.addAll(tags)
        return ctx
    }

    @Test
    fun `default split is all-physical and sums to the total`() {
        val ctx = context(40.0)
        ElementalBreakdownStage().apply(ctx)

        assertEquals(40.0, ctx.components.values.sum(), 0.0001)
        assertEquals(40.0, ctx.components[BuiltinDamageTypes.PHYSICAL])
        assertEquals(1, ctx.components.size)
    }

    @Test
    fun `damage already tagged TRUE becomes an all-true component that still sums to the total`() {
        val ctx = context(17.5, tags = setOf(DamageTag.TRUE))
        ElementalBreakdownStage().apply(ctx)

        assertEquals(17.5, ctx.components.values.sum(), 0.0001)
        assertEquals(17.5, ctx.components[BuiltinDamageTypes.TRUE])
        assertEquals(1, ctx.components.size)
    }

    @Test
    fun `re-running the stage on the same context never accumulates components`() {
        val ctx = context(10.0)
        val stage = ElementalBreakdownStage()
        stage.apply(ctx)
        stage.apply(ctx)

        assertEquals(10.0, ctx.components.values.sum(), 0.0001)
        assertEquals(1, ctx.components.size)
    }
}
