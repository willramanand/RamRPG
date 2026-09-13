package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.effects.ScalingFormula
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.enchants.RPGEnchantment
import dev.willram.ramrpg.api.identity.EffectKey
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemRequirement
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.SocketData
import dev.willram.ramrpg.api.items.isMet
import dev.willram.ramrpg.api.reforges.ReforgeDefinition
import dev.willram.ramrpg.api.skills.SkillDefinition
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.XpSource
import dev.willram.ramrpg.api.sockets.GemDefinition
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatContext
import dev.willram.ramrpg.api.stats.StatDefinition
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.api.stats.StatProvider
import dev.willram.ramrpg.core.listeners.ItemRequirementServices
import dev.willram.ramrpg.core.listeners.requirementStateFor
import dev.willram.ramrpg.core.services.EnchantmentRegistryImpl
import dev.willram.ramrpg.core.services.GemRegistryImpl
import dev.willram.ramrpg.core.services.ReforgeRegistryImpl
import dev.willram.ramrpg.core.services.StatServiceImpl
import dev.willram.ramrpg.core.services.enchantmentStatsFor
import dev.willram.ramrpg.core.services.equipmentStatsFor
import dev.willram.ramrpg.core.services.reforgeStatsFor
import dev.willram.ramrpg.core.services.socketStatsFor
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * WP-2.1c: an inert item ([dev.willram.ramrpg.api.items.isInert] `true`) must contribute ZERO stats from
 * EVERY item-based provider -- equipment base stats, enchantments, reforge, sockets. Each provider's
 * per-stack contribution was extracted into a pure `xStatsFor(def, data, state, ...)` function
 * ([equipmentStatsFor], [enchantmentStatsFor], [reforgeStatsFor], [socketStatsFor]) that all funnel
 * through the SAME [dev.willram.ramrpg.api.items.isInert] check -- a provider that forgot the check
 * would return a non-empty list here instead of `emptyList()`, failing the corresponding test below.
 * (`SetStatProvider` does not exist yet -- WP-5.3 -- and will need the same treatment when it lands.)
 *
 * Also proves the "no self-satisfaction" rule from `docs/design/2.1c-inert-items.md`: a live
 * [requirementStateFor] never lets an item's (or any item's) stat contribution count toward a
 * `StatThreshold` requirement, even when [dev.willram.ramrpg.api.stats.StatService] reports a much
 * higher *current* value for that stat from item-based providers.
 */
class InertItemContributesNoStatsTest {

    private val combat = SkillKey.of("ramrpg", "combat")
    private val strength = StatKey.of("ramrpg", "strength")

    private class FakeState(
        private val skillLevels: Map<SkillKey, Int> = emptyMap(),
        private val statValues: Map<StatKey, Double> = emptyMap(),
    ) : ItemRequirementState {
        override fun skillLevel(skill: SkillKey): Int = skillLevels[skill] ?: 0
        override fun statValue(stat: StatKey): Double = statValues[stat] ?: 0.0
    }

    private val met = FakeState(skillLevels = mapOf(combat to 10))
    private val unmet = FakeState()

    private fun swordDef(requirements: List<ItemRequirement> = listOf(ItemRequirement.SkillLevel(combat, 10))): ItemDefinition =
        ItemDefinition(
            key = ItemKey.of("ramrpg", "test_sword"),
            displayName = Component.text("Test Sword"),
            material = Material.IRON_SWORD,
            rarity = Rarity.UNCOMMON,
            categories = setOf(ItemCategory.SWORD),
            baseStats = listOf(StatModifier(strength, 50.0, ModifierOperation.ADD, ModifierSource(SourceType.ITEM, ContentId.of("ramrpg", "test_sword")))),
            requirements = requirements,
        )

    private fun instance(def: ItemDefinition, durability: Int = 500, reforge: ReforgeKey? = null, sockets: List<SocketData> = emptyList(), enchantments: Map<EnchantmentKey, Int> = emptyMap()): ItemInstanceData =
        ItemInstanceData(
            identity = ItemIdentity(key = def.key),
            reforge = reforge,
            sockets = sockets,
            enchantments = enchantments,
            durability = durability,
        )

    // -- EquipmentStatProvider -----------------------------------------------------------------

    @Test
    fun `equipment provider withholds base stats when a requirement is unmet`() {
        val def = swordDef()
        assertTrue(equipmentStatsFor(def, instance(def), unmet).isEmpty())
    }

    @Test
    fun `equipment provider withholds base stats at zero durability even if requirements are met`() {
        val def = swordDef()
        assertTrue(equipmentStatsFor(def, instance(def, durability = 0), met).isEmpty())
    }

    @Test
    fun `equipment provider contributes base stats once requirements are met and durability remains`() {
        val def = swordDef()
        val mods = equipmentStatsFor(def, instance(def), met)
        assertTrue(mods.isNotEmpty())
        assertEquals(50.0, mods.first { it.stat == strength }.amount)
    }

    // -- EnchantmentStatProvider ----------------------------------------------------------------

    private fun enchantWithStatEffect(): dev.willram.ramrpg.api.enchants.EnchantmentRegistry {
        val registry = EnchantmentRegistryImpl()
        registry.register("test", object : RPGEnchantment {
            override val key = EnchantmentKey.of("ramrpg", "sharp")
            override val displayName = Component.text("Sharp")
            override val maxLevel = 5
            override val targets = setOf(ItemCategory.SWORD)
            override fun effects(level: Int) = listOf(
                StatEffect(
                    key = EffectKey.of("ramrpg", "sharp_effect"),
                    stat = strength,
                    amount = ScalingFormula { level * 5.0 },
                    operation = ModifierOperation.ADD,
                )
            )
        })
        return registry
    }

    @Test
    fun `enchantment provider withholds stat effects on an inert item`() {
        val def = swordDef()
        val enchants = enchantWithStatEffect()
        val data = instance(def, enchantments = mapOf(EnchantmentKey.of("ramrpg", "sharp") to 3))
        assertTrue(enchantmentStatsFor(def, data, unmet, enchants).isEmpty())
    }

    @Test
    fun `enchantment provider contributes stat effects on an active item`() {
        val def = swordDef()
        val enchants = enchantWithStatEffect()
        val data = instance(def, enchantments = mapOf(EnchantmentKey.of("ramrpg", "sharp") to 3))
        val mods = enchantmentStatsFor(def, data, met, enchants)
        assertTrue(mods.isNotEmpty())
    }

    // -- ReforgeStatProvider --------------------------------------------------------------------

    private fun reforgeRegistryWith(key: ReforgeKey): dev.willram.ramrpg.api.reforges.ReforgeRegistry {
        val registry = ReforgeRegistryImpl()
        registry.register("test", ReforgeDefinition(key = key, displayName = Component.text("Sharp"), universal = mapOf(strength to 15.0)))
        return registry
    }

    @Test
    fun `reforge provider withholds bonuses on an inert item`() {
        val def = swordDef()
        val rk = ReforgeKey(ContentId.of("ramrpg", "sharp"))
        val reforges = reforgeRegistryWith(rk)
        val data = instance(def, reforge = rk)
        assertTrue(reforgeStatsFor(def, data, unmet, reforges).isEmpty())
    }

    @Test
    fun `reforge provider contributes bonuses on an active item`() {
        val def = swordDef()
        val rk = ReforgeKey(ContentId.of("ramrpg", "sharp"))
        val reforges = reforgeRegistryWith(rk)
        val data = instance(def, reforge = rk)
        assertTrue(reforgeStatsFor(def, data, met, reforges).isNotEmpty())
    }

    // -- SocketStatProvider ---------------------------------------------------------------------

    private fun gemRegistryWith(id: ContentId): dev.willram.ramrpg.api.sockets.GemRegistry {
        val registry = GemRegistryImpl()
        registry.register("test", GemDefinition(key = GemKey(id), displayName = Component.text("Ruby"), statContribution = mapOf(strength to 8.0)))
        return registry
    }

    @Test
    fun `socket provider withholds gem stats on an inert item`() {
        val def = swordDef()
        val gemId = ContentId.of("ramrpg", "ruby")
        val gems = gemRegistryWith(gemId)
        val data = instance(def, sockets = listOf(SocketData(key = ContentId.of("ramrpg", "socket1"), gem = gemId)))
        assertTrue(socketStatsFor(def, data, unmet, gems).isEmpty())
    }

    @Test
    fun `socket provider contributes gem stats on an active item`() {
        val def = swordDef()
        val gemId = ContentId.of("ramrpg", "ruby")
        val gems = gemRegistryWith(gemId)
        val data = instance(def, sockets = listOf(SocketData(key = ContentId.of("ramrpg", "socket1"), gem = gemId)))
        assertTrue(socketStatsFor(def, data, met, gems).isNotEmpty())
    }

    // -- No-self-satisfaction: requirementStateFor never reads item-based stats ------------------

    private class FixedLevelSkillService(private val fixedLevel: Int) : SkillService {
        override fun addXp(p: Player, src: XpSource, target: LivingEntity?, multiplier: Double) {}
        override fun level(p: Player, skill: SkillKey): Int = fixedLevel
        override fun xp(p: Player, skill: SkillKey): Double = 0.0
        override fun setLevel(p: Player, skill: SkillKey, level: Int) {}
    }

    private class EmptySkillRegistry : SkillRegistry {
        override fun register(owner: String, def: SkillDefinition) {}
        override fun unregisterOwner(owner: String) = 0
        override fun get(key: SkillKey): SkillDefinition? = null
        override fun all(): Collection<SkillDefinition> = emptyList()
    }

    @Test
    fun `a StatThreshold requirement cannot be satisfied by the player's item-derived stat total`() {
        val player: Player = ProxyFakes.proxy(Player::class.java, mapOf("getUniqueId" to UUID.randomUUID(), "getName" to "Steve"))

        // Simulate the player's CURRENT strength being entirely item-derived (e.g. from this very sword's
        // own +50 base stat) -- StatService reports 50.0 when asked for the live snapshot.
        val stats = StatServiceImpl()
        stats.registerDefinition(StatDefinition(strength, Component.text("Strength"), defaultBase = 0.0))
        stats.registerProvider(object : StatProvider {
            override fun provideStats(ctx: StatContext, output: MutableList<StatModifier>) {
                output += StatModifier(strength, 50.0, ModifierOperation.ADD, ModifierSource(SourceType.ITEM, ContentId.of("ramrpg", "test_sword")))
            }
        }, "test")
        assertEquals(50.0, stats.snapshot(player)[strength], 0.0001)

        // requirementStateFor must NOT reflect that item-derived total.
        val services = ItemRequirementServices(EmptySkillRegistry(), FixedLevelSkillService(1), stats)
        val state = requirementStateFor(player, services)
        assertEquals(0.0, state.statValue(strength), 0.0001)

        // So a sword requiring Strength >= 20 (which its own +50 would trivially satisfy if the state
        // leaked item stats) reads as UNMET.
        val req = ItemRequirement.StatThreshold(strength, 20.0)
        assertFalse(req.isMet(state))
    }

    @Test
    fun `requirementStateFor fails closed when services are not yet wired`() {
        val player: Player = ProxyFakes.proxy(Player::class.java, mapOf("getUniqueId" to UUID.randomUUID(), "getName" to "Steve"))
        val state = requirementStateFor(player, services = null)
        assertEquals(0, state.skillLevel(combat))
        assertEquals(0.0, state.statValue(strength))
    }
}
