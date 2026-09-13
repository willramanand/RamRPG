package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.enchants.RPGEnchantment
import dev.willram.ramrpg.api.entities.EntityProfile
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.EntityProfileKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.reforges.ReforgeDefinition
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.skills.SkillDefinition
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.sockets.GemDefinition
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.StatDefinition
import dev.willram.ramrpg.api.stats.StatDirtyReason
import dev.willram.ramrpg.api.stats.StatProvider
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.api.stats.StatSnapshot
import dev.willram.ramrpg.core.config.ContentRegistrarRpg
import dev.willram.ramrpg.core.config.RpgContentLoader
import dev.willram.ramrpg.core.modules.ContentModule
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * WP-1.5c: `ContentModule.applyToRegistries` must unregister owner [ContentModule.OWNER] from EVERY
 * registry the `ramrpg-content` pack touches -- items, skills, enchants, entities, reforges, gems -- not
 * a subset (the WP-1.5a review flagged the pre-existing `ramrpg-override` reload path in RamRPG.kt for
 * doing exactly that). This asserts registry STATE after the call, against fakes, with one entry per
 * registry that only exists "before" (must be gone after) and one that only exists "after" (must be
 * present after) -- so a registry left out of the unregister list would fail this test by still holding
 * its stale "before" entry.
 *
 * Pure: no Bukkit runtime, no PlatformScheduler, no dispatched command (BLOCKED on WP-RC2's
 * CommandTestHarness, per the WP contract) -- this calls the registry-mutation function directly.
 */
class ReloadUnregisterOwnerTest {

    private class TestStatService : StatService {
        val defs = HashMap<StatKey, StatDefinition>()
        override fun registerDefinition(def: StatDefinition) { defs[def.key] = def }
        override fun definitions() = defs.values
        override fun definition(key: StatKey) = defs[key]
        override fun registerProvider(provider: StatProvider, owner: String) {}
        override fun unregisterProviders(owner: String) {}
        override fun markDirty(player: Player, reason: StatDirtyReason) {}
        override fun snapshot(player: Player) = StatSnapshot(emptyMap())
        override fun recalculateNow(player: Player) = StatSnapshot(emptyMap())
    }

    private class TestItemRegistry : ItemDefinitionRegistry {
        private val owners = HashMap<ItemKey, String>()
        val map = LinkedHashMap<ItemKey, ItemDefinition>()
        override fun get(key: ItemKey) = map[key]
        override fun register(owner: String, def: ItemDefinition) { map[def.key] = def; owners[def.key] = owner }
        override fun unregisterOwner(owner: String): Int {
            val stale = owners.filterValues { it == owner }.keys.toList()
            stale.forEach { map.remove(it); owners.remove(it) }
            return stale.size
        }
        override fun all() = map.values
        override fun revision() = 0
    }

    private class TestSkillRegistry : SkillRegistry {
        private val owners = HashMap<SkillKey, String>()
        val map = LinkedHashMap<SkillKey, SkillDefinition>()
        override fun register(owner: String, def: SkillDefinition) { map[def.key] = def; owners[def.key] = owner }
        override fun unregisterOwner(owner: String): Int {
            val stale = owners.filterValues { it == owner }.keys.toList()
            stale.forEach { map.remove(it); owners.remove(it) }
            return stale.size
        }
        override fun get(key: SkillKey) = map[key]
        override fun all() = map.values
    }

    private class TestEnchantmentRegistry : EnchantmentRegistry {
        private val owners = HashMap<EnchantmentKey, String>()
        val map = LinkedHashMap<EnchantmentKey, RPGEnchantment>()
        override fun register(owner: String, ench: RPGEnchantment) { map[ench.key] = ench; owners[ench.key] = owner }
        override fun unregisterOwner(owner: String): Int {
            val stale = owners.filterValues { it == owner }.keys.toList()
            stale.forEach { map.remove(it); owners.remove(it) }
            return stale.size
        }
        override fun get(key: EnchantmentKey) = map[key]
        override fun all() = map.values
    }

    private class TestEntityRegistry : EntityProfileRegistry {
        private val owners = HashMap<EntityProfileKey, String>()
        val map = LinkedHashMap<EntityProfileKey, EntityProfile>()
        override fun register(owner: String, profile: EntityProfile) { map[profile.key] = profile; owners[profile.key] = owner }
        override fun unregisterOwner(owner: String): Int {
            val stale = owners.filterValues { it == owner }.keys.toList()
            stale.forEach { map.remove(it); owners.remove(it) }
            return stale.size
        }
        override fun get(key: EntityProfileKey) = map[key]
        override fun resolve(entity: LivingEntity) = null
        override fun all() = map.values
    }

    private class TestReforgeRegistry : ReforgeRegistry {
        private val owners = HashMap<ReforgeKey, String>()
        val map = LinkedHashMap<ReforgeKey, ReforgeDefinition>()
        override fun register(owner: String, def: ReforgeDefinition) { map[def.key] = def; owners[def.key] = owner }
        override fun unregisterOwner(owner: String): Int {
            val stale = owners.filterValues { it == owner }.keys.toList()
            stale.forEach { map.remove(it); owners.remove(it) }
            return stale.size
        }
        override fun get(key: ReforgeKey) = map[key]
        override fun all() = map.values
    }

    private class TestGemRegistry : GemRegistry {
        private val owners = HashMap<GemKey, String>()
        val map = LinkedHashMap<GemKey, GemDefinition>()
        override fun register(owner: String, def: GemDefinition) { map[def.key] = def; owners[def.key] = owner }
        override fun unregisterOwner(owner: String): Int {
            val stale = owners.filterValues { it == owner }.keys.toList()
            stale.forEach { map.remove(it); owners.remove(it) }
            return stale.size
        }
        override fun get(key: GemKey) = map[key]
        override fun all() = map.values
    }

    @Test
    fun `reload unregisters the owner from every registry then re-registers`(
        @TempDir before: Path,
        @TempDir after: Path,
    ) {
        writeConf(before, "items", "old", "id = \"ramrpg:item_old\"\nmaterial = \"APPLE\"")
        writeConf(before, "skills", "old", "id = \"ramrpg:skill_old\"")
        writeConf(before, "enchants", "old", "id = \"ramrpg:ench_old\"")
        writeConf(before, "entities", "old", "id = \"ramrpg:ent_old\"")
        writeConf(before, "reforges", "old", "id = \"ramrpg:reforge_old\"")
        writeConf(before, "gems", "old", "id = \"ramrpg:gem_old\"")

        writeConf(after, "items", "new", "id = \"ramrpg:item_new\"\nmaterial = \"STICK\"")
        writeConf(after, "skills", "new", "id = \"ramrpg:skill_new\"")
        writeConf(after, "enchants", "new", "id = \"ramrpg:ench_new\"")
        writeConf(after, "entities", "new", "id = \"ramrpg:ent_new\"")
        writeConf(after, "reforges", "new", "id = \"ramrpg:reforge_new\"")
        writeConf(after, "gems", "new", "id = \"ramrpg:gem_new\"")

        val items = TestItemRegistry()
        val skills = TestSkillRegistry()
        val enchants = TestEnchantmentRegistry()
        val entities = TestEntityRegistry()
        val reforges = TestReforgeRegistry()
        val gems = TestGemRegistry()
        val registrar = ContentRegistrarRpg(
            stats = TestStatService(),
            items = items,
            skills = skills,
            enchants = enchants,
            entities = entities,
            reforges = reforges,
            gems = gems,
        )

        val beforeResult = RpgContentLoader.load(before)
        val seedErrors = registrar.registerAll(beforeResult, ContentModule.OWNER)
        assertTrue(seedErrors.isEmpty(), "seed load should be clean: $seedErrors")

        // Sanity: every "old" entry is live before reload.
        assertNotNull(items.get(ItemKey.of("ramrpg", "item_old")))
        assertNotNull(skills.get(SkillKey.of("ramrpg", "skill_old")))
        assertNotNull(enchants.get(EnchantmentKey.of("ramrpg", "ench_old")))
        assertNotNull(entities.get(EntityProfileKey.of("ramrpg", "ent_old")))
        assertNotNull(reforges.get(ReforgeKey(ContentId.of("ramrpg", "reforge_old"))))
        assertNotNull(gems.get(GemKey.of("ramrpg", "gem_old")))

        val afterResult = RpgContentLoader.load(after)
        val reloadErrors = ContentModule.applyToRegistries(
            registrar, afterResult, items, skills, enchants, entities, reforges, gems,
        )
        assertTrue(reloadErrors.isEmpty(), "reload should be clean: $reloadErrors")

        // Every "old" entry is gone -- unregisterOwner ran for ALL SIX registries, not a subset.
        assertNull(items.get(ItemKey.of("ramrpg", "item_old")), "stale item must be unregistered")
        assertNull(skills.get(SkillKey.of("ramrpg", "skill_old")), "stale skill must be unregistered")
        assertNull(enchants.get(EnchantmentKey.of("ramrpg", "ench_old")), "stale enchant must be unregistered")
        assertNull(entities.get(EntityProfileKey.of("ramrpg", "ent_old")), "stale entity must be unregistered")
        assertNull(reforges.get(ReforgeKey(ContentId.of("ramrpg", "reforge_old"))), "stale reforge must be unregistered")
        assertNull(gems.get(GemKey.of("ramrpg", "gem_old")), "stale gem must be unregistered")

        // Every "new" entry is registered.
        assertNotNull(items.get(ItemKey.of("ramrpg", "item_new")))
        assertNotNull(skills.get(SkillKey.of("ramrpg", "skill_new")))
        assertNotNull(enchants.get(EnchantmentKey.of("ramrpg", "ench_new")))
        assertNotNull(entities.get(EntityProfileKey.of("ramrpg", "ent_new")))
        assertNotNull(reforges.get(ReforgeKey(ContentId.of("ramrpg", "reforge_new"))))
        assertNotNull(gems.get(GemKey.of("ramrpg", "gem_new")))

        assertEquals(1, items.map.size)
        assertEquals(1, skills.map.size)
        assertEquals(1, enchants.map.size)
        assertEquals(1, entities.map.size)
        assertEquals(1, reforges.map.size)
        assertEquals(1, gems.map.size)
    }

    private fun writeConf(root: Path, type: String, name: String, body: String) {
        val dir = root.resolve(type)
        Files.createDirectories(dir)
        dir.resolve("$name.conf").toFile().writeText(body)
    }
}
