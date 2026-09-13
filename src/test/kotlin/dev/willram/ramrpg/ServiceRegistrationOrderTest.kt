package dev.willram.ramrpg

import dev.willram.ramcore.service.ServiceKey
import dev.willram.ramcore.service.ServiceRegistry
import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramcore.testkit.TestServiceContext
import dev.willram.ramrpg.api.abilities.AbilityRegistry
import dev.willram.ramrpg.api.abilities.AbilityService
import dev.willram.ramrpg.api.combat.DamagePipeline
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
import dev.willram.ramrpg.core.services.RpgServiceGraph
import dev.willram.ramrpg.core.services.RpgServiceKeys
import dev.willram.ramrpg.core.storage.PlayerStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-1.7b: proves the order [dev.willram.ramrpg.RamRPG.load] constructs and registers the 18 RPG
 * services in (recorded as [RpgServiceGraph.constructionOrder]) is a valid topological order of the
 * service dependency DAG ([RpgServiceGraph.dependencies]) -- every service is built after each of the
 * services its constructor needs. Entirely off-server: `RamRPG` (a Bukkit `JavaPlugin`) is never
 * constructed; the graph is the test-guarded source of truth the `load()` body follows.
 */
class ServiceRegistrationOrderTest {

    @Test
    fun `the graph covers exactly the 18 declared services`() {
        assertEquals(RpgServiceKeys.all(), RpgServiceGraph.dependencies.keys.toSet())
        assertEquals(RpgServiceKeys.all(), RpgServiceGraph.constructionOrder.toSet())
        assertEquals(
            RpgServiceGraph.constructionOrder.size,
            RpgServiceGraph.constructionOrder.toSet().size,
            "construction order must not list a service twice",
        )
        // Every declared dependency edge points at a real, known service.
        for ((key, deps) in RpgServiceGraph.dependencies) {
            for (dep in deps) {
                assertTrue(dep in RpgServiceKeys.all(), "$key depends on unknown service $dep")
            }
        }
    }

    @Test
    fun `every dependency is constructed before its dependent`() {
        val position: Map<ServiceKey<*>, Int> =
            RpgServiceGraph.constructionOrder.withIndex().associate { (i, key) -> key to i }

        for ((key, deps) in RpgServiceGraph.dependencies) {
            val here = position.getValue(key)
            for (dep in deps) {
                assertTrue(
                    position.getValue(dep) < here,
                    "$dep must be constructed before $key (dependency precedes dependent)",
                )
            }
        }
    }

    @Test
    fun `RamCore's registry accepts the declared order with dependsOn edges`() {
        // Register the stub-able services (16 of 18: EconomyService and QuestService are concrete
        // classes ProxyFakes cannot stub -- see ServiceWiringTest) in construction order, wiring each
        // one's dependsOn edges. loadAll() runs RamCore's own topological resolver: a bad order or a
        // cycle in the declared graph would make it throw. None of these 16 depend on the two excluded
        // services, so the sub-graph is self-contained.
        val registry: ServiceRegistry = TestServiceContext.withRegistry().services()
        val stubbable = RpgServiceKeys.all() - setOf(RpgServiceKeys.ECONOMY, RpgServiceKeys.QUESTS)

        for (key in RpgServiceGraph.constructionOrder) {
            if (key !in stubbable) continue
            @Suppress("UNCHECKED_CAST")
            val reg = registry.register(key as ServiceKey<Any>, stub(key))
            for (dep in RpgServiceGraph.dependencies.getValue(key)) {
                if (dep in stubbable) reg.dependsOn(dep)
            }
        }

        registry.loadAll()
    }

    private fun stub(key: ServiceKey<*>): Any = when (key) {
        RpgServiceKeys.PLATFORM -> ProxyFakes.stub(PlatformScheduler::class.java)
        RpgServiceKeys.PLAYER_STORE -> ProxyFakes.stub(PlayerStore::class.java)
        RpgServiceKeys.STATS -> ProxyFakes.stub(StatService::class.java)
        RpgServiceKeys.SKILL_REGISTRY -> ProxyFakes.stub(SkillRegistry::class.java)
        RpgServiceKeys.SKILL_SERVICE -> ProxyFakes.stub(SkillService::class.java)
        RpgServiceKeys.ITEM_DEFINITIONS -> ProxyFakes.stub(ItemDefinitionRegistry::class.java)
        RpgServiceKeys.ITEM_INSTANCES -> ProxyFakes.stub(ItemInstanceService::class.java)
        RpgServiceKeys.ENCHANTMENTS -> ProxyFakes.stub(EnchantmentRegistry::class.java)
        RpgServiceKeys.ENTITY_PROFILES -> ProxyFakes.stub(EntityProfileRegistry::class.java)
        RpgServiceKeys.ABILITIES -> ProxyFakes.stub(AbilityRegistry::class.java)
        RpgServiceKeys.ABILITY_SERVICE -> ProxyFakes.stub(AbilityService::class.java)
        RpgServiceKeys.DAMAGE_PIPELINE -> ProxyFakes.stub(DamagePipeline::class.java)
        RpgServiceKeys.RENDERER -> ProxyFakes.stub(PacketItemRenderer::class.java)
        RpgServiceKeys.REFORGES -> ProxyFakes.stub(ReforgeRegistry::class.java)
        RpgServiceKeys.GEMS -> ProxyFakes.stub(GemRegistry::class.java)
        RpgServiceKeys.QUEST_REGISTRY -> ProxyFakes.stub(QuestRegistry::class.java)
        else -> error("no stub for $key")
    }
}
