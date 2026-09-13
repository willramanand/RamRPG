/**
 * WP-3.1c: the recipe book -- a RamCore [PaginatedMenu] (rule 1, never a RamRPG menu type) that lists
 * recipes, filtered by station and/or by what the player can currently make. Two layers live here:
 *
 *  - [RecipeBookFilter] + [RecipeBookQuery] -- the PURE, server-free filter/sort. Everything it reads is
 *    pure ([Recipe.station], [Recipe.outcome]'s [dev.willram.ramrpg.api.crafting.RecipeOutcomeKind], and
 *    [dev.willram.ramrpg.api.crafting.Recipe.requirements] via the WP-2.1a [isMet] against a pure
 *    [ItemRequirementState]), so the "which recipes show for a station / a player's skill" decision is
 *    unit-tested off-server by RecipeBookFilterTest. It is the single source of truth for what the book
 *    shows -- [build] renders exactly [RecipeBookFilter.visible].
 *  - [RecipeBookMenu] -- assembles the live [PaginatedMenu] over that filtered list (RamCore does the page
 *    math, unit-tested in RamCore's own tests / the PaginatedMenu precedent QuestsGui uses).
 *
 * OPENER SEAM. This file does NOT resolve services itself: the module/command/station-button that opens
 * the book resolves the recipe list via `ctx.service(RpgServiceKeys.RECIPE_REGISTRY)` (station-scoped via
 * [dev.willram.ramrpg.api.crafting.RecipeRegistry.forStation], or `all()` for a global book) and the
 * player's [ItemRequirementState] via the existing `requirementStateFor(player, services)` helper, then
 * calls [open]. No such call site exists yet (this WP may not touch RamRPG.kt / StationMenu / a module);
 * the orchestrator wires one at merge -- see the WP-3.1c report.
 *
 * FOLIA. Opening/reopening runs through RamCore's [Menus.open]/[PaginatedMenu.open], which is already
 * viewer-scheduler anchored; the filter toggle reopens the book on the click handler's (player region)
 * thread.
 */
package dev.willram.ramrpg.core.menus

import dev.willram.ramcore.menu.MenuButton
import dev.willram.ramcore.menu.Menus
import dev.willram.ramcore.menu.PaginatedMenu
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeOutcomeKind
import dev.willram.ramrpg.api.crafting.StationKey
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.isMet
import dev.willram.ramrpg.core.rendering.markGuiIcon
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * The PURE selection criteria for a recipe book page. All fields are pure keys/flags so
 * [RecipeBookFilter.visible] is decidable with no server.
 *
 * @property station only recipes for this [StationKey], or `null` for every station (a global book).
 * @property permittedKinds only recipes whose outcome kind is in this set (a station's
 *   [dev.willram.ramrpg.api.crafting.Station.permittedKinds]), or `null` to skip the kind gate.
 * @property onlyCraftable when `true`, drop recipes whose [Recipe.requirements] are not all met.
 */
data class RecipeBookQuery(
    val station: StationKey? = null,
    val permittedKinds: Set<RecipeOutcomeKind>? = null,
    val onlyCraftable: Boolean = false,
)

/**
 * The PURE recipe-book filter/sort. Off-server-testable (RecipeBookFilterTest): it never touches a
 * [Player], [ItemStack] or [dev.willram.ramcore.menu.MenuView].
 */
object RecipeBookFilter {

    /** `true` iff every one of [recipe]'s requirements is met against [state] (WP-2.1a [isMet]). Pure. */
    fun canCraft(recipe: Recipe, state: ItemRequirementState): Boolean =
        recipe.requirements.all { it.isMet(state) }

    /**
     * The recipes visible in the book for [query], evaluated against [state]. Filters by station, by the
     * station's permitted outcome kinds, and (when [RecipeBookQuery.onlyCraftable]) by met requirements;
     * then sorts craftable-first (so what the player can make floats to the top) with a stable
     * key-ordered tiebreak. Pure -- same inputs, same list.
     */
    fun visible(recipes: Collection<Recipe>, query: RecipeBookQuery, state: ItemRequirementState): List<Recipe> =
        recipes.asSequence()
            .filter { query.station == null || it.station == query.station }
            .filter { query.permittedKinds == null || it.outcome.kind in query.permittedKinds }
            .filter { !query.onlyCraftable || canCraft(it, state) }
            .sortedWith(compareBy({ !canCraft(it, state) }, { it.key.id.value() }))
            .toList()
}

object RecipeBookMenu {

    private val ITEM_SLOTS: List<Int> = (0..44).toList()
    private const val PREV_SLOT = 45
    private const val FILTER_SLOT = 49
    private const val NEXT_SLOT = 53
    private const val EMPTY_SLOT = 22

    /**
     * Opens the recipe book for [player] over the [recipes] the opener resolved from
     * [dev.willram.ramrpg.api.crafting.RecipeRegistry] (see this file's header). [state] is the player's
     * pure requirement snapshot; [station]/[permittedKinds] scope the book; [onlyCraftable] toggles the
     * craftable-only filter (the in-menu filter button reopens with it flipped).
     */
    fun open(
        player: Player,
        recipes: Collection<Recipe>,
        state: ItemRequirementState,
        station: StationKey? = null,
        permittedKinds: Set<RecipeOutcomeKind>? = null,
        onlyCraftable: Boolean = false,
    ) {
        build(player, recipes, state, station, permittedKinds, onlyCraftable).open(player)
    }

    /** Builds (without opening) the [PaginatedMenu] the book renders -- the filtered list plus nav/filter. */
    fun build(
        player: Player,
        recipes: Collection<Recipe>,
        state: ItemRequirementState,
        station: StationKey? = null,
        permittedKinds: Set<RecipeOutcomeKind>? = null,
        onlyCraftable: Boolean = false,
    ): PaginatedMenu<Recipe> {
        val query = RecipeBookQuery(station, permittedKinds, onlyCraftable)
        val visible = RecipeBookFilter.visible(recipes, query, state)
        return Menus.paginated<Recipe>(Component.translatable("ramrpg.crafting.book.title"), 6) { recipe, _ ->
            recipeButton(recipe, RecipeBookFilter.canCraft(recipe, state))
        }
            .entries(visible)
            .slots(*ITEM_SLOTS.toIntArray())
            .previous(PREV_SLOT, navButton(Component.translatable("ramrpg.crafting.book.button.prev")))
            .next(NEXT_SLOT, navButton(Component.translatable("ramrpg.crafting.book.button.next")))
            .decorate { view ->
                view.button(FILTER_SLOT, filterButton(player, recipes, state, station, permittedKinds, onlyCraftable))
                if (visible.isEmpty()) {
                    view.button(EMPTY_SLOT, MenuButton.of(icon(Material.BARRIER, Component.translatable("ramrpg.crafting.book.empty"))))
                }
            }
            .build()
    }

    // -- buttons -----------------------------------------------------------------------------------

    private fun recipeButton(recipe: Recipe, craftable: Boolean): MenuButton {
        val material = if (craftable) Material.CRAFTING_TABLE else Material.GRAY_STAINED_GLASS_PANE
        val color = if (craftable) NamedTextColor.GREEN else NamedTextColor.GRAY
        val stack = ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(Component.text(recipe.key.id.value(), color).decoration(TextDecoration.ITALIC, false))
                val lore = ArrayList<Component>()
                lore += Component.text(recipe.outcome.kind.name.lowercase(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                lore += Component.text(if (craftable) "Craftable" else "Locked", color)
                    .decoration(TextDecoration.ITALIC, false)
                meta.lore(lore)
            }
        }
        return MenuButton.of(stack.markGuiIcon())
    }

    private fun navButton(name: Component): MenuButton =
        MenuButton.of(icon(Material.ARROW, name.color(NamedTextColor.YELLOW)))

    /** Toggle between the "all" and "craftable-only" views; clicking reopens the book with it flipped. */
    private fun filterButton(
        player: Player,
        recipes: Collection<Recipe>,
        state: ItemRequirementState,
        station: StationKey?,
        permittedKinds: Set<RecipeOutcomeKind>?,
        onlyCraftable: Boolean,
    ): MenuButton {
        val label = if (onlyCraftable) {
            Component.translatable("ramrpg.crafting.book.filter.craftable")
        } else {
            Component.translatable("ramrpg.crafting.book.filter.all")
        }
        val material = if (onlyCraftable) Material.WRITABLE_BOOK else Material.BOOK
        return MenuButton.builder(icon(material, label.color(NamedTextColor.AQUA)))
            .onAny(Runnable { open(player, recipes, state, station, permittedKinds, !onlyCraftable) })
            .build()
    }

    private fun icon(material: Material, name: Component): ItemStack =
        ItemStack(material).apply {
            editMeta { it.displayName(name.decoration(TextDecoration.ITALIC, false)) }
        }.markGuiIcon()
}
