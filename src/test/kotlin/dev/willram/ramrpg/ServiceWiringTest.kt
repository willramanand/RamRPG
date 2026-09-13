package dev.willram.ramrpg

import dev.willram.ramcore.service.ServiceKey
import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramcore.testkit.TestServiceContext
import dev.willram.ramrpg.api.abilities.AbilityRegistry
import dev.willram.ramrpg.api.abilities.AbilityService
import dev.willram.ramrpg.api.combat.DamagePipeline
import dev.willram.ramrpg.api.crafting.CraftingService
import dev.willram.ramrpg.api.crafting.RecipeRegistry
import dev.willram.ramrpg.api.crafting.StationRegistry
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.quests.QuestRegistry
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.platform.PlatformScheduler
import dev.willram.ramrpg.core.rendering.PacketItemRenderer
import dev.willram.ramrpg.core.services.RpgServiceKeys
import dev.willram.ramrpg.core.storage.PlayerStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * WP-1.7a: exercises the real RamCore `dev.willram.ramcore.service.ServiceRegistry` / `ServiceKey`
 * machinery against `RpgServiceKeys` -- the same classes [dev.willram.ramrpg.RamRPG]'s
 * `registerServices()` (called from `load()`) drives -- entirely off-server.
 *
 * `RamRPG` itself extends Bukkit's `JavaPlugin` (via RamCore's `RamPlugin`) and cannot be constructed
 * in a unit test, so this uses RamCore's own test fixture, `TestServiceContext`
 * (`dev.willram.ramcore.testkit`, from the `ramcore-test` artifact already on the test classpath), the
 * same fixture RamCore's own `ServiceRegistryTest` uses.
 *
 * 19 of the 21 keyed RPG types are plain interfaces, stood in here by `ProxyFakes.stub(...)`.
 * `RpgServiceKeys.ECONOMY` (`EconomyService`) and `RpgServiceKeys.QUESTS` (`QuestService`, which takes
 * an `EconomyService`) are deliberately NOT registered with live instances in the "resolves" test
 * below: `EconomyService`'s class file references `net.milkbowl.vault.economy.Economy`, which is only
 * a `compileOnly` dependency of the main source set (never on the test classpath), so merely
 * constructing `EconomyService()` here throws `NoClassDefFoundError` -- a pre-existing, out-of-scope
 * condition (rule 9) that predates WP-1.7a and is identical whether `EconomyService` is built in
 * `load()` or `enable()`. Those two keys are still covered by the uniqueness/coverage test below, which
 * needs no instances at all.
 */
class ServiceWiringTest {

    private val STUB_ONLY_KEYS = RpgServiceKeys.all() - setOf(RpgServiceKeys.ECONOMY, RpgServiceKeys.QUESTS)

    @Test
    fun `keys are unique and cover exactly the declared RPG services`() {
        val keys = RpgServiceKeys.all()
        val expectedIds = setOf(
            "rpg-platform", "rpg-player-store", "rpg-stats", "rpg-skill-registry", "rpg-skill-service",
            "rpg-item-definitions", "rpg-item-instances", "rpg-enchantments", "rpg-entity-profiles",
            "rpg-abilities", "rpg-ability-service", "rpg-damage-pipeline", "rpg-renderer", "rpg-reforges",
            "rpg-gems", "rpg-economy", "rpg-quest-registry", "rpg-quests",
            "rpg-recipe-registry", "rpg-station-registry", "rpg-crafting-service",
        )
        assertEquals(21, keys.size, "expected one key per RPG subsystem service")
        assertEquals(expectedIds, keys.map { it.id() }.toSet())
        assertEquals(keys.size, keys.map { it.type() }.toSet().size, "no two keys may share a service type")
    }

    @Test
    fun `every stub-able key registers and resolves back to the same instance`() {
        val registry = TestServiceContext.withRegistry().services()

        val instances: Map<ServiceKey<*>, Any> = linkedMapOf(
            RpgServiceKeys.PLATFORM to ProxyFakes.stub(PlatformScheduler::class.java),
            RpgServiceKeys.PLAYER_STORE to ProxyFakes.stub(PlayerStore::class.java),
            RpgServiceKeys.STATS to ProxyFakes.stub(StatService::class.java),
            RpgServiceKeys.SKILL_REGISTRY to ProxyFakes.stub(SkillRegistry::class.java),
            RpgServiceKeys.SKILL_SERVICE to ProxyFakes.stub(SkillService::class.java),
            RpgServiceKeys.ITEM_DEFINITIONS to ProxyFakes.stub(ItemDefinitionRegistry::class.java),
            RpgServiceKeys.ITEM_INSTANCES to ProxyFakes.stub(ItemInstanceService::class.java),
            RpgServiceKeys.ENCHANTMENTS to ProxyFakes.stub(EnchantmentRegistry::class.java),
            RpgServiceKeys.ENTITY_PROFILES to ProxyFakes.stub(EntityProfileRegistry::class.java),
            RpgServiceKeys.ABILITIES to ProxyFakes.stub(AbilityRegistry::class.java),
            RpgServiceKeys.ABILITY_SERVICE to ProxyFakes.stub(AbilityService::class.java),
            RpgServiceKeys.DAMAGE_PIPELINE to ProxyFakes.stub(DamagePipeline::class.java),
            RpgServiceKeys.RENDERER to ProxyFakes.stub(PacketItemRenderer::class.java),
            RpgServiceKeys.REFORGES to ProxyFakes.stub(ReforgeRegistry::class.java),
            RpgServiceKeys.GEMS to ProxyFakes.stub(GemRegistry::class.java),
            RpgServiceKeys.QUEST_REGISTRY to ProxyFakes.stub(QuestRegistry::class.java),
            RpgServiceKeys.RECIPE_REGISTRY to ProxyFakes.stub(RecipeRegistry::class.java),
            RpgServiceKeys.STATION_REGISTRY to ProxyFakes.stub(StationRegistry::class.java),
            RpgServiceKeys.CRAFTING_SERVICE to ProxyFakes.stub(CraftingService::class.java),
        )

        assertEquals(STUB_ONLY_KEYS, instances.keys, "test fixture must cover every stub-able key")

        for ((key, instance) in instances) {
            @Suppress("UNCHECKED_CAST")
            registry.register(key as ServiceKey<Any>, instance)
        }

        // Coverage: every stub-able key was registered, and nothing extra was.
        assertEquals(STUB_ONLY_KEYS, registry.keys())

        // No missing dependency, no cycle -- RamCore's SimpleServiceRegistry.resolveLifecycleOrder
        // would throw from loadAll() otherwise.
        registry.loadAll()

        for ((key, instance) in instances) {
            assertSame(instance, registry.require(key), "$key must resolve back to the instance it was registered with")
        }
    }

    @Test
    fun `duplicate registration of the same key is rejected`() {
        val registry = TestServiceContext.withRegistry().services()
        registry.register(RpgServiceKeys.STATS, ProxyFakes.stub(StatService::class.java))

        assertThrows(IllegalArgumentException::class.java) {
            registry.register(RpgServiceKeys.STATS, ProxyFakes.stub(StatService::class.java))
        }
    }

    @Test
    fun `registration after loadAll is rejected -- this is why RamRPG registers services from load()`() {
        val registry = TestServiceContext.withRegistry().services()
        registry.register(RpgServiceKeys.STATS, ProxyFakes.stub(StatService::class.java))
        registry.loadAll()

        assertThrows(IllegalStateException::class.java) {
            registry.register(RpgServiceKeys.SKILL_REGISTRY, ProxyFakes.stub(SkillRegistry::class.java))
        }
    }

    @Test
    fun `a genuine dependency cycle among RPG keys is caught by RamCore's registry`() {
        val registry = TestServiceContext.withRegistry().services()
        registry.register(RpgServiceKeys.STATS, ProxyFakes.stub(StatService::class.java))
            .dependsOn(RpgServiceKeys.SKILL_REGISTRY)
        registry.register(RpgServiceKeys.SKILL_REGISTRY, ProxyFakes.stub(SkillRegistry::class.java))
            .dependsOn(RpgServiceKeys.STATS)

        assertThrows(IllegalStateException::class.java) { registry.loadAll() }
    }
}
