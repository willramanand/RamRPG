/**
 * WP-3.3d: PURE, server-free helpers for the three socket crafting operations -- cut a new socket slot
 * ([RecipeOutcome.AddSocket][dev.willram.ramrpg.api.crafting.RecipeOutcome.AddSocket]), insert a gem
 * ([RecipeOutcome.InsertGem][dev.willram.ramrpg.api.crafting.RecipeOutcome.InsertGem]) and remove a gem
 * ([RecipeOutcome.RemoveGem][dev.willram.ramrpg.api.crafting.RecipeOutcome.RemoveGem]).
 *
 * The generic craft engine ([dev.willram.ramrpg.core.services.CraftEngine] /
 * [dev.willram.ramrpg.core.services.CraftingServiceImpl]) already APPLIES the collapsed
 * [OutcomePlan.Modify][dev.willram.ramrpg.api.crafting.OutcomePlan.Modify] each of these produces, so
 * WP-3.3d adds CONTENT (`content/recipes/sockets.conf`) and the two RPG RULES the engine does not know:
 *
 *  1. **Slot cap.** A socket cut must respect a per-item CAP. `RecipeOutcome.plan` (api/) validates only
 *     that a socket INDEX is in range for insert/remove; it does NOT cap [AddSocket] -- it will happily
 *     append past any limit. [validateAddSocket] is therefore the gate a cut must pass BEFORE the plan is
 *     applied. The cap comes from the item's [Rarity] via the EXISTING shared [RarityRules.socketCap]
 *     ladder -- no parallel cap table is forked here.
 *  2. **Cost.** What each operation costs (see the cost model below), mirrored by the shipped recipes so
 *     content and the future runtime read ONE source of truth.
 *
 * Nothing here touches Bukkit or a live server: every check is a pure function of the target's current
 * [SocketData] list, and stats from a socketed gem still flow through the UNCHANGED
 * [dev.willram.ramrpg.core.services.SocketStatProvider] (no bespoke stat path is introduced). Adventure
 * [Component] is used only to map a validation error to its player-facing translatable string -- the same
 * "pure logic may build Components" convention `api/items` already follows.
 */
package dev.willram.ramrpg.core.crafting

import dev.willram.ramrpg.api.crafting.RecipeCost
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.RarityRules
import dev.willram.ramrpg.api.items.SocketData
import net.kyori.adventure.text.Component

object SocketOutcomes {

    // ---------------------------------------------------------------------------------------------
    // Slot cap (reuses the shared RarityRules ladder -- no parallel cap table)
    // ---------------------------------------------------------------------------------------------

    /** Max socket slots an item of [rarity] may ever hold -- the shared [RarityRules.socketCap] ladder. */
    fun slotCap(rarity: Rarity): Int = RarityRules.socketCap(rarity)

    /** Slots that may still be cut before hitting [cap] for an item that has [currentSlots] (never negative). */
    fun remainingSlots(currentSlots: Int, cap: Int): Int = (cap - currentSlots).coerceAtLeast(0)

    /** `true` iff cutting [count] more slot(s) onto an item with [currentSlots] stays within [cap]. */
    fun canAddSockets(currentSlots: Int, count: Int, cap: Int): Boolean =
        count > 0 && currentSlots >= 0 && currentSlots + count <= cap

    // ---------------------------------------------------------------------------------------------
    // Per-operation validation (the cap + occupancy rules RecipeOutcome.plan does not cover)
    // ---------------------------------------------------------------------------------------------

    /** Why a socket operation is rejected before its plan is applied. */
    enum class SocketOpError {
        /** [AddSocket] would push the slot count past the [slotCap]. */
        SLOT_CAP_EXCEEDED,

        /** A non-positive socket count was requested for a cut. */
        NON_POSITIVE_COUNT,

        /** An insert/remove socket index is outside the target's existing slots. */
        INDEX_OUT_OF_RANGE,

        /** Insert targeted a slot that already holds a gem (remove it first). */
        SOCKET_OCCUPIED,

        /** Remove targeted a slot that holds no gem. */
        SOCKET_EMPTY,
    }

    /** Validate cutting [count] socket(s) onto [sockets] under [cap]; `null` = OK. */
    fun validateAddSocket(sockets: List<SocketData>, count: Int, cap: Int): SocketOpError? = when {
        count <= 0 -> SocketOpError.NON_POSITIVE_COUNT
        !canAddSockets(sockets.size, count, cap) -> SocketOpError.SLOT_CAP_EXCEEDED
        else -> null
    }

    /** Convenience over [validateAddSocket] using the target [rarity]'s cap. */
    fun validateAddSocket(sockets: List<SocketData>, count: Int, rarity: Rarity): SocketOpError? =
        validateAddSocket(sockets, count, slotCap(rarity))

    /** Validate inserting a gem at [index] of [sockets]; `null` = OK. Fails if the slot is missing or full. */
    fun validateInsertGem(sockets: List<SocketData>, index: Int): SocketOpError? = when {
        index !in sockets.indices -> SocketOpError.INDEX_OUT_OF_RANGE
        sockets[index].gem != null -> SocketOpError.SOCKET_OCCUPIED
        else -> null
    }

    /** Validate removing a gem at [index] of [sockets]; `null` = OK. Fails if the slot is missing or empty. */
    fun validateRemoveGem(sockets: List<SocketData>, index: Int): SocketOpError? = when {
        index !in sockets.indices -> SocketOpError.INDEX_OUT_OF_RANGE
        sockets[index].gem == null -> SocketOpError.SOCKET_EMPTY
        else -> null
    }

    /**
     * The player-facing translatable message for a [SocketOpError]. [INDEX_OUT_OF_RANGE] reuses the
     * existing generic crafting `invalid_target` string (the same failure `RecipeOutcome.plan` maps an
     * out-of-range index to); the socket-specific reasons get their own `ramrpg.crafting.socket.*` keys.
     */
    fun message(error: SocketOpError): Component = when (error) {
        SocketOpError.SLOT_CAP_EXCEEDED -> Component.translatable("ramrpg.crafting.socket.cap_reached")
        SocketOpError.NON_POSITIVE_COUNT -> Component.translatable("ramrpg.crafting.socket.invalid_count")
        SocketOpError.INDEX_OUT_OF_RANGE -> Component.translatable("ramrpg.crafting.failure.invalid_target")
        SocketOpError.SOCKET_OCCUPIED -> Component.translatable("ramrpg.crafting.socket.occupied")
        SocketOpError.SOCKET_EMPTY -> Component.translatable("ramrpg.crafting.socket.empty")
    }

    // ---------------------------------------------------------------------------------------------
    // Cost model (mirrored by content/recipes/sockets.conf; tuned like the 0.1 power-curve placeholders)
    // ---------------------------------------------------------------------------------------------

    /** Money to cut the FIRST socket. */
    const val SOCKET_CUT_BASE_MONEY: Double = 500.0

    /** Extra money added per socket already present, so later sockets on the same item cost more to cut. */
    const val SOCKET_CUT_MONEY_PER_EXISTING: Double = 500.0

    /** XP levels to cut a new socket slot. */
    const val SOCKET_CUT_XP_LEVELS: Int = 3

    /** Flat money to insert a gem (the gem reagent is a consumed [dev.willram.ramrpg.api.crafting.Recipe.inputs], not part of this). */
    const val GEM_INSERT_MONEY: Double = 250.0

    /** Money to remove (and destroy) a socketed gem. */
    const val GEM_REMOVE_MONEY: Double = 750.0

    /**
     * [RecipeCost] to cut the next socket onto an item that already has [existingSlots] slots. Scales with
     * the slot count so each additional socket costs more; the shipped `cut_socket` recipe encodes the
     * base cut (`existingSlots = 0`) because a static HOCON file cannot know the target's slot count, and
     * the future runtime substitutes the scaled cost.
     */
    fun addSocketCost(existingSlots: Int = 0): RecipeCost = RecipeCost(
        money = SOCKET_CUT_BASE_MONEY + SOCKET_CUT_MONEY_PER_EXISTING * existingSlots.coerceAtLeast(0),
        experienceLevels = SOCKET_CUT_XP_LEVELS,
    )

    /** Flat [RecipeCost] to insert a gem. */
    fun insertGemCost(): RecipeCost = RecipeCost(money = GEM_INSERT_MONEY)

    /** Flat [RecipeCost] to remove a gem. */
    fun removeGemCost(): RecipeCost = RecipeCost(money = GEM_REMOVE_MONEY)
}
