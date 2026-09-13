/**
 * WP-3.1b: the crafting runtime. Two layers live here:
 *
 *  - [CraftEngine] -- the PURE, server-free heart of the generic outcome engine. It turns the pure
 *    [OutcomePlan] produced by 3.1a's [RecipeOutcome.plan] into a generic [CraftEngine.Application]
 *    (CREATE a fresh item, or MODIFY the target instance) and folds the [CraftResult.Success.consumption]
 *    list into per-target removal totals. Because EVERY input-transforming outcome
 *    (UpgradeInput/Repair/Enchant/Reforge/InsertGem/AddSocket/RemoveGem) is already collapsed by
 *    [RecipeOutcome.plan] into a single [OutcomePlan.Modify], the engine applies ALL of them through the
 *    ONE [CraftEngine.Application.ModifyTarget] arm -- so WP-3.3a/3.3c/3.3d add recipe CONTENT and cost
 *    tuning, never a new engine branch. No Bukkit here, so [CraftEngine] is unit-tested off-server.
 *  - [CraftingServiceImpl] -- the live [CraftingService]. [craft] adapts a live craft onto a
 *    [CraftContext], calls the pure [CraftPlanner.plan], and (on success) charges the cost and grants the
 *    output in ONE unit of work on the player's TaskContext (Folia rule 4). [open] builds the station's
 *    interactive [dev.willram.ramrpg.core.menus.StationMenu] over the station's RamCore [MenuView] and
 *    opens it as a [MenuSession].
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramcore.menu.MenuSession
import dev.willram.ramcore.menu.Menus
import dev.willram.ramrpg.api.crafting.ConsumedInput
import dev.willram.ramrpg.api.crafting.CraftContext
import dev.willram.ramrpg.api.crafting.CraftFailure
import dev.willram.ramrpg.api.crafting.CraftPlanner
import dev.willram.ramrpg.api.crafting.CraftResult
import dev.willram.ramrpg.api.crafting.CraftingService
import dev.willram.ramrpg.api.crafting.IngredientCandidate
import dev.willram.ramrpg.api.crafting.OutcomePlan
import dev.willram.ramrpg.api.crafting.Recipe
import dev.willram.ramrpg.api.crafting.RecipeOutcome
import dev.willram.ramrpg.api.crafting.RecipeRegistry
import dev.willram.ramrpg.api.crafting.Station
import dev.willram.ramrpg.api.crafting.requiresTargetItem
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemInstanceInit
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.core.listeners.EconomyService
import dev.willram.ramrpg.core.listeners.ItemRequirementServices
import dev.willram.ramrpg.core.listeners.requirementStateFor
import dev.willram.ramrpg.core.menus.StationMenu
import dev.willram.ramrpg.core.rendering.PacketItemRenderer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * The pure, server-free generic craft engine. Everything here is assertable with no [ItemStack]
 * mutation and no live server -- [CraftingServiceImpl] is the only thing that touches Bukkit.
 */
object CraftEngine {

    /**
     * How a successful craft's [OutcomePlan] is applied to the world -- a GENERIC, kind-agnostic
     * description. There are exactly two arms because [RecipeOutcome.plan] already reduces every one of
     * the nine outcome kinds to a [OutcomePlan.Create] (NewItem/Transmute) or a [OutcomePlan.Modify]
     * (every input-transforming outcome, socket arms included). [CraftingServiceImpl.applyCraft] switches
     * on these two arms only, so new outcome CONTENT needs no new engine code.
     */
    sealed interface Application {
        /** Create a fresh item [item] at the rolled [quality], [count] of it, stamped [craftedBy]. */
        data class CreateItem(val item: ItemKey, val quality: Double, val count: Int, val craftedBy: UUID?) : Application

        /** Write the already-transformed [result] instance onto the target stack. */
        data class ModifyTarget(val result: ItemInstanceData) : Application
    }

    /** Which stack a [ConsumedInput] removes from -- an RPG item by key, or a plain vanilla stack by material. */
    sealed interface ConsumeTarget {
        data class Rpg(val key: ItemKey) : ConsumeTarget
        data class Vanilla(val material: Material) : ConsumeTarget
    }

    /**
     * Maps a planned [outcome] to its generic [Application]. Total over both [OutcomePlan] arms, so it
     * covers ALL nine [RecipeOutcome] kinds without naming any of them.
     */
    fun applicationFor(outcome: OutcomePlan, craftedBy: UUID?): Application = when (outcome) {
        is OutcomePlan.Create -> Application.CreateItem(outcome.item, outcome.quality, outcome.count, craftedBy)
        is OutcomePlan.Modify -> Application.ModifyTarget(outcome.result)
    }

    /**
     * PURE go/no-go on whether an [Application] can actually be written, using only facts the caller
     * supplies (so it is unit-testable with no server). This is the [CraftingServiceImpl.craft]
     * validate-before-consume gate: it MUST return `null` (OK) before any escrow is drained, cost charged
     * or item granted, so no failure path ever leaves inputs consumed with nothing produced.
     *
     *  - CREATE: [outputDefExists] is `false` when the recipe's output [ItemKey] has no registered
     *    [dev.willram.ramrpg.api.items.ItemDefinition] (a content bug) -> [CraftFailure.INVALID_TARGET].
     *  - MODIFY: [targetAmount] is the target stack's amount (`null` when absent). A missing target is
     *    [CraftFailure.NO_TARGET_ITEM]; a stack of more than one is [CraftFailure.INVALID_TARGET] -- one
     *    craft cost must never transform a whole stack (see the WP-3.1b review, N3).
     *
     * Returns the blocking [CraftFailure], or `null` when the application is safe to apply.
     */
    fun validateApplication(app: Application, outputDefExists: Boolean, targetAmount: Int?): CraftFailure? =
        when (app) {
            is Application.CreateItem -> if (outputDefExists) null else CraftFailure.INVALID_TARGET
            is Application.ModifyTarget -> when {
                targetAmount == null -> CraftFailure.NO_TARGET_ITEM
                targetAmount > 1 -> CraftFailure.INVALID_TARGET
                else -> null
            }
        }

    /**
     * Folds the [CraftResult.Success.consumption] list -- the SINGLE source of truth for what a craft
     * removes -- into total counts per [ConsumeTarget]. [CraftingServiceImpl] / the station menu remove
     * strictly from this, NEVER re-deriving anything from [Recipe.inputs], so an RPG stack matched by a
     * tag/category ingredient is removed once (by key) and never double-consumed. Pure.
     */
    fun consumptionTotals(consumption: List<ConsumedInput>): Map<ConsumeTarget, Int> {
        val totals = LinkedHashMap<ConsumeTarget, Int>()
        for (c in consumption) {
            val target: ConsumeTarget = c.itemKey?.let { ConsumeTarget.Rpg(it) }
                ?: c.material?.let { ConsumeTarget.Vanilla(it) }
                ?: continue
            totals[target] = (totals[target] ?: 0) + c.count
        }
        return totals
    }

    /**
     * The pure preview/plan entry the station menu and [CraftingService.craft] share, so a preview can
     * never disagree with what a craft would actually do (both are [CraftPlanner.plan]). Kept here to make
     * that single-source-of-truth explicit and unit-testable.
     */
    fun preview(recipe: Recipe, ctx: CraftContext): CraftResult = CraftPlanner.plan(recipe, ctx)
}

class CraftingServiceImpl(
    private val itemDefs: ItemDefinitionRegistry,
    private val itemInstances: ItemInstanceService,
    private val recipes: RecipeRegistry,
    private val economy: EconomyService,
    private val skillService: SkillService,
    private val renderer: PacketItemRenderer,
    private val requirementServices: ItemRequirementServices,
) : CraftingService {

    /** Station sessions with items still escrowed, so a quit can return them (see [returnHeldOnQuit]). */
    private val openByPlayer = ConcurrentHashMap<UUID, MenuSession>()

    override fun open(player: Player, station: Station): MenuSession {
        val view = StationMenu.interactiveView(
            station = station,
            service = this,
            recipes = recipes.forStation(station.key),
            renderer = renderer,
            instances = itemInstances,
        )
        val session = Menus.open(player, view)
        track(session)
        return session
    }

    /**
     * Plans [recipe] against [player]'s state and [inputs] and, on success, charges the cost and grants
     * the output.
     *
     * THREADING (must-call contract). This applies its side effects INLINE -- it does NOT hop through a
     * scheduler, because RamCore's [PlatformScheduler.runForPlayer]/`Schedulers.run` normalise to a >= 1
     * tick delay (there is no inline path), which would defer the charge+grant a tick past the caller's
     * synchronous escrow drain and lose the inputs on a quit/reload/disable in between (WP-3.1b review,
     * R1). CALLERS MUST THEREFORE INVOKE `craft` ON THE PLAYER'S OWNING REGION THREAD -- the sole caller,
     * [dev.willram.ramrpg.core.menus.StationMenu]'s craft button, runs inside an inventory-click handler,
     * which Folia already dispatches on that thread. The plan, cost charge, and grant then form one
     * synchronous unit with the menu's escrow drain (which runs immediately after this returns Success).
     *
     * VALIDATE-BEFORE-CONSUME (R2). Every write is validated -- planner gates, XP-level cost, output
     * definition resolvable, single-item target -- BEFORE any side effect. Any failure returns a
     * [CraftResult.Failure] having changed nothing, so the caller consumes escrow only on Success.
     *
     * Input convention (the station menu obeys it): for a [requiresTargetItem] recipe, `inputs[0]` is the
     * TARGET to transform and `inputs.drop(1)` are the reagent candidates; otherwise every stack is a
     * reagent candidate. The target is never a reagent, so it is never consumed -- only transformed.
     */
    override fun craft(player: Player, recipe: Recipe, inputs: List<ItemStack>): CraftResult {
        val targetStack = targetStackOf(recipe, inputs)
        val ctx = contextFor(player, recipe, inputs, seed = ThreadLocalRandom.current().nextLong())

        val result = CraftEngine.preview(recipe, ctx)
        if (result !is CraftResult.Success) return result

        // XP-level cost needs the live player, so it is gated here (money is gated by the planner via
        // CraftContext.balance). Fail without side effects if the player cannot pay the level cost.
        if (player.level < recipe.cost.experienceLevels) return CraftResult.Failure(CraftFailure.INSUFFICIENT_FUNDS)

        // R2: validate that every write can actually happen BEFORE any side effect, so a missing output
        // definition or an over-sized target aborts the craft with NOTHING consumed or granted.
        val app = CraftEngine.applicationFor(result.outcome, player.uniqueId)
        val outputDefExists = app !is CraftEngine.Application.CreateItem || itemDefs.get(app.item) != null
        val targetAmount = if (app is CraftEngine.Application.ModifyTarget) targetStack?.amount else null
        CraftEngine.validateApplication(app, outputDefExists, targetAmount)?.let { return CraftResult.Failure(it) }

        // R1: apply INLINE on the caller's (player's owning region) thread -- one synchronous unit with the
        // menu's escrow drain. Never scheduled (a >= 1 tick hop is the R1 loss bug).
        applyCraft(player, result, targetStack, app)
        return result
    }

    /**
     * Plans [recipe] against [player]'s CURRENT state and [inputs] WITHOUT any side effect -- the station
     * menu's live-preview path. Uses a fixed [PREVIEW_SEED] so the previewed quality is stable across
     * re-renders (a real craft re-rolls with a fresh seed). Shares [contextFor] with [craft], so a preview
     * can never disagree with what a craft would gate on.
     */
    fun previewFor(player: Player, recipe: Recipe, inputs: List<ItemStack>): CraftResult =
        CraftEngine.preview(recipe, contextFor(player, recipe, inputs, seed = PREVIEW_SEED))

    /**
     * Builds a display-only [ItemStack] for the menu's preview slot from a planned [result] -- the fresh
     * item for a Create, or the transformed target for a Modify. Never mutates a real inventory item.
     */
    fun buildPreviewStack(player: Player, result: CraftResult.Success, targetStack: ItemStack?): ItemStack? {
        val stack = when (val app = CraftEngine.applicationFor(result.outcome, player.uniqueId)) {
            is CraftEngine.Application.CreateItem -> {
                val def = itemDefs.get(app.item) ?: return null
                itemInstances.create(def, ItemInstanceInit(quality = app.quality)).also {
                    if (app.count > 1) it.amount = app.count
                }
            }
            is CraftEngine.Application.ModifyTarget -> {
                val base = targetStack ?: return null
                itemInstances.write(base.clone(), app.result)
            }
        }
        return renderer.render(player, stack)
    }

    /** The target stack under this WP's input convention (see [craft]); null for a fresh-item outcome. */
    fun targetStackOf(recipe: Recipe, inputs: List<ItemStack>): ItemStack? =
        if (recipe.outcome.requiresTargetItem) inputs.firstOrNull() else null

    /** Adapts a live craft onto a pure [CraftContext] (shared by [craft] and [previewFor]). */
    private fun contextFor(player: Player, recipe: Recipe, inputs: List<ItemStack>, seed: Long): CraftContext {
        val requiresTarget = recipe.outcome.requiresTargetItem
        val targetStack = if (requiresTarget) inputs.firstOrNull() else null
        val reagentStacks = if (requiresTarget) inputs.drop(1) else inputs

        val target: ItemInstanceData? = targetStack?.let { itemInstances.identify(it) }
        val candidates: List<IngredientCandidate> = reagentStacks.map { candidateFrom(it) }
        val qualitySkill = (recipe.outcome as? RecipeOutcome.NewItem)?.qualitySkill
        val skillLevel = qualitySkill?.let { skillService.level(player, it) } ?: 0

        return CraftContext(
            requirementState = requirementStateFor(player, requirementServices),
            inputs = candidates,
            target = target,
            balance = economy.ramCoreEconomy.balance(player.uniqueId),
            skillLevel = skillLevel,
            seed = seed,
            // Station-kind gating is enforced by open() (a station only surfaces its own recipes); craft()
            // is station-agnostic, so it does not re-apply the permitted-kinds gate here.
            permittedKinds = null,
        )
    }

    /**
     * Charges [result]'s cost and grants its output. GENERIC over every outcome via the two
     * [CraftEngine.Application] arms. Preconditions were checked by [craft] via
     * [CraftEngine.validateApplication] (output definition resolves; a Modify target is a single item), so
     * the defensive `?: return`s here can only fire on a caller that skipped that gate.
     */
    private fun applyCraft(player: Player, result: CraftResult.Success, targetStack: ItemStack?, app: CraftEngine.Application) {
        val cost = result.cost
        if (cost.money > 0.0) economy.ramCoreEconomy.withdraw(player.uniqueId, cost.money)
        if (cost.experienceLevels > 0) player.giveExpLevels(-cost.experienceLevels)

        when (app) {
            is CraftEngine.Application.CreateItem -> {
                val def = itemDefs.get(app.item) ?: return // validated in craft(); defensive only
                var stack = itemInstances.create(def, ItemInstanceInit(quality = app.quality))
                itemInstances.read(stack)?.let { data ->
                    stack = itemInstances.write(stack, data.copy(craftedBy = app.craftedBy))
                }
                if (app.count > 1) stack.amount = app.count
                giveOrDrop(player, stack)
            }
            is CraftEngine.Application.ModifyTarget -> {
                val base = targetStack ?: return // validated in craft(); defensive only
                // N3: transform exactly ONE item (craft() rejected amount > 1), never a whole stack for one cost.
                val one = base.clone().apply { amount = 1 }
                giveOrDrop(player, itemInstances.write(one, app.result))
            }
        }
    }

    /** Builds the pure [IngredientCandidate] snapshot for a live [stack] (RPG identity + categories, or plain vanilla). */
    private fun candidateFrom(stack: ItemStack): IngredientCandidate {
        val data = itemInstances.identify(stack)
        val key = data?.identity?.key
        val def = key?.let { itemDefs.get(it) }
        return IngredientCandidate(
            material = stack.type,
            count = stack.amount,
            itemKey = key,
            categories = def?.categories ?: emptySet(),
            quality = data?.quality,
            upgradeLevel = data?.upgradeLevel,
        )
    }

    /** Adds [stack] to [player]'s inventory, dropping any overflow at their feet so nothing is lost. */
    private fun giveOrDrop(player: Player, stack: ItemStack) {
        val leftover = player.inventory.addItem(stack)
        for (rem in leftover.values) player.world.dropItemNaturally(player.location, rem)
    }

    /**
     * Returns any items still escrowed in [player]'s open station menu, called from CraftingModule's
     * PlayerQuitEvent handler. RamCore's [MenuSession] invalidates on quit WITHOUT running its close
     * handler, so this is the return-on-quit path that stops a mid-craft disconnect from eating the
     * player's deposited inputs.
     */
    fun returnHeldOnQuit(player: Player) {
        openByPlayer[player.uniqueId]?.let { onSessionClosed(it) }
    }

    /** Drains a session's escrowed items back to the player and stops tracking it. Idempotent. */
    fun onSessionClosed(session: MenuSession) {
        val player = session.player()
        for (stack in StationMenu.drainHeld(session)) giveOrDrop(player, stack)
        openByPlayer.remove(player.uniqueId, session)
    }

    private fun track(session: MenuSession) {
        openByPlayer[session.player().uniqueId] = session
    }

    companion object {
        /** Fixed seed for the live preview so its quality does not flicker between re-renders. */
        private const val PREVIEW_SEED: Long = 0L
    }
}
