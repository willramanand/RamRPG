/**
 * WP-1.5a: registers the pure specs from an [RpgContentLoadResult] into the live RPG registries.
 *
 * This is the ONE place in the content pipeline that touches Bukkit: an [ItemSpec] holds its material
 * as a String (to keep the spec off-server-testable), so the String -> [Material] resolution -- and the
 * "unknown material" error it can raise -- happens here, on the server. Every other spec builds a pure
 * domain object via its own `toX()` and is registered directly.
 *
 * Registration is NOT thread-safe against the Bukkit main thread on its own; callers load off-thread
 * and then invoke [registerAll] on the right context (see [dev.willram.ramrpg.core.modules.ContentModule]).
 *
 * Errors AGGREGATE: [registerAll] never throws on a bad material -- it collects a [ValidationError] and
 * moves on, so one typo does not sink the rest of a content pack. (Load-time errors are already carried
 * by the [RpgContentLoadResult]; these are the registration-time ones the loader cannot detect without
 * Bukkit.)
 */
package dev.willram.ramrpg.core.config

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.exception.ValidationError
import dev.willram.ramcore.menu.Menus
import dev.willram.ramrpg.api.crafting.Ingredient
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.RecipeRegistry
import dev.willram.ramrpg.api.crafting.Station
import dev.willram.ramrpg.api.crafting.StationRegistry
import dev.willram.ramrpg.api.effects.BlockMatchers
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.items.EquipSlotDefaults
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.config.specs.ItemSpec
import dev.willram.ramrpg.core.config.specs.RecipeIngredientKind
import dev.willram.ramrpg.core.config.specs.RecipeIngredientSpec
import dev.willram.ramrpg.core.config.specs.RecipeSpec
import dev.willram.ramrpg.core.config.specs.StationSpec
import org.bukkit.Material

class ContentRegistrarRpg(
    private val stats: StatService,
    private val items: ItemDefinitionRegistry,
    private val skills: SkillRegistry,
    private val enchants: EnchantmentRegistry,
    private val entities: EntityProfileRegistry,
    private val reforges: ReforgeRegistry,
    private val gems: GemRegistry,
    private val recipes: RecipeRegistry,
    private val stations: StationRegistry,
) {

    /**
     * Registers every loaded spec under [owner], returning any registration-time errors -- unknown
     * materials (items + station blocks + recipe material ingredients) and unresolvable content
     * references (a recipe's station / output item / reforge / gem). Every error AGGREGATES and carries
     * its source (file + path); [registerAll] NEVER throws. Note [StatService.registerDefinition] takes no
     * owner, so HOCON stat definitions cannot be scoped/unregistered by owner -- matching the existing
     * StatService contract.
     *
     * ORDER MATTERS: stations register before recipes (a recipe validates that its station exists), and
     * items before recipes (a `new_item`/`transmute` recipe validates that its output item exists).
     */
    fun registerAll(result: RpgContentLoadResult, owner: String): List<ValidationError> {
        val errors = ArrayList<ValidationError>()

        result.stats.forEach { stats.registerDefinition(it.toDefinition()) }
        result.skills.forEach { skills.register(owner, it.toDefinition()) }
        result.enchants.forEach { enchants.register(owner, it.toEnchantment()) }
        result.entities.forEach { entities.register(owner, it.toProfile()) }
        result.reforges.forEach { reforges.register(owner, it.toDefinition()) }
        result.gems.forEach { gems.register(owner, it.toDefinition()) }

        result.items.forEach { spec ->
            val material = runCatching { Material.valueOf(spec.material) }.getOrNull()
            if (material == null) {
                errors += error(result, spec.id, "unknown material '${spec.material}'")
            } else {
                items.register(owner, buildItem(spec, material))
            }
        }

        // WP-3.1d: stations FIRST (recipes reference a station), then recipes. Both resolve their single
        // Bukkit dependency (raw material names) here on the server, exactly like [buildItem] does for an
        // item's `material`, aggregating an "unknown material" error rather than throwing.
        result.stations.forEach { spec -> registerStation(spec, owner, result, errors) }
        result.recipes.forEach { spec -> registerRecipe(spec, owner, result, errors) }

        return errors
    }

    /**
     * Resolves a [StationSpec] into a live [Station] and registers it. `blocks` -> [BlockMatchers.ofMaterials]
     * (unknown material aggregated, like [buildItem]); `rows` + `name` -> a RamCore [dev.willram.ramcore.menu.MenuView]
     * via [Menus.menu]. Any build failure (e.g. an out-of-range `rows`) aggregates as a source-tagged error.
     */
    private fun registerStation(
        spec: StationSpec,
        owner: String,
        result: RpgContentLoadResult,
        errors: MutableList<ValidationError>,
    ) {
        val (materials, unknown) = resolveMaterials(spec.blockMaterials)
        if (unknown.isNotEmpty()) {
            errors += error(result, spec.id, "unknown block material(s) ${unknown.joinToString(", ")}")
            return
        }
        val station = runCatching {
            Station(
                key = spec.key,
                displayName = spec.displayName,
                blockMatcher = BlockMatchers.ofMaterials(*materials.toTypedArray()),
                // rows + name -> a RamCore MenuView (rule 1: reuse Menus, never hand-roll a menu). The
                // station GUI itself is rebuilt live by StationMenu from this view's row count + title.
                menuLayout = Menus.menu(spec.displayName, spec.rows).build(),
                permittedKinds = spec.permittedKinds,
            )
        }.getOrElse { ex ->
            errors += error(result, spec.id, "invalid station: ${ex.message}")
            return
        }
        runCatching { stations.register(owner, station) }
            .onFailure { errors += error(result, spec.id, "could not register station: ${it.message}") }
    }

    /**
     * Resolves a [RecipeSpec] into a live [Recipe] and registers it. Resolves each ingredient (material
     * names -> `Set<Material>`, unknowns aggregated) and validates every referenced id resolves against a
     * LIVE registry: the station exists, a `new_item`/`transmute` output [ItemDefinition] exists, a
     * `reforge` key is registered, an `insert_gem` gem is registered. Any failure aggregates a
     * source-tagged error and the recipe is skipped rather than registered half-resolved.
     */
    private fun registerRecipe(
        spec: RecipeSpec,
        owner: String,
        result: RpgContentLoadResult,
        errors: MutableList<ValidationError>,
    ) {
        val before = errors.size

        val ingredients = spec.inputs.map { input -> resolveIngredient(input, spec.id, result, errors) }

        if (stations.get(spec.station) == null) {
            errors += error(result, spec.id, "recipe references unknown station '${spec.station}'")
        }
        when (val outcome = spec.outcome) {
            is RecipeOutcome.NewItem ->
                if (items.get(outcome.output) == null) {
                    errors += error(result, spec.id, "recipe outputs unknown item '${outcome.output}'")
                }
            is RecipeOutcome.Transmute ->
                if (items.get(outcome.target) == null) {
                    errors += error(result, spec.id, "recipe transmutes to unknown item '${outcome.target}'")
                }
            is RecipeOutcome.Reforge ->
                if (reforges.get(outcome.reforge) == null) {
                    errors += error(result, spec.id, "recipe references unknown reforge '${outcome.reforge}'")
                }
            is RecipeOutcome.InsertGem ->
                if (gems.get(outcome.gem) == null) {
                    errors += error(result, spec.id, "recipe references unknown gem '${outcome.gem}'")
                }
            else -> Unit
        }

        // Any ingredient or reference error means the recipe is not safe to register -- skip it (errors
        // already aggregated above), never register a half-resolved recipe.
        if (errors.size != before) return

        val recipe = Recipe(
            key = spec.key,
            station = spec.station,
            inputs = ingredients.filterNotNull(),
            requirements = spec.requirements,
            cost = spec.cost,
            outcome = spec.outcome,
        )
        runCatching { recipes.register(owner, recipe) }
            .onFailure { errors += error(result, spec.id, "could not register recipe: ${it.message}") }
    }

    /** Resolves one raw [RecipeIngredientSpec] into a live [Ingredient]; unknown materials aggregate. */
    private fun resolveIngredient(
        input: RecipeIngredientSpec,
        recipeId: ContentId,
        result: RpgContentLoadResult,
        errors: MutableList<ValidationError>,
    ): Ingredient? = when (input.kind) {
        RecipeIngredientKind.ITEM -> Ingredient.Item(input.itemKey!!, input.count)
        RecipeIngredientKind.CATEGORY -> Ingredient.Category(input.category!!, input.count)
        RecipeIngredientKind.PREDICATE -> Ingredient.Predicate(
            itemKey = input.itemKey,
            category = input.category,
            minQuality = input.minQuality,
            minUpgradeLevel = input.minUpgradeLevel,
            count = input.count,
        )
        RecipeIngredientKind.MATERIAL -> {
            val (materials, unknown) = resolveMaterials(input.materials)
            if (unknown.isNotEmpty()) {
                errors += error(result, recipeId, "recipe ingredient has unknown material(s) ${unknown.joinToString(", ")}")
                null
            } else {
                Ingredient.MaterialTag(materials.toSet(), input.count)
            }
        }
    }

    /** Splits raw material names into resolved [Material]s and the names that did not resolve. */
    private fun resolveMaterials(names: Collection<String>): Pair<List<Material>, List<String>> {
        val resolved = ArrayList<Material>(names.size)
        val unknown = ArrayList<String>()
        for (name in names) {
            val material = runCatching { Material.valueOf(name) }.getOrNull()
            if (material == null) unknown += name else resolved += material
        }
        return resolved to unknown
    }

    /**
     * A source-tagged registration-time [ValidationError] for [id]. Cites the file + path the loader kept
     * for the definition, falling back to the id when the source is somehow unknown -- keeping the "every
     * error carries file and path" promise the same way the item-material seam does.
     */
    private fun error(result: RpgContentLoadResult, id: ContentId, message: String): ValidationError {
        val src = result.sourceOf(id)
        return if (src != null) {
            ValidationError.at(src.file(), src.path().ifEmpty { id.toString() }, message)
        } else {
            ValidationError.at(id.toString(), "", message)
        }
    }

    companion object {
        /**
         * WP-1.5d: turns a pure [ItemSpec] into an [ItemDefinition] once its [Material] has resolved.
         * Public (not the instance-scoped `registerAll`'s private helper it used to be) so
         * [dev.willram.ramrpg.builtin.items.BuiltinItems] -- which loads the SAME packaged
         * `content/items/builtin.conf` resource through [RpgContentLoader], but has no need for the
         * other six registries a full [ContentRegistrarRpg] instance requires -- can build its
         * [ItemDefinition]s through this exact mapping instead of forking a second one. This is the
         * seam WP-1.5b/2.1a left open (see their design notes): [spec]'s `effects`, `itemLevel`,
         * `requirements` and `equipSlots` are now copied onto the built definition, closing the gap
         * that made HOCON/Kotlin parity impossible before this WP. WP-2.3b adds `damageSplit` to that
         * same copy-through list.
         */
        fun buildItem(spec: ItemSpec, material: Material): ItemDefinition {
            val baseStats = spec.baseStats.map { (statKey, amount) ->
                StatModifier(statKey, amount, ModifierOperation.ADD, ModifierSource(SourceType.ITEM, spec.id))
            }
            return ItemDefinition(
                key = spec.key,
                displayName = spec.displayName,
                material = material,
                rarity = spec.rarity,
                categories = spec.categories,
                baseStats = baseStats,
                effects = spec.effects,
                // loreTemplate = DEFAULT is the constructor default -- content has no way to override it (yet).
                maxStack = spec.maxStack,
                customModelData = spec.customModelData,
                allowVanillaWrapper = spec.allowVanillaWrapper,
                description = spec.description,
                statRolls = spec.statRolls,
                itemLevel = spec.itemLevel,
                requirements = spec.requirements,
                // null (no `equip-slots` override) -> fall back to the SAME category default
                // ItemDefinition's own constructor default would compute, so an omitted override and an
                // explicit one that happens to match the default are indistinguishable on the built definition.
                equipSlots = spec.equipSlots ?: EquipSlotDefaults.forCategories(spec.categories),
                // WP-2.3b: already validated (sum to 1.0, or empty) by ItemSpec.deserialize -- copied through
                // unchanged, same as every other spec-side-validated field above.
                damageSplit = spec.damageSplit,
            )
        }
    }
}
