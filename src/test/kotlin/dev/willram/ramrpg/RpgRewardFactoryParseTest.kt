package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentRegistrar
import dev.willram.ramcore.content.SpecLoader
import dev.willram.ramcore.content.spec.RewardPlanSpec
import dev.willram.ramcore.economy.Economies
import dev.willram.ramcore.reward.RewardActionFactories
import dev.willram.ramcore.reward.RewardContext
import dev.willram.ramcore.testkit.ProxyFakes
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.core.rewards.BuffRewardFactory
import dev.willram.ramrpg.core.rewards.PerkPointRewardFactory
import dev.willram.ramrpg.core.rewards.RpgItemRewardFactory
import dev.willram.ramrpg.core.rewards.SkillXpRewardFactory
import dev.willram.ramrpg.core.services.ItemDefinitionRegistryImpl
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.spongepowered.configurate.BasicConfigurationNode
import org.spongepowered.configurate.ConfigurationNode
import java.nio.file.Files
import java.nio.file.Path

/**
 * WP-1.2b: bad reward params must throw [ContentDeserializeException] naming the offending key (the
 * contract [dev.willram.ramcore.reward.RewardActionFactory.create] documents), and a surrounding
 * [SpecLoader]/`ContentLoader` pass must turn that exception into a [dev.willram.ramcore.exception.ValidationError]
 * carrying the source file and path -- exactly how RamCore's own reward types behave
 * (`RewardPlanSpec`/`ContentRegistrar`, see RamCore's `SpecLoaderTest`).
 */
class RpgRewardFactoryParseTest {

    private val scheduler = RecordingScheduler()
    private val itemDefs = ItemDefinitionRegistryImpl().apply {
        register(
            "test",
            ItemDefinition(
                key = ItemKey.of("ramrpg", "iron_sword"),
                displayName = Component.text("Iron Sword"),
                material = Material.IRON_SWORD,
                rarity = Rarity.COMMON,
                categories = setOf(ItemCategory.SWORD),
            ),
        )
    }
    private val skillFactory = SkillXpRewardFactory(ProxyFakes.stub(SkillService::class.java), scheduler)
    private val itemFactory = RpgItemRewardFactory(itemDefs, ProxyFakes.stub(ItemInstanceService::class.java), scheduler)
    private val buffFactory = BuffRewardFactory()
    private val perkFactory = PerkPointRewardFactory()

    private fun node(vararg entries: Pair<String, Any>): ConfigurationNode =
        BasicConfigurationNode.root().apply {
            for ((key, value) in entries) node(key).set(value)
        }

    // --- skill_xp -----------------------------------------------------------------------------

    @Test
    fun `skill_xp factory requires a skill`() {
        val ex = assertThrows(ContentDeserializeException::class.java) {
            skillFactory.create(node("amount" to 10.0))
        }
        assertTrue(ex.message!!.contains("'skill'"), ex.message)
    }

    @Test
    fun `skill_xp factory rejects a bare skill id without a namespace`() {
        val ex = assertThrows(ContentDeserializeException::class.java) {
            skillFactory.create(node("skill" to "combat", "amount" to 10.0))
        }
        assertTrue(ex.message!!.contains("skill"), ex.message)
    }

    @Test
    fun `skill_xp factory requires a positive amount`() {
        val ex = assertThrows(ContentDeserializeException::class.java) {
            skillFactory.create(node("skill" to "ramrpg:combat", "amount" to -5.0))
        }
        assertTrue(ex.message!!.contains("'amount'"), ex.message)
    }

    // --- rpg_item -------------------------------------------------------------------------------

    @Test
    fun `rpg_item factory requires an item`() {
        val ex = assertThrows(ContentDeserializeException::class.java) {
            itemFactory.create(node())
        }
        assertTrue(ex.message!!.contains("'item'"), ex.message)
    }

    @Test
    fun `rpg_item factory rejects an unknown item key`() {
        val ex = assertThrows(ContentDeserializeException::class.java) {
            itemFactory.create(node("item" to "ramrpg:does_not_exist"))
        }
        assertTrue(ex.message!!.contains("does_not_exist"), ex.message)
    }

    @Test
    fun `rpg_item factory rejects a non-positive count`() {
        val ex = assertThrows(ContentDeserializeException::class.java) {
            itemFactory.create(node("item" to "ramrpg:iron_sword", "count" to 0))
        }
        assertTrue(ex.message!!.contains("'count'"), ex.message)
    }

    // --- buff / perk_point stubs: malformed content still throws at parse time, but even
    // well-formed content is refused at validate() until the real systems land (WP-1.2b rule 8). ----

    @Test
    fun `buff factory requires a buff id`() {
        val ex = assertThrows(ContentDeserializeException::class.java) {
            buffFactory.create(node())
        }
        assertTrue(ex.message!!.contains("'buff'"), ex.message)
    }

    @Test
    fun `buff factory never validates clean, even with well-formed params`() {
        val action = buffFactory.create(node("buff" to "ramrpg:strength_potion", "duration_ticks" to 200, "stacks" to 1))
        val errors = action.validate(RewardContext.of("test"))
        assertFalse(errors.isEmpty(), "buff reward must fail validation until WP-4.1a lands")
    }

    @Test
    fun `perk_point factory rejects a non-positive points value`() {
        val ex = assertThrows(ContentDeserializeException::class.java) {
            perkFactory.create(node("points" to 0))
        }
        assertTrue(ex.message!!.contains("'points'"), ex.message)
    }

    @Test
    fun `perk_point factory never validates clean, even with well-formed params`() {
        val action = perkFactory.create(node("points" to 3))
        val errors = action.validate(RewardContext.of("test"))
        assertFalse(errors.isEmpty(), "perk_point reward must fail validation until WP-5.1a lands")
    }

    // --- surrounding SpecLoader/ContentLoader pass ----------------------------------------------

    /**
     * Mirrors RamCore's own `SpecLoaderTest`: `RewardPlanSpec.deserialize` only validates that a
     * reward `type` is known, not that its params are well-formed (that happens lazily in
     * `ContentRegistrar.toRewardPlan`). So this loader eagerly builds the plan inside the `rewards`
     * deserializer -- the same thing a real content-loading pass must do to catch bad params at load
     * time instead of first use -- which is what lets `SpecLoader.deserialize` catch the
     * `ContentDeserializeException` our factory throws and turn it into a `ValidationError` carrying
     * the definition's `SourceRef` (file + path).
     */
    @Test
    fun `a bad skill_xp param becomes a ValidationError with the source file and path`(@TempDir root: Path) {
        val factories = RewardActionFactories.standard(Economies.inMemory()).register(skillFactory)
        val loader = SpecLoader.create().deserializer("rewards") { node ->
            val spec = RewardPlanSpec.deserialize(node, factories)
            ContentRegistrar.toRewardPlan(spec, factories)
            spec
        }

        writeContent(
            root, "rewards", "bad_xp.yml",
            "id: test:bad_xp",
            "entries:",
            "  - id: payout",
            "    type: skill_xp",
            "    guaranteed: true",
            "    amount: 10",
        )

        val result = loader.load(root)

        assertFalse(result.successful())
        assertEquals(1, result.errors().size, result.errors().toString())
        val error = result.errors().single()
        assertEquals("bad_xp.yml", error.source())
        assertEquals("test:bad_xp", error.path())
        assertTrue(error.message().contains("'skill'"), error.message())
    }

    @Test
    fun `a well-formed skill_xp reward loads and builds cleanly through the same pass`(@TempDir root: Path) {
        val factories = RewardActionFactories.standard(Economies.inMemory()).register(skillFactory)
        val loader = SpecLoader.create().deserializer("rewards") { node ->
            val spec = RewardPlanSpec.deserialize(node, factories)
            ContentRegistrar.toRewardPlan(spec, factories)
            spec
        }

        writeContent(
            root, "rewards", "good_xp.yml",
            "id: test:good_xp",
            "entries:",
            "  - id: payout",
            "    type: skill_xp",
            "    guaranteed: true",
            "    skill: ramrpg:combat",
            "    amount: 10",
        )

        val result = loader.load(root)

        assertTrue(result.successful(), result.errors().toString())
    }

    private fun writeContent(root: Path, type: String, name: String, vararg lines: String) {
        val dir = root.resolve(type)
        Files.createDirectories(dir)
        Files.writeString(dir.resolve(name), lines.joinToString("\n") + "\n")
    }
}
