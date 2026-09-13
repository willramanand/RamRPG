package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.combat.DamageStage
import dev.willram.ramrpg.api.stats.StatDefinition
import dev.willram.ramrpg.builtin.identity.BuiltinDamageTypes
import dev.willram.ramrpg.builtin.stats.ElementalBreakdownStage
import dev.willram.ramrpg.builtin.stats.ResistancesStage
import dev.willram.ramrpg.core.services.DamagePipelineImpl
import dev.willram.ramrpg.core.services.StatServiceImpl
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DamagePipelineTest {

    private fun stage(id: String, prio: Int, action: (DamageContext) -> Unit) = object : DamageStage {
        override val key: ContentId = ContentId.of("test", id)
        override val priority: Int = prio
        override fun apply(ctx: DamageContext) = action(ctx)
    }

    @Test
    fun `stages run in priority order`() {
        val pipeline = DamagePipelineImpl()
        val order = mutableListOf<String>()
        pipeline.register(stage("c", 300) { order += "c" })
        pipeline.register(stage("a", 100) { order += "a" })
        pipeline.register(stage("b", 200) { order += "b" })
        // No real entity needed — use null sentinel + no-op stages assertion via list only.
        // Cast trick: DamageContext requires non-null victim, so just assert ordering using stages().
        val sorted = pipeline.stages().map { it.key.value() }
        assertEquals(listOf("a", "b", "c"), sorted)
    }

    // WP-2.3a: the new elemental-breakdown / resistance slots must keep their documented positions --
    // 600, strictly between CRIT_ROLL (500) and ARMOR_MITIGATION (1100), and RESISTANCES strictly
    // between ELEMENTAL_BREAKDOWN and ARMOR_MITIGATION.

    @Test
    fun `ELEMENTAL_BREAKDOWN and RESISTANCES priorities hold their documented slot`() {
        assertEquals(600, DamagePriority.ELEMENTAL_BREAKDOWN)
        assertTrue(DamagePriority.CRIT_ROLL < DamagePriority.ELEMENTAL_BREAKDOWN)
        assertTrue(DamagePriority.ELEMENTAL_BREAKDOWN < DamagePriority.RESISTANCES)
        assertTrue(DamagePriority.RESISTANCES < DamagePriority.ARMOR_MITIGATION)
    }

    @Test
    fun `elemental breakdown and resistances slot in at 600 and before 1100 among real stages`() {
        val pipeline = DamagePipelineImpl()
        pipeline.register(stage("weapon_base", DamagePriority.WEAPON_BASE) {})
        pipeline.register(ElementalBreakdownStage())
        pipeline.register(ResistancesStage(StatServiceImpl()))
        pipeline.register(stage("armor_mitigation", DamagePriority.ARMOR_MITIGATION) {})

        val sorted = pipeline.stages().map { it.key.toString() }
        assertEquals(
            listOf("test:weapon_base", "ramrpg:elemental_breakdown", "ramrpg:resistances", "test:armor_mitigation"),
            sorted,
        )
    }

    @Test
    fun `end-to-end typed total through elemental breakdown and resistances`() {
        val stats = StatServiceImpl()
        stats.registerDefinition(
            StatDefinition(BuiltinDamageTypes.resistanceStat(BuiltinDamageTypes.PHYSICAL), Component.text("Physical Resistance"), defaultBase = 100.0),
        )

        val pipeline = DamagePipelineImpl()
        pipeline.register(stage("weapon_base", DamagePriority.WEAPON_BASE) { it.finalDamage = 80.0 })
        pipeline.register(ElementalBreakdownStage())
        pipeline.register(ResistancesStage(stats))

        val victim: Player = ProxyFakes.proxy(Player::class.java, mapOf("getUniqueId" to UUID.randomUUID(), "getName" to "Steve"))
        val ctx = DamageContext(
            attacker = null,
            victim = victim,
            cause = EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            baseDamage = 80.0,
        )

        pipeline.process(ctx)

        // WEAPON_BASE sets 80 physical; 100 resistance -> factor 1 - 100/200 = 0.5 -> 40.0
        assertEquals(40.0, ctx.finalDamage, 0.0001)
        assertEquals(40.0, ctx.components.getValue(BuiltinDamageTypes.PHYSICAL), 0.0001)
    }
}
