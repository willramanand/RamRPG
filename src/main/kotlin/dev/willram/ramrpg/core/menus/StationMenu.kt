/**
 * WP-3.1b: the station GUI, built on RamCore's declarative [MenuView]/[MenuSession] (rule 1 -- never a
 * RamRPG menu type). [StationLayout] is the PURE slot math (input slots vs target/preview/confirm),
 * unit-tested off-server; [StationMenu.interactiveView] assembles the live menu over the station's
 * RamCore [MenuView] (its title + row count).
 *
 * INPUT MODEL (dup-safety). RamCore's [MenuSession] cancels every top-inventory click, so a player never
 * physically holds an item in a menu slot: instead each input/target slot is a button whose click handler
 * moves a stack between the player's CURSOR and this session's [MenuState] "escrow" (keys `hold.<slot>`).
 * The escrow is therefore the SINGLE place a deposited stack exists between deposit and craft/close --
 * there is no second copy in a real inventory slot to duplicate. On a valid craft, [CraftingServiceImpl.craft]
 * validates + charges + grants INLINE on this (the player's owning region) thread and the consumed inputs
 * are then removed from the escrow strictly per [CraftEngine.consumptionTotals] (the ConsumedInput list,
 * never re-derived from the recipe) -- one synchronous unit, no scheduler hop, so a quit/reload between
 * grant and drain is impossible (WP-3.1b review R1). On close the escrow is returned to the player
 * ([CraftingServiceImpl.onSessionClosed]); on a mid-craft quit the same drain runs from CraftingModule's
 * quit handler (RamCore's session invalidates on quit WITHOUT firing its close handler).
 */
package dev.willram.ramrpg.core.menus

import dev.willram.ramcore.menu.MenuButton
import dev.willram.ramcore.menu.MenuClickContext
import dev.willram.ramcore.menu.MenuSession
import dev.willram.ramcore.menu.MenuView
import dev.willram.ramrpg.api.crafting.CraftFailure
import dev.willram.ramrpg.api.crafting.CraftResult
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.Station
import dev.willram.ramrpg.api.crafting.requiresTargetItem
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.core.rendering.PacketItemRenderer
import dev.willram.ramrpg.core.rendering.markGuiIcon
import dev.willram.ramrpg.core.services.CraftEngine
import dev.willram.ramrpg.core.services.CraftingServiceImpl
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * PURE slot geometry for a station menu of [rows] chest rows. The player deposits reagents into
 * [ingredientSlots] and the item to transform into [targetSlot]; the live preview renders at
 * [previewSlot] and the craft button at [confirmSlot]. Roles sit on distinct COLUMNS (target col 0,
 * ingredients cols 1-3, preview col 6, confirm col 8) so they never collide for any legal row count
 * (1-6). Unit-tested by StationLayoutTest; builds no [MenuView].
 */
class StationLayout(val rows: Int) {
    init { require(rows in 1..6) { "station menu rows must be 1..6, was $rows" } }

    val size: Int = rows * 9

    /** The vertically-centred control row target/preview/confirm sit on. */
    private val midRow: Int = (rows - 1) / 2

    /** Item-to-transform slot (col 0 of the control row). */
    val targetSlot: Int = midRow * 9
    /** Live-preview / result slot (col 6 of the control row). */
    val previewSlot: Int = midRow * 9 + 6
    /** Craft/confirm button slot (col 8 of the control row). */
    val confirmSlot: Int = midRow * 9 + 8

    /** Reagent slots: columns 1-3 of every row (three per row). */
    val ingredientSlots: List<Int> = buildList {
        for (r in 0 until rows) for (c in 1..3) add(r * 9 + c)
    }

    /** Every slot a player may deposit into (reagents + the single target slot). */
    val inputSlots: List<Int> = ingredientSlots + targetSlot

    /** Slots reserved for the framing filler (everything that is neither an input nor a control). */
    val fillerSlots: List<Int> =
        (0 until size).toList() - (inputSlots + previewSlot + confirmSlot).toSet()
}

object StationMenu {

    private const val HOLD_PREFIX = "hold."

    /**
     * Assembles the live, interactive menu for [station] over its RamCore [MenuView] (title + rows).
     * Everything is drawn in the [MenuView.Builder.render] pass from the session's escrow, so a click that
     * changes the escrow (via [MenuButton.Builder.refreshAfterClick]) redraws inputs, preview and the
     * craft button together.
     */
    fun interactiveView(
        station: Station,
        service: CraftingServiceImpl,
        recipes: Collection<Recipe>,
        renderer: PacketItemRenderer,
        instances: ItemInstanceService,
    ): MenuView {
        val layout = StationLayout(station.menuLayout.rows())
        val recipeList = recipes.toList()
        return MenuView.builder(station.displayName, layout.rows)
            .render { session -> render(session, layout, service, recipeList, instances) }
            .onClose { session -> service.onSessionClosed(session) }
            .build()
    }

    /** Drains and returns every escrowed stack, clearing the escrow. Idempotent (a second call returns nothing). */
    fun drainHeld(session: MenuSession): List<ItemStack> {
        val state = session.state()
        val out = ArrayList<ItemStack>()
        for (key in state.asMap().keys.filter { it.startsWith(HOLD_PREFIX) }.toList()) {
            (state.get(key) as? ItemStack)?.let(out::add)
            state.remove(key)
        }
        return out
    }

    // -- render ------------------------------------------------------------------------------------

    private fun render(
        session: MenuSession,
        layout: StationLayout,
        service: CraftingServiceImpl,
        recipes: List<Recipe>,
        instances: ItemInstanceService,
    ) {
        for (slot in layout.fillerSlots) session.setButton(slot, MenuButton.of(filler()))

        for (slot in layout.ingredientSlots) {
            val held = heldAt(session, slot)
            val placeholder = placeholder(Component.translatable("ramrpg.crafting.slot.input"), Material.LIGHT_GRAY_STAINED_GLASS_PANE)
            session.setButton(slot, inputButton(slot, held ?: placeholder))
        }
        val target = heldAt(session, layout.targetSlot)
        val targetPlaceholder = placeholder(Component.translatable("ramrpg.crafting.slot.target"), Material.ITEM_FRAME)
        session.setButton(layout.targetSlot, inputButton(layout.targetSlot, target ?: targetPlaceholder))

        val player = session.player()
        val ingredients = layout.ingredientSlots.mapNotNull { heldAt(session, it) }
        val (matched, result) = bestPreview(service, player, recipes, target, ingredients)

        val previewStack = (result as? CraftResult.Success)?.let { service.buildPreviewStack(player, it, target)?.markGuiIcon() }
        val previewEmpty = placeholder(Component.translatable("ramrpg.crafting.preview.empty"), Material.BARRIER)
        session.setButton(layout.previewSlot, MenuButton.of(previewStack ?: previewEmpty))
        session.setButton(layout.confirmSlot, craftButton(layout, matched, result is CraftResult.Success, target, ingredients, service, instances))
    }

    /** First station recipe whose plan succeeds against the current escrow, with its preview result. */
    private fun bestPreview(
        service: CraftingServiceImpl,
        player: Player,
        recipes: List<Recipe>,
        target: ItemStack?,
        ingredients: List<ItemStack>,
    ): Pair<Recipe?, CraftResult?> {
        var lastFailure: CraftResult? = null
        for (recipe in recipes) {
            val inputs = inputsFor(recipe, target, ingredients)
            when (val res = service.previewFor(player, recipe, inputs)) {
                is CraftResult.Success -> return recipe to res
                is CraftResult.Failure -> lastFailure = res
            }
        }
        return null to lastFailure
    }

    /** Input list under [CraftingServiceImpl.craft]'s convention: target first for a target-consuming recipe. */
    private fun inputsFor(recipe: Recipe, target: ItemStack?, ingredients: List<ItemStack>): List<ItemStack> =
        if (recipe.outcome.requiresTargetItem) listOfNotNull(target) + ingredients else ingredients

    // -- buttons -----------------------------------------------------------------------------------

    /** A deposit/withdraw button for one input slot; the click moves a stack between cursor and escrow. */
    private fun inputButton(slot: Int, icon: ItemStack): MenuButton =
        MenuButton.builder(icon)
            .onAny { ctx -> onInputClick(ctx, slot) }
            .refreshAfterClick(true)
            .build()

    private fun onInputClick(ctx: MenuClickContext, slot: Int) {
        val session = ctx.session()
        val player = ctx.event().whoClicked as? Player ?: return
        val cursor = ctx.event().cursor
        val held = heldAt(session, slot)
        if (!cursor.type.isAir) {
            // Deposit the cursor stack; if the slot was occupied, swap it back onto the cursor.
            putHeld(session, slot, cursor.clone())
            player.setItemOnCursor(held)
        } else if (held != null) {
            // Withdraw the held stack onto the (empty) cursor.
            player.setItemOnCursor(held)
            removeHeld(session, slot)
        }
    }

    private fun craftButton(
        layout: StationLayout,
        matched: Recipe?,
        canCraft: Boolean,
        target: ItemStack?,
        ingredients: List<ItemStack>,
        service: CraftingServiceImpl,
        instances: ItemInstanceService,
    ): MenuButton {
        val icon = if (canCraft) craftIcon(Material.ANVIL, NamedTextColor.GREEN)
        else craftIcon(Material.GRAY_DYE, NamedTextColor.GRAY)
        return MenuButton.builder(icon)
            .onAny { ctx -> if (canCraft && matched != null) onCraftClick(ctx, layout, matched, target, ingredients, service, instances) }
            .refreshAfterClick(true)
            .build()
    }

    private fun onCraftClick(
        ctx: MenuClickContext,
        layout: StationLayout,
        recipe: Recipe,
        target: ItemStack?,
        ingredients: List<ItemStack>,
        service: CraftingServiceImpl,
        instances: ItemInstanceService,
    ) {
        val session = ctx.session()
        val player = session.player()
        val inputs = inputsFor(recipe, target, ingredients)
        // This handler runs on the player's owning region thread and service.craft applies INLINE (never a
        // scheduler hop -- see its threading contract), so validate + charge + grant + the escrow drain
        // below are ONE synchronous unit: craft() only mutates on a validated Success, and the escrow is
        // drained only on that Success, so no failure path leaves reagents consumed with nothing produced.
        when (val result = service.craft(player, recipe, inputs)) {
            is CraftResult.Success -> {
                // Remove the consumed reagents from the escrow, strictly per the ConsumedInput list.
                consumeEscrow(session, layout, result, instances)
                // A target-transforming craft granted the (single) modified target, so drop it from escrow.
                if (recipe.outcome.requiresTargetItem) removeHeld(session, layout.targetSlot)
                player.sendActionBar(Component.translatable("ramrpg.crafting.result.success").color(NamedTextColor.GREEN))
            }
            is CraftResult.Failure -> player.sendActionBar(failureMessage(result.reason))
        }
    }

    /** Draws down escrowed reagent stacks by the amounts in [result]'s consumption (folded to per-target totals). */
    private fun consumeEscrow(
        session: MenuSession,
        layout: StationLayout,
        result: CraftResult.Success,
        instances: ItemInstanceService,
    ) {
        val totals = CraftEngine.consumptionTotals(result.consumption).toMutableMap()
        for (slot in layout.ingredientSlots) {
            val held = heldAt(session, slot) ?: continue
            val key = instances.identify(held)?.identity?.key
            val target: CraftEngine.ConsumeTarget =
                key?.let { CraftEngine.ConsumeTarget.Rpg(it) } ?: CraftEngine.ConsumeTarget.Vanilla(held.type)
            val need = totals[target] ?: continue
            if (need <= 0) continue
            val take = minOf(need, held.amount)
            totals[target] = need - take
            val remaining = held.amount - take
            if (remaining <= 0) removeHeld(session, slot) else {
                held.amount = remaining
                putHeld(session, slot, held)
            }
        }
    }

    // -- escrow accessors --------------------------------------------------------------------------

    private fun heldAt(session: MenuSession, slot: Int): ItemStack? =
        session.state().get(HOLD_PREFIX + slot) as? ItemStack

    private fun putHeld(session: MenuSession, slot: Int, stack: ItemStack) {
        session.state().put(HOLD_PREFIX + slot, stack)
    }

    private fun removeHeld(session: MenuSession, slot: Int) {
        session.state().remove(HOLD_PREFIX + slot)
    }

    // -- icons -------------------------------------------------------------------------------------

    private fun filler(): ItemStack = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
        editMeta { it.displayName(Component.empty()) }
    }.markGuiIcon()

    private fun placeholder(name: Component, material: Material): ItemStack = ItemStack(material).apply {
        editMeta { it.displayName(name.color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)) }
    }.markGuiIcon()

    private fun craftIcon(material: Material, color: NamedTextColor): ItemStack = ItemStack(material).apply {
        editMeta { it.displayName(Component.translatable("ramrpg.crafting.button.craft").color(color).decoration(TextDecoration.ITALIC, false)) }
    }.markGuiIcon()

    /** RED failure line; each [CraftFailure] maps to its own translatable so the lang keys stay referenced. */
    private fun failureMessage(reason: CraftFailure): Component = when (reason) {
        CraftFailure.DISALLOWED_OUTCOME -> Component.translatable("ramrpg.crafting.failure.disallowed_outcome")
        CraftFailure.UNMET_REQUIREMENT -> Component.translatable("ramrpg.crafting.failure.unmet_requirement")
        CraftFailure.MISSING_INPUTS -> Component.translatable("ramrpg.crafting.failure.missing_inputs")
        CraftFailure.INSUFFICIENT_FUNDS -> Component.translatable("ramrpg.crafting.failure.insufficient_funds")
        CraftFailure.NO_TARGET_ITEM -> Component.translatable("ramrpg.crafting.failure.no_target_item")
        CraftFailure.INVALID_TARGET -> Component.translatable("ramrpg.crafting.failure.invalid_target")
    }.color(NamedTextColor.RED)
}
