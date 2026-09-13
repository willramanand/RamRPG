/**
 * WP-3.1a: PURE spec for a `recipes/` content entry. Bukkit-free like every other spec.
 *
 * Most of a [dev.willram.ramrpg.api.crafting.Recipe] is already pure and is built HERE: the
 * [RecipeCost][dev.willram.ramrpg.api.crafting.RecipeCost], the [ItemRequirement] gates (reusing the
 * WP-2.1a type -- see [requirements]) and the [RecipeOutcome][dev.willram.ramrpg.api.crafting.RecipeOutcome]
 * (all its keys are ContentId-based, so no server is needed). The ONE thing that cannot be built purely
 * is the ingredient list, because [dev.willram.ramrpg.api.crafting.Ingredient.MaterialTag] carries
 * `org.bukkit.Material`: so [inputs] is held as raw [RecipeIngredientSpec]s (material names as Strings)
 * and WP-3.1b's registrar resolves them into live `Ingredient`s (raising "unknown material" there, the
 * same seam [ItemSpec] uses for its `material`). Hence there is no `toDefinition()`.
 *
 * No [EffectSpec.Registries] parameter: a recipe references content only by typed key (ItemKey /
 * EnchantmentKey / ReforgeKey / GemKey / SkillKey), resolved structurally, so nothing needs registry
 * lookup at parse time (unlike an item's/enchant's `effects` bundle).
 *
 * HOCON shape (namespaced ids MUST be quoted -- ':' is a HOCON separator):
 * ```
 * id = "ramrpg:sharpen_iron_sword"
 * station = "ramrpg:smithing"
 * inputs = [
 *   { type = item, item = "ramrpg:whetstone", count = 2 }
 *   { type = material, materials = ["FLINT", "COBBLESTONE"], count = 1 }
 *   { type = category, category = "SWORD", count = 1 }
 *   { type = predicate, item = "ramrpg:legendary_blade", category = "SWORD", min-quality = 0.5, min-upgrade-level = 1 }
 * ]
 * requirements = [ { type = skill_level, skill = "ramrpg:combat", level = 10 } ]   # WP-2.1a grammar
 * cost { money = 100.0, experience-levels = 3 }
 * outcome { type = upgrade_input, upgrade-levels = 1 }
 * ```
 *
 * Outcome forms (a closed grammar mirroring [EffectSpec.one]'s `type` dispatch):
 * ```
 * outcome { type = new_item, item = "ramrpg:steel_sword", quality-skill = "ramrpg:combat", count = 1 }
 * outcome { type = upgrade_input, upgrade-levels = 1 }
 * outcome { type = repair }                       # full; or: { type = repair, amount = 200 }
 * outcome { type = enchant, enchant = "ramrpg:sharpness", level = 3 }
 * outcome { type = reforge, reforge = "ramrpg:sharp" }
 * outcome { type = insert_gem, gem = "ramrpg:ruby", index = 0 }
 * outcome { type = add_socket, socket-type = "ramrpg:generic", count = 1 }
 * outcome { type = remove_gem, index = 0 }
 * outcome { type = transmute, item = "ramrpg:mystery_box" }
 * ```
 */
package dev.willram.ramrpg.core.config.specs

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.crafting.RecipeCost
import dev.willram.ramrpg.api.crafting.RecipeKey
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.StationKey
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemRequirement
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.core.config.RpgContentSpec
import dev.willram.ramrpg.core.config.SpecNodes
import org.spongepowered.configurate.ConfigurationNode

/** Which [dev.willram.ramrpg.api.crafting.Ingredient] variant a [RecipeIngredientSpec] resolves into. */
enum class RecipeIngredientKind { ITEM, MATERIAL, CATEGORY, PREDICATE }

/**
 * The RAW, pure form of one ingredient. WP-3.1b's registrar turns it into a live
 * [dev.willram.ramrpg.api.crafting.Ingredient] (resolving [materials] Strings -> `Set<Material>` for the
 * MATERIAL kind). Only the fields relevant to [kind] are populated.
 */
data class RecipeIngredientSpec(
    val kind: RecipeIngredientKind,
    val count: Int = 1,
    val itemKey: ItemKey? = null,
    /** Raw material names (MATERIAL kind); registrar resolves to `org.bukkit.Material`. */
    val materials: Set<String> = emptySet(),
    val category: ItemCategory? = null,
    val minQuality: Double? = null,
    val minUpgradeLevel: Int? = null,
)

data class RecipeSpec(
    val key: RecipeKey,
    val station: StationKey,
    /** Raw ingredients; registrar resolves to live `Ingredient`s (see [RecipeIngredientSpec]). */
    val inputs: List<RecipeIngredientSpec>,
    val requirements: List<ItemRequirement>,
    val cost: RecipeCost,
    val outcome: RecipeOutcome,
) : RpgContentSpec {
    override val id: ContentId get() = key.id

    companion object {
        fun deserialize(node: ConfigurationNode): RecipeSpec {
            val id = SpecNodes.requireId(node)
            return RecipeSpec(
                key = RecipeKey(id),
                station = StationKey(SpecNodes.requireIdAt(node, "station")),
                inputs = inputs(node),
                requirements = requirements(node),
                cost = cost(node),
                outcome = outcome(node),
            )
        }

        private fun inputs(node: ConfigurationNode): List<RecipeIngredientSpec> =
            node.node("inputs").childrenList().map { n ->
                val count = SpecNodes.intOr(n, "count", 1)
                when (val type = SpecNodes.requiredString(n, "type", "recipe ingredient type").lowercase()) {
                    "item" -> RecipeIngredientSpec(
                        kind = RecipeIngredientKind.ITEM,
                        count = count,
                        itemKey = ItemKey(SpecNodes.requireIdAt(n, "item")),
                    )
                    "material" -> {
                        val materials = SpecNodes.stringList(n, "materials").map { it.uppercase() }.toSet()
                        if (materials.isEmpty()) {
                            throw ContentDeserializeException("material ingredient requires a non-empty 'materials' list")
                        }
                        RecipeIngredientSpec(kind = RecipeIngredientKind.MATERIAL, count = count, materials = materials)
                    }
                    "category" -> RecipeIngredientSpec(
                        kind = RecipeIngredientKind.CATEGORY,
                        count = count,
                        category = SpecNodes.requiredEnum<ItemCategory>(n, "category", "item category"),
                    )
                    "predicate" -> RecipeIngredientSpec(
                        kind = RecipeIngredientKind.PREDICATE,
                        count = count,
                        itemKey = SpecNodes.optionalId(n, "item")?.let { ItemKey(it) },
                        category = SpecNodes.optionalString(n, "category")?.let {
                            SpecNodes.parseEnum<ItemCategory>(it, "item category")
                        },
                        minQuality = SpecNodes.doubleOrNull(n, "min-quality"),
                        minUpgradeLevel = SpecNodes.intOrNull(n, "min-upgrade-level"),
                    )
                    else -> throw ContentDeserializeException(
                        "unknown recipe ingredient type '$type'; expected item, material, category or predicate",
                    )
                }
            }

        /**
         * Parses `requirements = [...]` reusing the EXISTING WP-2.1a [ItemRequirement] sealed type (no new
         * requirement type is forked). The `type` grammar (`skill_level` / `stat_threshold` / `perk_owned`)
         * intentionally mirrors [ItemSpec]'s own requirement parser exactly -- that parser is a private
         * companion function on [ItemSpec] and this WP may not modify [ItemSpec] to share it, so the small
         * closed grammar is replicated here against the shared type. An unrecognised type throws
         * [ContentDeserializeException] naming it, which `SpecLoader` tags with the file + path.
         */
        private fun requirements(node: ConfigurationNode): List<ItemRequirement> =
            node.node("requirements").childrenList().map { req ->
                when (val type = SpecNodes.requiredString(req, "type", "item requirement type").lowercase()) {
                    "skill_level" -> ItemRequirement.SkillLevel(
                        skill = SkillKey(SpecNodes.requireIdAt(req, "skill")),
                        level = SpecNodes.intOr(req, "level", 1),
                    )
                    "stat_threshold" -> ItemRequirement.StatThreshold(
                        stat = StatKey(SpecNodes.requireIdAt(req, "stat")),
                        min = SpecNodes.doubleOr(req, "min", 0.0),
                    )
                    "perk_owned" -> ItemRequirement.PerkOwned(perk = SpecNodes.requireIdAt(req, "perk"))
                    else -> throw ContentDeserializeException(
                        "unknown item requirement type '$type'; expected skill_level, stat_threshold or perk_owned",
                    )
                }
            }

        private fun cost(node: ConfigurationNode): RecipeCost {
            val c = node.node("cost")
            if (c.virtual()) return RecipeCost.FREE
            return RecipeCost(
                money = SpecNodes.doubleOr(c, "money", 0.0),
                experienceLevels = SpecNodes.intOr(c, "experience-levels", 0),
            )
        }

        private fun outcome(node: ConfigurationNode): RecipeOutcome {
            val o = node.node("outcome")
            if (o.virtual()) throw ContentDeserializeException("recipe is missing 'outcome'")
            return when (val type = SpecNodes.requiredString(o, "type", "recipe outcome type").lowercase()) {
                "new_item" -> RecipeOutcome.NewItem(
                    output = ItemKey(SpecNodes.requireIdAt(o, "item")),
                    qualitySkill = SpecNodes.optionalId(o, "quality-skill")?.let { SkillKey(it) },
                    count = SpecNodes.intOr(o, "count", 1),
                )
                "upgrade_input" -> RecipeOutcome.UpgradeInput(upgradeLevels = SpecNodes.intOr(o, "upgrade-levels", 1))
                "repair" -> RecipeOutcome.Repair(amount = SpecNodes.intOrNull(o, "amount"))
                "enchant" -> RecipeOutcome.Enchant(
                    enchant = EnchantmentKey(SpecNodes.requireIdAt(o, "enchant")),
                    level = SpecNodes.intOr(o, "level", 1),
                )
                "reforge" -> RecipeOutcome.Reforge(reforge = ReforgeKey(SpecNodes.requireIdAt(o, "reforge")))
                "insert_gem" -> RecipeOutcome.InsertGem(
                    gem = GemKey(SpecNodes.requireIdAt(o, "gem")),
                    socketIndex = SpecNodes.intOr(o, "index", 0),
                )
                "add_socket" -> RecipeOutcome.AddSocket(
                    socketType = SpecNodes.requireIdAt(o, "socket-type"),
                    count = SpecNodes.intOr(o, "count", 1),
                )
                "remove_gem" -> RecipeOutcome.RemoveGem(socketIndex = SpecNodes.intOr(o, "index", 0))
                "transmute" -> RecipeOutcome.Transmute(target = ItemKey(SpecNodes.requireIdAt(o, "item")))
                else -> throw ContentDeserializeException(
                    "unknown recipe outcome type '$type'; expected new_item, upgrade_input, repair, enchant, " +
                        "reforge, insert_gem, add_socket, remove_gem or transmute",
                )
            }
        }
    }
}
