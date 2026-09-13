package dev.willram.ramrpg

import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.enchants.RPGEnchantment
import dev.willram.ramrpg.api.entities.EntityProfile
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.EntityProfileKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.reforges.ReforgeDefinition
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.skills.SkillDefinition
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.sockets.GemDefinition
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatDefinition
import dev.willram.ramrpg.api.stats.StatDirtyReason
import dev.willram.ramrpg.api.stats.StatProvider
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.api.stats.StatSnapshot
import dev.willram.ramrpg.core.config.ContentRegistrarRpg
import dev.willram.ramrpg.core.config.RpgContentLoader
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * WP-1.5a: a valid item .conf loads through RpgContentLoader into the right ItemSpec, and
 * ContentRegistrarRpg turns it into an ItemDefinition with the right fields (including the
 * String -> Material resolution the pure spec defers to the registrar).
 */
class ContentLoaderItemSpecTest {

    @Test
    fun `valid item conf loads into the right ItemDefinition fields`(@TempDir root: Path) {
        writeConf(
            root, "items", "test_sword",
            """
            id = "ramrpg:test_sword"
            name = "<red>Test Sword"
            material = "DIAMOND_SWORD"
            rarity = "RARE"
            categories = ["SWORD"]
            base-stats {
              "ramrpg:damage" = 50.0
              "ramrpg:strength" = 20.0
            }
            description = ["<gray>A blade for tests."]
            stat-rolls = [
              { stat = "ramrpg:crit_chance", min = 0.0, max = 10.0 }
            ]
            max-stack = 1
            custom-model-data = 1001
            allow-vanilla-wrapper = false
            """,
        )

        val result = RpgContentLoader.load(root)
        assertTrue(result.errors().isEmpty(), "no load errors: ${result.errors()}")
        assertEquals(1, result.items.size)

        // The pure spec keeps material as a raw String (no Bukkit).
        val spec = result.items.single()
        assertEquals(ItemKey.of("ramrpg", "test_sword"), spec.key)
        assertEquals("DIAMOND_SWORD", spec.material)
        assertEquals(Rarity.RARE, spec.rarity)
        assertEquals(setOf(ItemCategory.SWORD), spec.categories)

        // The registrar resolves Material and builds the ItemDefinition.
        val items = CapturingItemRegistry()
        val errors = registrar(items).registerAll(result, "test")
        assertTrue(errors.isEmpty(), "no register errors: $errors")

        val def = items.get(ItemKey.of("ramrpg", "test_sword"))!!
        assertEquals(Material.DIAMOND_SWORD, def.material)
        assertEquals(Rarity.RARE, def.rarity)
        assertEquals(setOf(ItemCategory.SWORD), def.categories)
        assertEquals(1, def.maxStack)
        assertEquals(1001, def.customModelData)
        assertEquals(false, def.allowVanillaWrapper)
        assertEquals(MiniMessage.miniMessage().deserialize("<red>Test Sword"), def.displayName)

        val stats = def.baseStats.associate { it.stat to it.amount }
        assertEquals(50.0, stats[StatKey.of("ramrpg", "damage")])
        assertEquals(20.0, stats[StatKey.of("ramrpg", "strength")])
        val damageMod = def.baseStats.first { it.stat == StatKey.of("ramrpg", "damage") }
        assertEquals(ModifierOperation.ADD, damageMod.operation)
        assertEquals(SourceType.ITEM, damageMod.source.type)
        assertEquals(spec.id, damageMod.source.ref)

        val roll = def.statRolls.single()
        assertEquals(StatKey.of("ramrpg", "crit_chance"), roll.stat)
        assertEquals(0.0, roll.min)
        assertEquals(10.0, roll.max)
    }

    private fun registrar(items: ItemDefinitionRegistry) = ContentRegistrarRpg(
        stats = NoopStatService,
        items = items,
        skills = NoopSkillRegistry,
        enchants = NoopEnchantmentRegistry,
        entities = NoopEntityProfileRegistry,
        reforges = NoopReforgeRegistry,
        gems = NoopGemRegistry,
    )

    private fun writeConf(root: Path, type: String, name: String, body: String) {
        val dir = root.resolve(type)
        Files.createDirectories(dir)
        dir.resolve("$name.conf").toFile().writeText(body.trimIndent())
    }

    private class CapturingItemRegistry : ItemDefinitionRegistry {
        private val map = LinkedHashMap<ItemKey, ItemDefinition>()
        override fun get(key: ItemKey) = map[key]
        override fun register(owner: String, def: ItemDefinition) { map[def.key] = def }
        override fun unregisterOwner(owner: String) = 0
        override fun all() = map.values
        override fun revision() = 0
    }

    private object NoopStatService : StatService {
        override fun registerDefinition(def: StatDefinition) {}
        override fun definitions(): Collection<StatDefinition> = emptyList()
        override fun definition(key: StatKey): StatDefinition? = null
        override fun registerProvider(provider: StatProvider, owner: String) {}
        override fun unregisterProviders(owner: String) {}
        override fun markDirty(player: Player, reason: StatDirtyReason) {}
        override fun snapshot(player: Player): StatSnapshot = StatSnapshot(emptyMap())
        override fun recalculateNow(player: Player): StatSnapshot = StatSnapshot(emptyMap())
    }

    private object NoopSkillRegistry : SkillRegistry {
        override fun register(owner: String, def: SkillDefinition) {}
        override fun unregisterOwner(owner: String) = 0
        override fun get(key: SkillKey): SkillDefinition? = null
        override fun all(): Collection<SkillDefinition> = emptyList()
    }

    private object NoopEnchantmentRegistry : EnchantmentRegistry {
        override fun register(owner: String, ench: RPGEnchantment) {}
        override fun unregisterOwner(owner: String) = 0
        override fun get(key: EnchantmentKey): RPGEnchantment? = null
        override fun all(): Collection<RPGEnchantment> = emptyList()
    }

    private object NoopEntityProfileRegistry : EntityProfileRegistry {
        override fun register(owner: String, profile: EntityProfile) {}
        override fun unregisterOwner(owner: String) = 0
        override fun get(key: EntityProfileKey): EntityProfile? = null
        override fun resolve(entity: LivingEntity): EntityProfile? = null
        override fun all(): Collection<EntityProfile> = emptyList()
    }

    private object NoopReforgeRegistry : ReforgeRegistry {
        override fun register(owner: String, def: ReforgeDefinition) {}
        override fun unregisterOwner(owner: String) = 0
        override fun get(key: ReforgeKey): ReforgeDefinition? = null
        override fun all(): Collection<ReforgeDefinition> = emptyList()
    }

    private object NoopGemRegistry : GemRegistry {
        override fun register(owner: String, def: GemDefinition) {}
        override fun unregisterOwner(owner: String) = 0
        override fun get(key: GemKey): GemDefinition? = null
        override fun all(): Collection<GemDefinition> = emptyList()
    }
}
