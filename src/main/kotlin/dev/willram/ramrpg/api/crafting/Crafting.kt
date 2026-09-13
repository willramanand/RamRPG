/**
 * WP-3.1a: the crafting surface. ONE mechanism, THREE stations. A [Station] is a place you open (a
 * vanilla block opens a RamCore menu); a [Recipe] turns [inputs][Ingredient] + a [cost] into a
 * [RecipeOutcome]. Smithing (upgrade/repair/reforge/socket), enchanting, and generic crafting are all
 * just recipes whose [RecipeOutcome] differs -- there is deliberately no bespoke per-station code path.
 * See `docs/design/3.1a-crafting-model.md`.
 *
 * PURITY. Everything except [CraftingService] is pure and off-server-testable: the matchable surface
 * ([Ingredient.matches] against a pure [IngredientCandidate]), the deterministic [QualityRoll], the
 * per-outcome [plan] and the whole [CraftResult]/[CraftPlanner] planning result -- none of those touch a
 * live server. The direct Bukkit types on the pure surface are just [org.bukkit.Material] (needed to
 * match a raw vanilla stack in an [Ingredient.MaterialTag], and to name a vanilla removal in a
 * [ConsumedInput]). [Station] is a LIVE definition (not part of the pure planning surface): like
 * [dev.willram.ramrpg.api.items.ItemDefinition] holding a `Material`, it acceptably holds Bukkit-backed
 * collaborators -- a [BlockMatcher] (whose `matches` transitively takes an `org.bukkit.block.Block`) and
 * a RamCore [MenuView]. [CraftingService] alone references [org.bukkit.entity.Player] /
 * [org.bukkit.inventory.ItemStack] / RamCore's [MenuSession]. The RESULT and PLAN types 3.1b unit-tests
 * assert never reference a [Player], [ItemStack], [MenuView] or [MenuSession].
 *
 * RAMCORE FIRST. Menu layout is RamCore's [dev.willram.ramcore.menu.MenuView]; the opened session is
 * RamCore's [dev.willram.ramcore.menu.MenuSession] (never a RamRPG menu type). RamCore ships NO
 * block-matcher, so [Station] reuses RamRPG's own existing [dev.willram.ramrpg.api.effects.BlockMatcher]
 * (a pre-existing RamRPG concept, NOT a new fork) rather than inventing a parallel one -- see the report.
 * RamCore's economy is a per-UUID `double` balance (`dev.willram.ramcore.economy.Economy`) with no
 * reusable "cost" value object, so [RecipeCost] is the minimal local data type the WP allows.
 *
 * IMPLEMENTATIONS ARE WP-3.1b. `RecipeRegistryImpl`, `CraftingServiceImpl`, the station `MenuView`
 * builder and the `RecipeSpec`/`StationSpec` registrar all belong to WP-3.1b; this WP defines only the
 * interfaces + pure logic they build on. Reforge/Socket/Enchant OUTCOME RUNTIMES are deferred further
 * (Reforge -> WP-3.3c, Socket -> WP-3.3d, Enchant -> WP-3.4b), but their TYPES ship NOW so reforging and
 * socketing never keep a special code path.
 */
package dev.willram.ramrpg.api.crafting

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.menu.MenuSession
import dev.willram.ramcore.menu.MenuView
import dev.willram.ramrpg.api.effects.BlockMatcher
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemRequirement
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.items.SocketData
import dev.willram.ramrpg.api.items.isMet
import dev.willram.ramrpg.api.sockets.GemKey
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

// ---------------------------------------------------------------------------------------------------
// Keys
// ---------------------------------------------------------------------------------------------------

/** ContentId-based handle for a [Station] (mirrors [ItemKey]/[GemKey]/[SetKey][dev.willram.ramrpg.api.sets.SetKey]). */
@JvmInline value class StationKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = StationKey(ContentId.of(ns, v)) }
}

/** ContentId-based handle for a [Recipe]. */
@JvmInline value class RecipeKey(val id: ContentId) {
    override fun toString(): String = id.toString()
    companion object { fun of(ns: String, v: String) = RecipeKey(ContentId.of(ns, v)) }
}

// ---------------------------------------------------------------------------------------------------
// Station
// ---------------------------------------------------------------------------------------------------

/**
 * A crafting station: a vanilla block ([blockMatcher]) that, when interacted with, opens a RamCore menu
 * ([menuLayout]) restricted to recipes whose [RecipeOutcome.kind] is in [permittedKinds]. This is a LIVE
 * definition (like [dev.willram.ramrpg.api.items.ItemDefinition] holding a `Material`) -- its pure,
 * HOCON-loadable form is [dev.willram.ramrpg.core.config.specs.StationSpec], resolved on the server by
 * WP-3.1b's registrar (raw material names -> [BlockMatcher] via
 * [dev.willram.ramrpg.api.effects.BlockMatchers], `rows`+`name` -> [MenuView] via
 * `dev.willram.ramcore.menu.Menus`).
 *
 * @property blockMatcher which vanilla block opens this station. Reuses RamRPG's existing
 *   [dev.willram.ramrpg.api.effects.BlockMatcher] because RamCore ships none (see this file's header).
 * @property menuLayout the RamCore [MenuView] WP-3.1b opens for this station (input slots, preview slot
 *   and recipe-book page live inside it); [CraftingService.open] opens it into a [MenuSession].
 * @property permittedKinds the [RecipeOutcomeKind]s this station accepts -- a smithing station permits
 *   [RecipeOutcomeKind.UPGRADE_INPUT]/[RecipeOutcomeKind.REPAIR]/[RecipeOutcomeKind.REFORGE] and the
 *   socket kinds [RecipeOutcomeKind.INSERT_GEM]/[RecipeOutcomeKind.ADD_SOCKET]/[RecipeOutcomeKind.REMOVE_GEM];
 *   an enchanting station permits [RecipeOutcomeKind.ENCHANT]; etc.
 */
data class Station(
    val key: StationKey,
    val displayName: Component,
    val blockMatcher: BlockMatcher,
    val menuLayout: MenuView,
    val permittedKinds: Set<RecipeOutcomeKind>,
)

// ---------------------------------------------------------------------------------------------------
// Recipe
// ---------------------------------------------------------------------------------------------------

/**
 * A recipe consumed at a [Station]. Pure and HOCON-loadable (see
 * [dev.willram.ramrpg.core.config.specs.RecipeSpec]).
 *
 * @property station the [StationKey] this recipe belongs to (a key, not the live [Station], so a recipe
 *   stays pure and station resolution is a registry lookup -- see [RecipeRegistry.forStation]).
 * @property inputs the [Ingredient]s consumed. "Reagents" in the roadmap are just inputs, not [cost].
 * @property requirements gates reusing the EXISTING [ItemRequirement] sealed type from WP-2.1a
 *   (SkillLevel / StatThreshold / PerkOwned) -- no new requirement type is forked; evaluated with the
 *   same [isMet].
 * @property cost the economy cost (gold / XP levels) -- see [RecipeCost].
 * @property outcome what the craft produces -- see [RecipeOutcome].
 */
data class Recipe(
    val key: RecipeKey,
    val station: StationKey,
    val inputs: List<Ingredient>,
    val requirements: List<ItemRequirement> = emptyList(),
    val cost: RecipeCost = RecipeCost.FREE,
    val outcome: RecipeOutcome,
)

/**
 * The economy cost of a craft. Kept minimal because RamCore's economy models money as a bare `double`
 * balance (`dev.willram.ramcore.economy.Economy.withdraw(uuid, amount)`) with no reusable cost value
 * object to reuse. [money] is charged through that economy by WP-3.1b; [experienceLevels] covers the
 * vanilla XP-level cost enchanting uses (roadmap 3.4). Reagents are [Recipe.inputs], not a cost; skill
 * XP GRANTED on a successful craft is a reward (`XpSource`), also not modelled here.
 */
data class RecipeCost(
    val money: Double = 0.0,
    val experienceLevels: Int = 0,
) {
    companion object { val FREE = RecipeCost() }
}

// ---------------------------------------------------------------------------------------------------
// Ingredient
// ---------------------------------------------------------------------------------------------------

/**
 * A pure, server-free snapshot of one candidate stack an [Ingredient] is matched against. WP-3.1b builds
 * this from a live [ItemStack] (via `ItemInstanceService.identify` + `ItemDefinitionRegistry`), but the
 * matching itself never needs a server, so [Ingredient.matches] is unit-testable in isolation.
 *
 * @property material the stack's [Material] -- the only thing a raw vanilla item exposes.
 * @property count how many the candidate stack holds (an ingredient matches only when this is enough).
 * @property itemKey the RPG [ItemKey], or `null` for a plain vanilla item with no RPG identity.
 * @property categories the [ItemCategory]s of the candidate's [ItemDefinition][dev.willram.ramrpg.api.items.ItemDefinition]
 *   (empty for a plain vanilla item).
 * @property quality the instance quality in `[0,1]` (WP-2.1b), or `null` when unknown / not an RPG item.
 * @property upgradeLevel the instance upgrade level, or `null` when unknown / not an RPG item.
 */
data class IngredientCandidate(
    val material: Material,
    val count: Int = 1,
    val itemKey: ItemKey? = null,
    val categories: Set<ItemCategory> = emptySet(),
    val quality: Double? = null,
    val upgradeLevel: Int? = null,
)

/**
 * One required input of a [Recipe]. Every variant carries a [count] and a PURE [matches] that also
 * enforces that count. The MaterialTag variant is the one reason this file imports [Material]: matching a
 * raw vanilla stack has nothing but its [Material] to go on.
 */
sealed interface Ingredient {
    /** How many the candidate stack must supply for this ingredient to match. */
    val count: Int

    /** `true` iff [candidate] satisfies this ingredient's TYPE and supplies at least [count]. Pure. */
    fun matches(candidate: IngredientCandidate): Boolean

    /** A specific RPG item, by [ItemKey]. */
    data class Item(val itemKey: ItemKey, override val count: Int = 1) : Ingredient {
        override fun matches(candidate: IngredientCandidate): Boolean =
            candidate.count >= count && candidate.itemKey == itemKey
    }

    /**
     * Any stack whose [Material] is one of [materials]. A "material tag" (e.g. `minecraft:planks`) is
     * resolved from its raw HOCON name to this concrete [Material] set by WP-3.1b's registrar, keeping
     * match time pure -- see [dev.willram.ramrpg.core.config.specs.RecipeSpec].
     */
    data class MaterialTag(val materials: Set<Material>, override val count: Int = 1) : Ingredient {
        override fun matches(candidate: IngredientCandidate): Boolean =
            candidate.count >= count && candidate.material in materials
    }

    /** Any RPG item in an [ItemCategory] (e.g. "any SWORD"). */
    data class Category(val category: ItemCategory, override val count: Int = 1) : Ingredient {
        override fun matches(candidate: IngredientCandidate): Boolean =
            candidate.count >= count && category in candidate.categories
    }

    /**
     * A compound instance predicate -- the roadmap's "a sword with quality >= 0.5" case. Every non-null
     * constraint must hold; an unknown instance field ([IngredientCandidate.quality] /
     * [IngredientCandidate.upgradeLevel] being `null`) fails a constraint that needs it, fail-closed.
     * Structured (not a lambda) so it stays HOCON-loadable AND unit-testable.
     */
    data class Predicate(
        val itemKey: ItemKey? = null,
        val category: ItemCategory? = null,
        val minQuality: Double? = null,
        val minUpgradeLevel: Int? = null,
        override val count: Int = 1,
    ) : Ingredient {
        override fun matches(candidate: IngredientCandidate): Boolean {
            if (candidate.count < count) return false
            if (itemKey != null && candidate.itemKey != itemKey) return false
            if (category != null && category !in candidate.categories) return false
            if (minQuality != null && (candidate.quality == null || candidate.quality < minQuality)) return false
            if (minUpgradeLevel != null && (candidate.upgradeLevel == null || candidate.upgradeLevel < minUpgradeLevel)) return false
            return true
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// Outcomes
// ---------------------------------------------------------------------------------------------------

/**
 * The outcome kinds, so a [Station] can whitelist what it accepts ([Station.permittedKinds]). One kind
 * per [RecipeOutcome] arm; socketing is three distinct kinds ([INSERT_GEM]/[ADD_SOCKET]/[REMOVE_GEM]) so
 * a station can, e.g., allow inserting but not removing gems.
 */
enum class RecipeOutcomeKind {
    NEW_ITEM, UPGRADE_INPUT, REPAIR, ENCHANT, REFORGE, INSERT_GEM, ADD_SOCKET, REMOVE_GEM, TRANSMUTE,
}

/**
 * What a successful craft produces. ALL arms ship in this WP even though several runtimes land later
 * (Reforge -> WP-3.3c, the socket arms -> WP-3.3d, Enchant -> WP-3.4b): shipping the TYPES now is exactly
 * what stops reforging/socketing/enchanting from each keeping a bespoke code path. In particular the FULL
 * socket surface WP-3.3d needs is expressible today -- [AddSocket] (cut new empty slots), [InsertGem]
 * (fill a slot), [RemoveGem] (clear a slot) -- so 3.3d is a pure runtime add with no api change. The PURE
 * result of an outcome is computed by [plan]; WP-3.1b turns that plan into real [ItemStack] writes.
 */
sealed interface RecipeOutcome {
    val kind: RecipeOutcomeKind

    /**
     * Craft a fresh item [output]. Its quality comes from [QualityRoll] driven by [qualitySkill]'s level
     * (null -> the default [ItemInstanceData.DEFAULT_QUALITY]); that quality feeds WP-2.1b's
     * `min + quality*(max-min)` roll path via `ItemInstanceInit.quality`. [qualitySkill] is 3.1b ADAPTER
     * METADATA -- the pure [CraftPlanner]/[plan] ignore it and use the already-resolved
     * [CraftContext.skillLevel]; 3.1b reads [qualitySkill] to know which skill level to put there.
     */
    data class NewItem(
        val output: ItemKey,
        val qualitySkill: SkillKey? = null,
        val count: Int = 1,
    ) : RecipeOutcome { override val kind get() = RecipeOutcomeKind.NEW_ITEM }

    /** Raise the input item's `upgradeLevel` by [upgradeLevels] (smithing; runtime WP-3.3a/b). */
    data class UpgradeInput(val upgradeLevels: Int = 1) : RecipeOutcome {
        override val kind get() = RecipeOutcomeKind.UPGRADE_INPUT
    }

    /** Restore durability: `null` [amount] = repair to full (`maxDurability`); else add [amount], capped. */
    data class Repair(val amount: Int? = null) : RecipeOutcome {
        override val kind get() = RecipeOutcomeKind.REPAIR
    }

    /** Apply [enchant] at [level] to the input (runtime WP-3.4b). Reuses the existing [EnchantmentKey]. */
    data class Enchant(val enchant: EnchantmentKey, val level: Int = 1) : RecipeOutcome {
        override val kind get() = RecipeOutcomeKind.ENCHANT
    }

    /** Set the input's [reforge] (runtime WP-3.3c). Reuses the EXISTING [ReforgeKey] (Items.kt) -- no fork. */
    data class Reforge(val reforge: ReforgeKey) : RecipeOutcome {
        override val kind get() = RecipeOutcomeKind.REFORGE
    }

    /**
     * Insert [gem] into the input's EXISTING socket at [socketIndex] (runtime WP-3.3d). Reuses the
     * EXISTING [GemKey] (Sockets.kt) and writes into the EXISTING [SocketData] list -- no parallel key.
     * The socket must already exist (cut one first with [AddSocket]); an out-of-range index is an
     * [CraftFailure.INVALID_TARGET].
     */
    data class InsertGem(val gem: GemKey, val socketIndex: Int = 0) : RecipeOutcome {
        override val kind get() = RecipeOutcomeKind.INSERT_GEM
    }

    /**
     * Cut [count] new empty [SocketData] slot(s) onto the input (runtime WP-3.3d). [socketType] is the
     * socket's [SocketData.key] (a slot/type id -- not used for stats today, but carried); the appended
     * sockets start empty (`gem = null`), ready for a later [InsertGem].
     */
    data class AddSocket(val socketType: ContentId, val count: Int = 1) : RecipeOutcome {
        override val kind get() = RecipeOutcomeKind.ADD_SOCKET
    }

    /**
     * Clear the gem at the input's EXISTING socket at [socketIndex] (runtime WP-3.3d), leaving the slot
     * empty (`gem = null`) -- the counterpart to [InsertGem]. An out-of-range index is an
     * [CraftFailure.INVALID_TARGET].
     */
    data class RemoveGem(val socketIndex: Int = 0) : RecipeOutcome {
        override val kind get() = RecipeOutcomeKind.REMOVE_GEM
    }

    /** Convert the input into a fresh [target] item, preserving the input's quality (runtime later). */
    data class Transmute(val target: ItemKey) : RecipeOutcome {
        override val kind get() = RecipeOutcomeKind.TRANSMUTE
    }
}

/**
 * `true` for outcomes that transform an existing input instance ([RecipeOutcome.UpgradeInput],
 * [RecipeOutcome.Repair], [RecipeOutcome.Enchant], [RecipeOutcome.Reforge], and all three socket arms
 * [RecipeOutcome.InsertGem]/[RecipeOutcome.AddSocket]/[RecipeOutcome.RemoveGem]) -- so
 * [CraftPlanner]/WP-3.1b know a target item slot is mandatory. `false` for
 * [RecipeOutcome.NewItem]/[RecipeOutcome.Transmute], which produce a fresh stack.
 */
val RecipeOutcome.requiresTargetItem: Boolean
    get() = when (this) {
        is RecipeOutcome.NewItem, is RecipeOutcome.Transmute -> false
        else -> true
    }

/**
 * The PURE, server-free result of applying a [RecipeOutcome]. WP-3.1b turns this into real [ItemStack]
 * writes; a test asserts THIS, never a mutated stack.
 */
sealed interface OutcomePlan {
    /** A fresh item to create ([RecipeOutcome.NewItem] / [RecipeOutcome.Transmute]). */
    data class Create(val item: ItemKey, val quality: Double, val count: Int = 1) : OutcomePlan

    /** The input instance transformed in place (every target-consuming outcome). */
    data class Modify(val result: ItemInstanceData) : OutcomePlan
}

/**
 * Compute the pure [OutcomePlan] for this outcome. [input] is the target instance (required by every
 * [requiresTargetItem] outcome; `null` for NewItem, and optional for Transmute where it supplies the
 * quality to preserve). [quality] is the already-rolled [QualityRoll] value NewItem stamps on its output.
 *
 * Returns `null` iff the outcome needs a target it did not get, or references a socket index the target
 * does not have -- [CraftPlanner] maps those to [CraftFailure.NO_TARGET_ITEM] / [CraftFailure.INVALID_TARGET].
 */
fun RecipeOutcome.plan(input: ItemInstanceData?, quality: Double): OutcomePlan? = when (this) {
    is RecipeOutcome.NewItem -> OutcomePlan.Create(output, quality, count)
    is RecipeOutcome.Transmute -> OutcomePlan.Create(target, input?.quality ?: ItemInstanceData.DEFAULT_QUALITY, 1)
    is RecipeOutcome.UpgradeInput ->
        input?.let { OutcomePlan.Modify(it.copy(upgradeLevel = it.upgradeLevel + upgradeLevels)) }
    is RecipeOutcome.Repair ->
        input?.let {
            val restored = if (amount == null) it.maxDurability else (it.durability + amount).coerceAtMost(it.maxDurability)
            OutcomePlan.Modify(it.copy(durability = restored))
        }
    is RecipeOutcome.Enchant ->
        input?.let { OutcomePlan.Modify(it.copy(enchantments = it.enchantments + (enchant to level))) }
    is RecipeOutcome.Reforge ->
        input?.let { OutcomePlan.Modify(it.copy(reforge = reforge)) }
    is RecipeOutcome.InsertGem ->
        input?.takeIf { socketIndex in it.sockets.indices }?.let {
            val sockets = it.sockets.toMutableList()
            sockets[socketIndex] = sockets[socketIndex].copy(gem = gem.id)
            OutcomePlan.Modify(it.copy(sockets = sockets))
        }
    is RecipeOutcome.AddSocket ->
        input?.let {
            val added = List(count.coerceAtLeast(0)) { SocketData(socketType, gem = null) }
            OutcomePlan.Modify(it.copy(sockets = it.sockets + added))
        }
    is RecipeOutcome.RemoveGem ->
        input?.takeIf { socketIndex in it.sockets.indices }?.let {
            val sockets = it.sockets.toMutableList()
            sockets[socketIndex] = sockets[socketIndex].copy(gem = null)
            OutcomePlan.Modify(it.copy(sockets = sockets))
        }
}

// ---------------------------------------------------------------------------------------------------
// Quality roll
// ---------------------------------------------------------------------------------------------------

/**
 * The PURE, DETERMINISTIC craft-quality function. `QualityRoll(skill, seed)` returns a `Double` in
 * `[0,1]` that feeds WP-2.1b's `ItemInstanceInit.quality` (and thus its `min + quality*(max-min)` roll
 * path) -- a crafted item's quality is the same KIND of number a dropped item's seeded roll is.
 *
 * Formula (see `docs/design/3.1a-crafting-model.md` for the full derivation):
 * ```
 * frac    = clamp(skill / SKILL_CAP, 0, 1)
 * u       = splitmix64(seed) -> [0,1)          // deterministic uniform from the craft seed
 * quality = SKILL_WEIGHT*frac + (1-SKILL_WEIGHT)*u
 * ```
 * With `SKILL_WEIGHT = 0.5`, skill sets the FLOOR of a half-width window: at `skill = 0`,
 * `quality in [0, 0.5]`; at `skill = SKILL_CAP`, `quality in [0.5, 1.0]`. Higher skill therefore
 * strictly raises expected quality (and, for a fixed seed, raises the exact value), while randomness
 * keeps two crafts of the same item different.
 */
object QualityRoll {
    /** Skill value that reaches full skill weighting (skills cap at 100). */
    const val SKILL_CAP: Double = 100.0

    /** Share of quality driven by skill vs. randomness. `0.5` => skill sets the floor of a half window. */
    const val SKILL_WEIGHT: Double = 0.5

    /** Critical-craft chance at skill 0. */
    const val CRIT_BASE: Double = 0.05

    /** Additional critical-craft chance added linearly up to [SKILL_CAP] (max chance = base + this). */
    const val CRIT_PER_SKILL: Double = 0.20

    // Distinct salt so the crit roll is independent of the quality roll for the same (skill, seed).
    private const val CRIT_SALT: Long = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

    /** `QualityRoll(skill, seed)` -> quality in `[0,1]`. Deterministic and pure. */
    operator fun invoke(skill: Int, seed: Long): Double {
        val frac = (skill.toDouble() / SKILL_CAP).coerceIn(0.0, 1.0)
        val u = uniform(seed)
        return (SKILL_WEIGHT * frac + (1.0 - SKILL_WEIGHT) * u).coerceIn(0.0, 1.0)
    }

    /** Critical-craft chance in `[0,1]` for a skill level; linear from [CRIT_BASE] to base+[CRIT_PER_SKILL]. */
    fun criticalChance(skill: Int): Double {
        val frac = (skill.toDouble() / SKILL_CAP).coerceIn(0.0, 1.0)
        return (CRIT_BASE + CRIT_PER_SKILL * frac).coerceIn(0.0, 1.0)
    }

    /** Deterministic critical-craft decision for a `(skill, seed)`, independent of the quality roll. */
    fun isCritical(skill: Int, seed: Long): Boolean = uniform(seed xor CRIT_SALT) < criticalChance(skill)

    /** splitmix64 finalizer -> a uniform double in `[0,1)`. Pure, deterministic, well-distributed. */
    private fun uniform(seed: Long): Double {
        var z = seed + -0x61c8864680b583ebL          // 0x9E3779B97F4A7C15
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        z = z xor (z ushr 31)
        return (z ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}

// ---------------------------------------------------------------------------------------------------
// Registries (house style -- match ItemDefinitionRegistry / ReforgeRegistry exactly)
// ---------------------------------------------------------------------------------------------------

/**
 * Owner-scoped [Recipe] registry, matching the house style ([dev.willram.ramrpg.api.items.ItemDefinitionRegistry],
 * [dev.willram.ramrpg.api.reforges.ReforgeRegistry]). `/rpg reload` unregisters a content owner via
 * [unregisterOwner]. WP-3.1b implements this.
 */
interface RecipeRegistry {
    fun register(owner: String, recipe: Recipe)
    fun unregisterOwner(owner: String): Int
    fun get(key: RecipeKey): Recipe?
    fun all(): Collection<Recipe>

    /** Every registered recipe for a station -- backs the station's filtered recipe-book page (roadmap 3.1). */
    fun forStation(station: StationKey): Collection<Recipe>
}

/**
 * Owner-scoped [Station] registry, same house style as [RecipeRegistry]. Stations are content
 * ([dev.willram.ramrpg.core.config.specs.StationSpec]) so they too must unregister by owner on reload.
 * (The WP names [RecipeRegistry] explicitly; this is its necessary sibling for the station content type.)
 */
interface StationRegistry {
    fun register(owner: String, station: Station)
    fun unregisterOwner(owner: String): Int
    fun get(key: StationKey): Station?
    fun all(): Collection<Station>
}

// ---------------------------------------------------------------------------------------------------
// Craft result + planner (pure) and the service (WP-3.1b)
// ---------------------------------------------------------------------------------------------------

/** Why a craft could not proceed. */
enum class CraftFailure {
    /** [Station.permittedKinds] does not allow this outcome kind. */
    DISALLOWED_OUTCOME,

    /** At least one [Recipe.requirements] entry is unmet ([ItemRequirement.isMet] `false`). */
    UNMET_REQUIREMENT,

    /** The supplied inputs do not satisfy every [Recipe.inputs] ingredient (type and/or count). */
    MISSING_INPUTS,

    /** The player cannot afford [RecipeCost.money]. */
    INSUFFICIENT_FUNDS,

    /** A [requiresTargetItem] outcome had no target instance. */
    NO_TARGET_ITEM,

    /** A target was present but invalid for the outcome (e.g. a socket index it does not have). */
    INVALID_TARGET,
}

/**
 * One removal the craft performs, from ONE matched candidate. EXACTLY ONE of [itemKey] / [material]
 * identifies what to remove: an [itemKey] for an RPG-identified stack (whatever ingredient matched it),
 * a [material] for a plain vanilla stack. This is the SINGLE source of truth for consumption -- WP-3.1b
 * removes strictly from [CraftResult.Success.consumption] and NEVER re-derives anything from
 * [Recipe.inputs]. That is what prevents an RPG stack matched by a [Ingredient.MaterialTag]/
 * [Ingredient.Category] ingredient from being consumed twice.
 */
data class ConsumedInput(
    val count: Int,
    val itemKey: ItemKey? = null,
    val material: Material? = null,
)

/**
 * The PURE outcome of planning a craft -- computable and assertable with no [ItemStack] mutation and no
 * live server, so WP-3.1b can unit-test the plan. [CraftingService.craft] returns this too.
 */
sealed interface CraftResult {
    /**
     * @property outcome the [OutcomePlan] to apply.
     * @property consumption ONE [ConsumedInput] per satisfied [Recipe.inputs] ingredient, each naming
     *   exactly the matched candidate to remove (by [ItemKey] or [Material]). The SINGLE source of truth
     *   for what the craft consumes -- see [ConsumedInput].
     * @property cost the [RecipeCost] to charge.
     * @property quality the rolled [QualityRoll] value (also inside [OutcomePlan.Create] for NewItem).
     * @property critical whether this was a critical craft ([QualityRoll.isCritical]).
     */
    data class Success(
        val outcome: OutcomePlan,
        val consumption: List<ConsumedInput>,
        val cost: RecipeCost,
        val quality: Double,
        val critical: Boolean,
    ) : CraftResult

    data class Failure(val reason: CraftFailure) : CraftResult
}

/**
 * The pure inputs [CraftPlanner.plan] needs -- no [Player], no [ItemStack]. WP-3.1b adapts a live craft
 * (held items, the player's skill/stat state, balance) onto this so the planning stays testable.
 *
 * @property requirementState the crafter's state for [ItemRequirement.isMet] (reuses WP-2.1a).
 * @property inputs the candidate stacks the crafter supplied.
 * @property target the item being upgraded/repaired/enchanted/reforged/socketed, or `null`.
 * @property balance the crafter's available money (default: unlimited, for tests that ignore cost).
 * @property skillLevel the crafter's level in the quality-driving skill (feeds [QualityRoll]).
 * @property seed the deterministic craft seed (feeds [QualityRoll]).
 * @property permittedKinds the opening [Station.permittedKinds], or `null` to skip the station gate.
 */
data class CraftContext(
    val requirementState: ItemRequirementState,
    val inputs: List<IngredientCandidate> = emptyList(),
    val target: ItemInstanceData? = null,
    val balance: Double = Double.MAX_VALUE,
    val skillLevel: Int = 0,
    val seed: Long = 0L,
    val permittedKinds: Set<RecipeOutcomeKind>? = null,
)

/**
 * Pure craft planning: gates a [Recipe] against a [CraftContext] and computes the [CraftResult] with no
 * server or [ItemStack] mutation. WP-3.1b's `CraftingServiceImpl` adapts the live craft onto a
 * [CraftContext], calls [plan], then applies the returned [OutcomePlan] and [CraftResult.Success.consumption]
 * on the player's `TaskContext`. Mirrors the WP-2.1a precedent of shipping pure evaluation logic
 * ([isMet]) in the api for the impl to call.
 */
object CraftPlanner {
    fun plan(recipe: Recipe, ctx: CraftContext): CraftResult {
        // 1. Station gate.
        val permitted = ctx.permittedKinds
        if (permitted != null && recipe.outcome.kind !in permitted) {
            return CraftResult.Failure(CraftFailure.DISALLOWED_OUTCOME)
        }
        // 2. Requirement gate (reuse WP-2.1a isMet).
        if (recipe.requirements.any { !it.isMet(ctx.requirementState) }) {
            return CraftResult.Failure(CraftFailure.UNMET_REQUIREMENT)
        }
        // 3. Inputs -> consumption. Match SPECIFIC ingredients before GENERAL ones (Item < Predicate <
        //    Category < MaterialTag) so a general ingredient never eats a stack a specific ingredient
        //    needed, failing an otherwise-solvable craft. One ConsumedInput per satisfied ingredient
        //    names exactly the matched candidate to remove (by ItemKey when it has one, else Material) --
        //    the single source of truth, so an RPG stack matched by a tag/category ingredient is recorded
        //    once and never double-consumed. Count-aware: a candidate's count is drawn down as consumed.
        val available = ctx.inputs.toMutableList()
        val consumption = ArrayList<ConsumedInput>(recipe.inputs.size)
        for (ingredient in recipe.inputs.sortedBy { specificity(it) }) {
            val idx = available.indexOfFirst { ingredient.matches(it) }
            if (idx < 0) return CraftResult.Failure(CraftFailure.MISSING_INPUTS)
            val candidate = available[idx]
            consumption += if (candidate.itemKey != null) {
                ConsumedInput(count = ingredient.count, itemKey = candidate.itemKey)
            } else {
                ConsumedInput(count = ingredient.count, material = candidate.material)
            }
            val remaining = candidate.count - ingredient.count
            if (remaining <= 0) available.removeAt(idx) else available[idx] = candidate.copy(count = remaining)
        }
        // 4. Cost gate (money; XP-level gating needs the live player and is WP-3.1b's).
        if (ctx.balance < recipe.cost.money) {
            return CraftResult.Failure(CraftFailure.INSUFFICIENT_FUNDS)
        }
        // 5. Quality + crit (deterministic).
        val quality = QualityRoll(ctx.skillLevel, ctx.seed)
        val critical = QualityRoll.isCritical(ctx.skillLevel, ctx.seed)
        // 6. Outcome plan (target validation).
        if (recipe.outcome.requiresTargetItem && ctx.target == null) {
            return CraftResult.Failure(CraftFailure.NO_TARGET_ITEM)
        }
        val outcomePlan = recipe.outcome.plan(ctx.target, quality)
            ?: return CraftResult.Failure(CraftFailure.INVALID_TARGET)
        return CraftResult.Success(outcomePlan, consumption, recipe.cost, quality, critical)
    }

    /** Lower = more specific: matched first so a general ingredient never eats a specific stack. */
    private fun specificity(ingredient: Ingredient): Int = when (ingredient) {
        is Ingredient.Item -> 0
        is Ingredient.Predicate -> 1
        is Ingredient.Category -> 2
        is Ingredient.MaterialTag -> 3
    }
}

/**
 * The crafting service WP-3.1b implements. The service interface may reference [Player]/[MenuSession];
 * its RESULT ([CraftResult]) and PLAN ([OutcomePlan]) types are pure by construction.
 *
 * FOLIA. [craft] runs one unit of work on the player's `TaskContext` (WP-3.1b's impl concern): the pure
 * [CraftPlanner.plan] runs anywhere, but applying the plan (mutating held [ItemStack]s, charging the
 * economy) must be anchored to the player's scheduler. [open] routes through RamCore's menu, which is
 * already viewer-scheduler anchored.
 */
interface CraftingService {
    /** Open [station]'s menu for [player], returning the RamCore [MenuSession] (never a RamRPG type). */
    fun open(player: Player, station: Station): MenuSession

    /** Attempt [recipe] for [player] with the supplied [inputs]; returns the pure [CraftResult]. */
    fun craft(player: Player, recipe: Recipe, inputs: List<ItemStack>): CraftResult
}
