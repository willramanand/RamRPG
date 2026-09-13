package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.crafting.CraftContext
import dev.willram.ramrpg.api.crafting.CraftPlanner
import dev.willram.ramrpg.api.crafting.CraftResult
import dev.willram.ramrpg.api.crafting.Ingredient
import dev.willram.ramrpg.api.crafting.IngredientCandidate
import dev.willram.ramrpg.api.crafting.OutcomePlan
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeCost
import dev.willram.ramrpg.api.crafting.RecipeKey
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.StationKey
import dev.willram.ramrpg.api.crafting.plan
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.RarityRules
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.builtin.reforges.BuiltinReforges
import dev.willram.ramrpg.core.config.specs.RecipeSpec
import dev.willram.ramrpg.core.crafting.ReforgeOutcomes
import dev.willram.ramrpg.core.services.ReforgeRegistryImpl
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * WP-3.3c: reforging is a normal crafting recipe. Every test parses the SHIPPED
 * `content/recipes/reforge.conf` straight through the production [RecipeSpec] deserializer (recipes are
 * not yet a `RpgContentLoader.KNOWN_TYPES` directory, so they are parsed directly, like
 * `MaterialTierChainTest`) and proves each entry is a well-formed `Reforge` outcome over a reforge that
 * really exists, that the generic planner applies the right [ReforgeKey], and that the pure cost/selection
 * helpers in [ReforgeOutcomes] behave. Pure: the only Bukkit surface is the [Material] enum.
 */
class ReforgeAsRecipeTest {

    private val reforges: ReforgeRegistry = ReforgeRegistryImpl().also { BuiltinReforges.registerAll(it) }

    private val recipes: List<RecipeSpec> =
        childrenOf("content/recipes/reforge.conf").map { RecipeSpec.deserialize(it) }

    private val emptyState = object : ItemRequirementState {
        override fun skillLevel(skill: SkillKey): Int = 0
        override fun statValue(stat: StatKey): Double = 0.0
    }

    private fun reforgeKeyOf(spec: RecipeSpec): ReforgeKey {
        val outcome = spec.outcome
        assertTrue(outcome is RecipeOutcome.Reforge, "recipe ${spec.key} must be a reforge outcome")
        return (outcome as RecipeOutcome.Reforge).reforge
    }

    // -- The shipped reforge pool ------------------------------------------------------------------

    @Test
    fun `every shipped reforge recipe is a reforge outcome over a registered reforge`() {
        assertTrue(recipes.isNotEmpty(), "reforge.conf must define reforge recipes")
        for (spec in recipes) {
            assertEquals(StationKey.of("ramrpg", "smithing"), spec.station, "${spec.key} must bind to the smithing station")
            val rk = reforgeKeyOf(spec)
            assertNotNull(reforges.get(rk), "${spec.key} reforges to $rk, which no ReforgeDefinition registers")
        }
    }

    @Test
    fun `the shipped recipes cover exactly the builtin reforge pool`() {
        val recipeReforges = recipes.map { reforgeKeyOf(it) }.toSet()
        val builtinReforges = reforges.all().map { it.key }.toSet()
        assertEquals(builtinReforges, recipeReforges, "one reforge recipe per builtin reforge (the pool is data)")
    }

    @Test
    fun `each reforge recipe carries the documented baseline cost`() {
        for (spec in recipes) {
            assertEquals(RecipeCost(money = 250.0, experienceLevels = 10), spec.cost, "${spec.key} baseline cost")
        }
    }

    // -- Applying the outcome writes the right ReforgeKey ------------------------------------------

    @Test
    fun `the reforge outcome plan stamps the recipe's reforge key onto the target`() {
        for (spec in recipes) {
            val rk = reforgeKeyOf(spec)
            val target = ItemInstanceData(identity = ItemIdentity(ItemKey.of("ramrpg", "blade")))
            val plan = spec.outcome.plan(input = target, quality = 0.0)
            assertEquals(rk, (plan as OutcomePlan.Modify).result.reforge, "${spec.key} must stamp $rk")
        }
    }

    @Test
    fun `reforging replaces any existing reforge with the recipe's key`() {
        val already = ItemInstanceData(
            identity = ItemIdentity(ItemKey.of("ramrpg", "blade")),
            reforge = ReforgeKey(ContentId.of("ramrpg", "sharp")),
        )
        val newKey = ReforgeKey(ContentId.of("ramrpg", "fierce"))
        val plan = RecipeOutcome.Reforge(newKey).plan(input = already, quality = 0.0)
        assertEquals(newKey, (plan as OutcomePlan.Modify).result.reforge)
    }

    @Test
    fun `the generic planner produces the reforge modify plan and passes cost + consumption through`() {
        val key = ReforgeKey(ContentId.of("ramrpg", "sharp"))
        val recipe = Recipe(
            key = RecipeKey.of("ramrpg", "reforge_sharp"),
            station = StationKey.of("ramrpg", "smithing"),
            inputs = listOf(Ingredient.MaterialTag(setOf(Material.AMETHYST_SHARD), count = 1)),
            cost = RecipeCost(money = 250.0, experienceLevels = 10),
            outcome = RecipeOutcome.Reforge(key),
        )
        val ctx = CraftContext(
            requirementState = emptyState,
            inputs = listOf(IngredientCandidate(Material.AMETHYST_SHARD, count = 1)),
            target = ItemInstanceData(identity = ItemIdentity(ItemKey.of("ramrpg", "blade"))),
            seed = 42L,
        )
        val result = CraftPlanner.plan(recipe, ctx) as CraftResult.Success
        assertEquals(key, (result.outcome as OutcomePlan.Modify).result.reforge)
        assertEquals(RecipeCost(money = 250.0, experienceLevels = 10), result.cost)
        assertTrue(
            result.consumption.any { it.material == Material.AMETHYST_SHARD && it.count == 1 },
            "the reagent catalyst must be recorded for consumption",
        )
    }

    // -- ReforgeOutcomes: cost by rarity ----------------------------------------------------------

    @Test
    fun `reforge cost by rarity reuses the RarityRules xp ladder and derives money from it`() {
        for (rarity in Rarity.entries) {
            val cost = ReforgeOutcomes.reforgeCost(rarity)
            assertEquals(RarityRules.reforgeXpCost(rarity), cost.experienceLevels, "$rarity xp levels")
            assertEquals(RarityRules.reforgeXpCost(rarity) * ReforgeOutcomes.MONEY_PER_XP_LEVEL, cost.money, "$rarity money")
        }
        // Monotonic in rarity, inherited from the RarityRules ladder.
        val costs = Rarity.entries.map { ReforgeOutcomes.reforgeCost(it).experienceLevels }
        for (i in 1 until costs.size) assertTrue(costs[i] >= costs[i - 1], "reforge cost must not regress: $costs")
    }

    // -- ReforgeOutcomes: seeded selection from the pool ------------------------------------------

    @Test
    fun `the pool is every registered reforge ordered deterministically by id`() {
        val pool = ReforgeOutcomes.pool(reforges)
        assertEquals(reforges.all().map { it.key }.sortedBy { it.id.toString() }, pool)
        assertEquals(reforges.all().size, pool.size)
    }

    @Test
    fun `seeded selection is deterministic, always in the pool, and not constant`() {
        val pool = ReforgeOutcomes.pool(reforges)
        val picked = HashSet<ReforgeKey>()
        for (seed in 0L until 200L) {
            val a = ReforgeOutcomes.selectReforge(pool, seed)
            val b = ReforgeOutcomes.selectReforge(pool, seed)
            assertEquals(a, b, "same (pool, seed) must select the same reforge")
            assertNotNull(a)
            assertTrue(a != null && a in pool, "selection $a must come from the pool")
            picked += a!!
        }
        assertTrue(picked.size > 1, "over many seeds the roll must reach more than one reforge, was $picked")
    }

    @Test
    fun `selection order does not depend on caller ordering of the pool`() {
        val pool = ReforgeOutcomes.pool(reforges)
        val shuffled = pool.reversed()
        for (seed in 0L until 50L) {
            assertEquals(ReforgeOutcomes.selectReforge(pool, seed), ReforgeOutcomes.selectReforge(shuffled, seed))
        }
    }

    @Test
    fun `an empty pool selects nothing`() {
        assertNull(ReforgeOutcomes.selectReforge(emptyList(), 123L))
        assertNull(ReforgeOutcomes.rolledOutcome(emptyList(), 123L))
    }

    @Test
    fun `rolledOutcome wraps the selected key and outcomeFor mirrors the grammar`() {
        val pool = ReforgeOutcomes.pool(reforges)
        val selected = ReforgeOutcomes.selectReforge(pool, 7L)!!
        assertEquals(RecipeOutcome.Reforge(selected), ReforgeOutcomes.rolledOutcome(pool, 7L))
        val fixed = ReforgeKey(ContentId.of("ramrpg", "wise"))
        assertEquals(RecipeOutcome.Reforge(fixed), ReforgeOutcomes.outcomeFor(fixed))
    }

    private companion object {
        fun childrenOf(resource: String): List<ConfigurationNode> {
            val stream = ReforgeAsRecipeTest::class.java.classLoader.getResourceAsStream(resource)
                ?: error("shipped resource not on the classpath: $resource")
            val root = stream.use {
                HoconConfigurationLoader.builder()
                    .source { BufferedReader(InputStreamReader(it, Charsets.UTF_8)) }
                    .build()
                    .load()
            }
            return root.childrenMap().values.toList()
        }
    }
}
