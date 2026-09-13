package dev.willram.ramrpg

import dev.willram.ramrpg.api.crafting.Ingredient
import dev.willram.ramrpg.api.crafting.IngredientCandidate
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.core.config.RpgContentLoader
import dev.willram.ramrpg.core.config.specs.ItemSpec
import dev.willram.ramrpg.core.config.specs.RecipeIngredientKind
import dev.willram.ramrpg.core.config.specs.RecipeSpec
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * WP-3.2a: asserts the SHIPPED material tier ladder is a well-formed, item-id-gated refine chain.
 *
 * Every test loads the real packaged files -- `content/items/materials.conf` and
 * `content/recipes/materials.conf` -- straight off the classpath and parses each entry through the
 * production [ItemSpec] / [RecipeSpec] deserializers (recipes are not yet a
 * [RpgContentLoader.KNOWN_TYPES] directory, so they are parsed with [RecipeSpec] directly, not the full
 * loader). Nothing here builds a hand-typed fixture to assert against itself. Pure: the only Bukkit
 * surface touched is the [Material] enum (resolving catalyst names and building candidate stacks); no
 * live server.
 *
 * The crux this guards: each tier N -> N+1 step is gated on the specific lower-tier ramrpg: item id (an
 * [Ingredient.Item]), never on a raw vanilla [Material], so a plain vanilla ingot can never satisfy a
 * refine step. `ItemSpec.tags` is loaded and sanity-checked as a tier band, but -- being a
 * parsed-but-not-yet-matched seam -- is deliberately NOT what any recipe matches on.
 */
class MaterialTierChainTest {

    /** slug -> ItemSpec for every shipped tier item, parsed through the real deserializer. */
    private val items: Map<ItemKey, ItemSpec> =
        childrenOf("content/items/materials.conf")
            .map { ItemSpec.deserialize(it) }
            .associateBy { it.key }

    /** every shipped refine recipe, parsed through the real deserializer. */
    private val recipes: List<RecipeSpec> =
        childrenOf("content/recipes/materials.conf").map { RecipeSpec.deserialize(it) }

    private fun tierOf(spec: ItemSpec): Int {
        val tierTag = spec.tags.singleOrNull { it.startsWith("tier-") }
            ?: error("item ${spec.key} must carry exactly one tier-N tag, had ${spec.tags}")
        return tierTag.removePrefix("tier-").toIntOrNull()
            ?: error("item ${spec.key} has a malformed tier tag '$tierTag'")
    }

    private fun tierOf(key: ItemKey): Int =
        tierOf(items[key] ?: error("recipe references unknown item $key"))

    private fun outputKey(recipe: RecipeSpec): ItemKey {
        val outcome = recipe.outcome
        assertTrue(outcome is RecipeOutcome.NewItem, "refine ${recipe.key} must produce a new_item")
        return (outcome as RecipeOutcome.NewItem).output
    }

    @Test
    fun `every shipped material item declares exactly one tier band`() {
        assertTrue(items.isNotEmpty(), "materials.conf must define items")
        for (spec in items.values) {
            val tier = tierOf(spec) // throws if zero or many tier-N tags
            assertTrue(tier in 1..4, "item ${spec.key} tier $tier out of the documented 1..4 range")
            // Every item must name a real Material -- catches an invented material name at parse-adjacent time.
            assertNotNull(Material.valueOf(spec.material), "item ${spec.key} names an unknown Material")
        }
    }

    @Test
    fun `every refine gates its tiered inputs on a real item exactly one tier below its output`() {
        assertTrue(recipes.isNotEmpty(), "materials.conf recipes must define refine recipes")
        for (recipe in recipes) {
            val output = outputKey(recipe)
            assertTrue(output in items, "refine ${recipe.key} outputs $output, absent from materials.conf")
            val outTier = tierOf(output)

            val tieredInputs = recipe.inputs.filter { it.kind == RecipeIngredientKind.ITEM }
            assertTrue(
                tieredInputs.isNotEmpty(),
                "refine ${recipe.key} has no item-gated tiered input -- nothing stops a vanilla stack completing it",
            )
            for (input in tieredInputs) {
                val inKey = input.itemKey ?: error("ITEM ingredient in ${recipe.key} carries no itemKey")
                assertTrue(inKey in items, "refine ${recipe.key} consumes $inKey, absent from materials.conf")
                assertEquals(
                    outTier - 1, tierOf(inKey),
                    "refine ${recipe.key}: tiered input $inKey (tier ${tierOf(inKey)}) is not exactly one " +
                        "tier below output $output (tier $outTier) -- a gap or a cycle",
                )
            }
        }
    }

    @Test
    fun `every tier above the first is produced by at least one refine recipe (no gaps)`() {
        val maxTier = items.values.maxOf { tierOf(it) }
        val producedTiers = recipes.map { tierOf(outputKey(it)) }.toSet()
        for (tier in 2..maxTier) {
            assertTrue(tier in producedTiers, "no refine recipe produces a tier-$tier item -- the ladder has a gap")
        }
    }

    @Test
    fun `a vanilla material cannot satisfy a tier-to-tier refine input`() {
        // For every tiered input across the whole ladder, resolve it to the live Ingredient.Item it becomes
        // and prove a plain vanilla stack of the SAME base Material (no RPG identity) is rejected, while the
        // actual RPG item is accepted. This is exactly why a vanilla Diamond/Netherite Scrap/ingot cannot
        // complete a refine step.
        var proven = 0
        for (recipe in recipes) {
            for (input in recipe.inputs.filter { it.kind == RecipeIngredientKind.ITEM }) {
                val inKey = input.itemKey!!
                val baseMaterial = Material.valueOf(items.getValue(inKey).material)
                val ingredient = Ingredient.Item(inKey, count = input.count)

                val vanilla = IngredientCandidate(baseMaterial, count = input.count, itemKey = null)
                assertFalse(
                    ingredient.matches(vanilla),
                    "vanilla $baseMaterial (no RPG identity) must NOT satisfy ${recipe.key}'s $inKey gate",
                )

                val rpgItem = IngredientCandidate(baseMaterial, count = input.count, itemKey = inKey)
                assertTrue(
                    ingredient.matches(rpgItem),
                    "the real RPG item $inKey must satisfy ${recipe.key}'s gate",
                )
                proven++
            }
        }
        assertTrue(proven > 0, "expected at least one item-gated tiered input to prove the gating property")
    }

    @Test
    fun `catalyst inputs name real obtainable vanilla materials and are not the tier gate`() {
        for (recipe in recipes) {
            val catalysts = recipe.inputs.filter { it.kind == RecipeIngredientKind.MATERIAL }
            for (catalyst in catalysts) {
                assertTrue(catalyst.itemKey == null, "a material catalyst must carry no itemKey (${recipe.key})")
                assertTrue(catalyst.materials.isNotEmpty(), "material catalyst in ${recipe.key} lists no materials")
                for (name in catalyst.materials) {
                    // Throws IllegalArgumentException on an invented name (e.g. the old DEEP_DARK_BLOCK).
                    assertNotNull(Material.valueOf(name), "catalyst material '$name' in ${recipe.key} is not a real Material")
                }
            }
        }
    }

    @Test
    fun `pre-existing builtin items still parse with tags defaulting to empty`() {
        // The 65 shipped builtin items carry no `tags`; loading them through the real packaged pipeline
        // proves the new optional field is backward-compatible (default emptySet), not a schema bump.
        val result = RpgContentLoader.loadPackaged()
        assertTrue(result.errors().isEmpty(), "no load errors: ${result.errors()}")
        assertEquals(65, result.items.size, "builtin.conf must still parse all 65 items")
        assertTrue(result.items.all { it.tags.isEmpty() }, "builtin items must default tags to empty")
    }

    private companion object {
        fun childrenOf(resource: String): List<ConfigurationNode> {
            val stream = MaterialTierChainTest::class.java.classLoader.getResourceAsStream(resource)
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
