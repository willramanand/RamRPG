package dev.willram.ramrpg

import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamageTag
import dev.willram.ramrpg.api.identity.DamageTypeKey
import dev.willram.ramrpg.builtin.identity.BuiltinDamageTypes
import dev.willram.ramrpg.builtin.stats.ElementalBreakdownStage
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * WP-2.3b: [ElementalBreakdownStage] CONSUMES the attacking weapon's declared
 * [dev.willram.ramrpg.api.items.ItemDefinition.damageSplit] instead of always defaulting all-physical --
 * extends the WP-2.3a [ElementalSplitSumsToTotalTest] idiom (same `context()`-style helper) to the new
 * split-consuming and enchant-bonus-blending behavior.
 *
 * [ElementalBreakdownStage.seed] is the pure "given a split map, seed proportionally" core the weapon
 * resolution (`DamageContext.weapon` -> [dev.willram.ramrpg.api.items.ItemInstanceService.identify] ->
 * [dev.willram.ramrpg.api.items.ItemDefinitionRegistry.get] -> `.damageSplit`) wraps -- it is asserted
 * directly here, fully off-server. Resolving an actual weapon [ItemStack], by contrast, is NOT covered by
 * a stage-level test in this class: `org.bukkit.inventory.ItemStack`'s modern constructor calls
 * `Material.asItemType()` -> `Registry` -> `RegistryAccess`, which throws
 * `IllegalStateException: No RegistryAccess implementation found` off-server (confirmed by running this
 * suite -- the same reason `RpgItemRewardTest` turns its scheduler off before calling
 * `ItemInstanceService.create`). Per the WP-2.3b test plan's explicit fallback, that end-to-end resolution
 * (a real weapon stack -> its definition's declared split) is a SMOKE_TEST.md-shaped concern, not a unit
 * test here; every test below either exercises [ElementalBreakdownStage.seed] directly, or drives
 * [ElementalBreakdownStage.apply] with `weapon = null` (so [DamageContext.weapon]-resolution never runs
 * and no [ItemStack] needs constructing) to prove the TRUE-override and enchant-metadata-blend behavior
 * that does NOT depend on weapon resolution.
 */
class ElementalBreakdownWeaponSplitTest {

    // ---- pure core: ElementalBreakdownStage.seed --------------------------------------------------

    @Test
    fun `seed splits base damage proportionally across a declared split`() {
        val target = mutableMapOf<DamageTypeKey, Double>()
        val split = mapOf(BuiltinDamageTypes.PHYSICAL to 0.6, BuiltinDamageTypes.FIRE to 0.4)

        ElementalBreakdownStage.seed(target, split, 50.0)

        assertEquals(30.0, target.getValue(BuiltinDamageTypes.PHYSICAL), 0.0001)
        assertEquals(20.0, target.getValue(BuiltinDamageTypes.FIRE), 0.0001)
        assertEquals(50.0, target.values.sum(), 0.0001)
    }

    @Test
    fun `seed splits base damage across three components and still sums to the total`() {
        val target = mutableMapOf<DamageTypeKey, Double>()
        val split = mapOf(
            BuiltinDamageTypes.PHYSICAL to 0.5,
            BuiltinDamageTypes.FROST to 0.3,
            BuiltinDamageTypes.ARCANE to 0.2,
        )

        ElementalBreakdownStage.seed(target, split, 80.0)

        assertEquals(40.0, target.getValue(BuiltinDamageTypes.PHYSICAL), 0.0001)
        assertEquals(24.0, target.getValue(BuiltinDamageTypes.FROST), 0.0001)
        assertEquals(16.0, target.getValue(BuiltinDamageTypes.ARCANE), 0.0001)
        assertEquals(80.0, target.values.sum(), 0.0001)
    }

    @Test
    fun `seed with an empty split defaults to all-physical`() {
        val target = mutableMapOf<DamageTypeKey, Double>()

        ElementalBreakdownStage.seed(target, emptyMap(), 25.0)

        assertEquals(25.0, target.getValue(BuiltinDamageTypes.PHYSICAL), 0.0001)
        assertEquals(1, target.size)
    }

    @Test
    fun `seed is additive onto whatever the target already holds`() {
        val target = mutableMapOf(BuiltinDamageTypes.FIRE to 5.0)

        ElementalBreakdownStage.seed(target, mapOf(BuiltinDamageTypes.PHYSICAL to 1.0), 10.0)

        assertEquals(10.0, target.getValue(BuiltinDamageTypes.PHYSICAL), 0.0001)
        assertEquals(5.0, target.getValue(BuiltinDamageTypes.FIRE), 0.0001)
    }

    // ---- full stage, weapon-resolution-free branches ------------------------------------------------

    private fun victim(): LivingEntity = ProxyFakes.stub(LivingEntity::class.java)

    private fun context(finalDamage: Double, weapon: ItemStack? = null, tags: Set<DamageTag> = emptySet()): DamageContext {
        val ctx = DamageContext(
            attacker = null,
            victim = victim(),
            cause = EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            weapon = weapon,
            baseDamage = finalDamage,
        )
        ctx.finalDamage = finalDamage
        ctx.tags.addAll(tags)
        return ctx
    }

    @Test
    fun `apply defaults to all-physical when there is no weapon (the WP-2_3a default, unchanged)`() {
        val stage = ElementalBreakdownStage()

        val ctx = context(40.0, weapon = null)
        stage.apply(ctx)

        assertEquals(40.0, ctx.components.getValue(BuiltinDamageTypes.PHYSICAL), 0.0001)
        assertEquals(1, ctx.components.size)
    }

    @Test
    fun `TRUE tag overrides to all-true before any weapon-split resolution runs`() {
        val stage = ElementalBreakdownStage()

        val ctx = context(22.0, weapon = null, tags = setOf(DamageTag.TRUE))
        stage.apply(ctx)

        assertEquals(22.0, ctx.components.getValue(BuiltinDamageTypes.TRUE), 0.0001)
        assertEquals(1, ctx.components.size)
    }

    @Test
    fun `an enchant's metadata bonus blends in as its own component on top of the all-physical default`() {
        val stage = ElementalBreakdownStage()

        // Mirrors what BuiltinEnchants.Flaming's DamagePipelineEffect hook does at ENCHANT_OFFENSE
        // (before this stage runs): add the bonus into finalDamage, then stash it under the metadata key.
        val ctx = context(50.0, weapon = null)
        val bonus = 6.0
        ctx.finalDamage += bonus
        ctx.metadata[ElementalBreakdownStage.ELEMENTAL_BONUS_METADATA_KEY] = mutableMapOf(BuiltinDamageTypes.FIRE to bonus)

        stage.apply(ctx)

        // base = finalDamage(56) - bonusTotal(6) = 50 -> all-physical (no weapon), plus the enchant's own
        // 6 fire bonus folded in on top -- components must still sum to finalDamage.
        assertEquals(50.0, ctx.components.getValue(BuiltinDamageTypes.PHYSICAL), 0.0001)
        assertEquals(6.0, ctx.components.getValue(BuiltinDamageTypes.FIRE), 0.0001)
        assertEquals(56.0, ctx.components.values.sum(), 0.0001)
        assertEquals(ctx.finalDamage, ctx.components.values.sum(), 0.0001)
    }

    @Test
    fun `re-running the stage never accumulates a stale enchant bonus from a previous apply`() {
        val stage = ElementalBreakdownStage()
        val ctx = context(10.0, weapon = null)
        ctx.metadata[ElementalBreakdownStage.ELEMENTAL_BONUS_METADATA_KEY] = mutableMapOf(BuiltinDamageTypes.FIRE to 4.0)

        stage.apply(ctx)
        stage.apply(ctx)

        assertEquals(10.0, ctx.components.values.sum(), 0.0001)
        assertEquals(2, ctx.components.size)
    }
}
