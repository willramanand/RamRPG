package dev.willram.ramrpg

import com.google.gson.GsonBuilder
import dev.willram.ramrpg.api.entities.EntityProfile
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.identity.EntityProfileKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.skills.SkillDefinition
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.XpCurves
import dev.willram.ramrpg.api.stats.StatDefinition
import dev.willram.ramrpg.api.stats.StatDirtyReason
import dev.willram.ramrpg.api.stats.StatProvider
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.api.stats.StatSnapshot
import dev.willram.ramrpg.core.config.ContentOverrideLoader
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ContentOverrideLoaderTest {

    private class TestStatService : StatService {
        val defs = HashMap<StatKey, StatDefinition>()
        override fun registerDefinition(def: StatDefinition) { defs[def.key] = def }
        override fun definitions() = defs.values
        override fun definition(key: StatKey) = defs[key]
        override fun registerProvider(provider: StatProvider, owner: String) {}
        override fun unregisterProviders(owner: String) {}
        override fun markDirty(player: org.bukkit.entity.Player, reason: StatDirtyReason) {}
        override fun snapshot(player: org.bukkit.entity.Player) = StatSnapshot(emptyMap())
        override fun recalculateNow(player: org.bukkit.entity.Player) = StatSnapshot(emptyMap())
    }

    @Test
    fun `stat override only changes specified fields`(@TempDir tmp: Path) {
        val statsDir = tmp.resolve("stats").toFile().also { it.mkdirs() }
        val gson = GsonBuilder().setPrettyPrinting().create()
        // FileDataRepository url-encodes keys, so `ramrpg:damage` becomes `ramrpg%3Adamage`
        File(statsDir, "ramrpg%3Adamage.json").writeText(
            gson.toJson(mapOf("defaultBase" to 9.5, "min" to 0.0))
        )
        val svc = TestStatService()
        val baseline = StatDefinition(
            key = StatKey.of("ramrpg", "damage"),
            displayName = Component.text("Damage"),
            symbol = "D",
            defaultBase = 5.0,
            perLevel = 0.5,
            min = null,
            max = 100.0,
        )
        svc.registerDefinition(baseline)

        ContentOverrideLoader(tmp).apply(svc, EmptyItemRegistry)

        val updated = svc.definition(baseline.key)!!
        assertEquals(9.5, updated.defaultBase, 0.0001)
        assertEquals(0.0, updated.min)
        // unchanged fields preserved
        assertEquals(0.5, updated.perLevel, 0.0001)
        assertEquals(100.0, updated.max)
        assertEquals("D", updated.symbol)
    }

    private object EmptyItemRegistry : ItemDefinitionRegistry {
        override fun get(key: ItemKey) = null
        override fun register(owner: String, def: ItemDefinition) {}
        override fun unregisterOwner(owner: String) = 0
        override fun all() = emptyList<ItemDefinition>()
        override fun revision() = 0
    }

    private class TestItemRegistry : ItemDefinitionRegistry {
        val map = HashMap<ItemKey, ItemDefinition>()
        override fun get(key: ItemKey) = map[key]
        override fun register(owner: String, def: ItemDefinition) { map[def.key] = def }
        override fun unregisterOwner(owner: String) = 0
        override fun all() = map.values
        override fun revision() = 0
    }

    private class TestSkillRegistry : SkillRegistry {
        val map = HashMap<SkillKey, SkillDefinition>()
        override fun register(owner: String, def: SkillDefinition) { map[def.key] = def }
        override fun unregisterOwner(owner: String) = 0
        override fun get(key: SkillKey) = map[key]
        override fun all() = map.values
    }

    private class TestEntityRegistry : EntityProfileRegistry {
        val map = HashMap<EntityProfileKey, EntityProfile>()
        override fun register(owner: String, profile: EntityProfile) { map[profile.key] = profile }
        override fun unregisterOwner(owner: String) = 0
        override fun get(key: EntityProfileKey) = map[key]
        override fun resolve(entity: org.bukkit.entity.LivingEntity) = null
        override fun all() = map.values
    }

    @Test
    fun `item override changes baseStats and rarity`(@TempDir tmp: Path) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        File(tmp.resolve("items").toFile().apply { mkdirs() }, "ramrpg%3Asword.json").writeText(
            gson.toJson(mapOf(
                "baseStats" to mapOf("ramrpg:damage" to 15.0),
                "rarity" to "epic",
            ))
        )
        val items = TestItemRegistry()
        items.register("base", ItemDefinition(
            key = ItemKey.of("ramrpg", "sword"),
            displayName = Component.text("Sword"),
            material = Material.IRON_SWORD,
            rarity = Rarity.COMMON,
            categories = setOf(ItemCategory.SWORD),
            allowVanillaWrapper = true,
        ))

        ContentOverrideLoader(tmp).apply(TestStatService(), items)

        val updated = items.get(ItemKey.of("ramrpg", "sword"))!!
        assertEquals(Rarity.EPIC, updated.rarity)
        assertEquals(15.0, updated.baseStats.single().amount, 0.0001)
        assertEquals(true, updated.allowVanillaWrapper)
    }

    @Test
    fun `skill override changes maxLevel and curve`(@TempDir tmp: Path) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        File(tmp.resolve("skills").toFile().apply { mkdirs() }, "ramrpg%3Acombat.json").writeText(
            gson.toJson(mapOf("maxLevel" to 99, "xpBase" to 10.0, "xpMul" to 5.0))
        )
        val skills = TestSkillRegistry()
        skills.register("base", SkillDefinition(
            key = SkillKey.of("ramrpg", "combat"),
            displayName = Component.text("Combat"),
            description = Component.text(""),
            maxLevel = 50,
            xpCurve = XpCurves.polynomial(50.0, 25.0),
        ))

        ContentOverrideLoader(tmp).apply(TestStatService(), EmptyItemRegistry, skills)

        val updated = skills.get(SkillKey.of("ramrpg", "combat"))!!
        assertEquals(99, updated.maxLevel)
        assertEquals(15.0, updated.xpCurve.xpToReach(1), 0.0001)
    }

    @Test
    fun `entity override changes baseStats + xp`(@TempDir tmp: Path) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        File(tmp.resolve("entities").toFile().apply { mkdirs() }, "ramrpg%3Azombie.json").writeText(
            gson.toJson(mapOf(
                "baseStats" to mapOf("ramrpg:health" to 99.0),
                "xpAmount" to 12.5,
            ))
        )
        val entities = TestEntityRegistry()
        entities.register("base", EntityProfile(
            key = EntityProfileKey.of("ramrpg", "zombie"),
            baseStats = mapOf(StatKey.of("ramrpg", "health") to 20.0),
            xpAmount = 7.0,
            xpSourceKey = XpSourceKey.of("ramrpg", "kill_zombie"),
            skill = SkillKey.of("ramrpg", "combat"),
        ))

        ContentOverrideLoader(tmp).apply(TestStatService(), EmptyItemRegistry, entities = entities)

        val updated = entities.get(EntityProfileKey.of("ramrpg", "zombie"))!!
        assertEquals(99.0, updated.baseStats[StatKey.of("ramrpg", "health")])
        assertEquals(12.5, updated.xpAmount)
        assertEquals(SkillKey.of("ramrpg", "combat"), updated.skill)
    }
}
