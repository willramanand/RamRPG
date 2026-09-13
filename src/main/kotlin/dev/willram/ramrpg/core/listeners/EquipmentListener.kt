/** Marks stats dirty + applies attribute baseValues on equipment / world / join events. */
package dev.willram.ramrpg.core.listeners

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import dev.willram.ramcore.event.Events
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.StatPerLevelReward
import dev.willram.ramrpg.api.stats.StatDirtyReason
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.builtin.identity.RamStats
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/**
 * Recalculates [player]'s stats and writes the derived Bukkit attributes (max health, attack speed,
 * walk speed). A free function so the level-up handler and `/skills level` can re-apply attributes
 * from just a [StatService], without holding an [EquipmentListener] instance (WP-1.7b: the private
 * UI/listener holders are no longer fields on the plugin).
 */
fun applyPlayerAttributes(stats: StatService, player: Player, setHealth: Boolean = false) {
    val snap = stats.recalculateNow(player)
    val health = snap[RamStats.HEALTH].coerceAtLeast(1.0)
    val swing = snap[RamStats.ATTACK_SPEED]
    val speed = snap[RamStats.SPEED]

    player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = health
    player.getAttribute(Attribute.ATTACK_SPEED)?.baseValue = 4.0 + swing
    player.walkSpeed = if (speed > 0.0) {
        (0.2f + (0.8f * (speed.toFloat() / (100f + speed.toFloat()))))
    } else 0.2f
    if (setHealth) player.health = health
    player.healthScale = 20.0
}

/**
 * WP-2.1c: the RamCore-backed services [requirementStateFor] needs to build a live [ItemRequirementState]
 * for the wearer -- see `docs/design/2.1c-inert-items.md`.
 *
 * These are threaded into the four item-based `StatProvider`s (`EquipmentStatProvider`,
 * `EnchantmentStatProvider`, `ReforgeStatProvider`, `SocketStatProvider` in `core/services`) as an
 * OPTIONAL trailing constructor parameter rather than this file wiring them in directly, because every
 * one of those providers' construction call sites lives in `RamRPG.kt`, which this WP's file scope (rule
 * 8/B5) forbids touching. Until the orchestrator passes a real instance at those call sites (a one-line
 * change per provider, at merge time), [requirementStateFor] runs fail-closed -- see its KDoc.
 */
class ItemRequirementServices(
    val skillRegistry: SkillRegistry,
    val skillService: SkillService,
    val stats: StatService,
)

/**
 * WP-2.1c: builds the [ItemRequirementState] for [player] that every item-based `StatProvider` (and
 * lore rendering) evaluates a candidate item's [dev.willram.ramrpg.api.items.ItemRequirement]s against.
 * See `docs/design/2.1c-inert-items.md` for the full "no-self-satisfaction" rationale; summary:
 *
 * - [ItemRequirementState.skillLevel] reads [SkillService.level] directly -- skill levels are entirely
 *   item-independent (WP-2.1a), so this is always safe, with or without [services].
 * - [ItemRequirementState.statValue] returns the stat's registered `defaultBase` plus each skill's
 *   per-level [StatPerLevelReward] contribution (the same math `SkillStatProvider` applies) --
 *   DELIBERATELY excluding every item-based `StatProvider`'s contribution, including the candidate
 *   item's own. An item's own stats (or any other equipped item's stats) can therefore never help satisfy
 *   a `StatThreshold` requirement -- closing the self-satisfaction exploit this WP calls out. This also
 *   means the check never calls back into [StatService] (no snapshot/recalculate), so it is safe to run
 *   from inside a `StatProvider.provideStats` without recursing into the provider list.
 *
 * When [services] is `null` (not yet wired at the caller's construction site -- see [ItemRequirementServices]),
 * every check fails closed: `skillLevel` reads `0` and `statValue` reads `0.0`, so a non-trivial
 * requirement is UNMET rather than silently vanishing -- mirroring [dev.willram.ramrpg.api.items.ItemRequirement.PerkOwned]'s
 * fail-closed stub from WP-2.1a.
 */
fun requirementStateFor(player: Player, services: ItemRequirementServices?): ItemRequirementState =
    object : ItemRequirementState {
        override fun skillLevel(skill: SkillKey): Int = services?.skillService?.level(player, skill) ?: 0

        override fun statValue(stat: StatKey): Double {
            val svc = services ?: return 0.0
            var value = svc.stats.definition(stat)?.defaultBase ?: 0.0
            for (def in svc.skillRegistry.all()) {
                val lvl = svc.skillService.level(player, def.key) - 1
                if (lvl <= 0) continue
                for (r in def.rewards) {
                    if (r is StatPerLevelReward && r.stat == stat) value += lvl * r.amountPerLevel
                }
            }
            return value
        }
    }

/**
 * WP-2.1c: equipment-change events already recalculate stats ([applyAttributes] -> [StatService.recalculateNow])
 * on every event this listener handles; that recalculation is what "refreshes" inert-ness, since the four
 * item-based `StatProvider`s consult [requirementStateFor] (via the shared
 * [dev.willram.ramrpg.api.items.isInert] check) fresh on every call -- there is no separate inert cache to
 * invalidate and no new listener hook needed (rule 2). This class's own constructor is unchanged; see
 * [ItemRequirementServices]'s KDoc for the RamRPG.kt wiring this still needs at merge time.
 */
class EquipmentListener(private val stats: StatService) {

    fun register(consumer: TerminableConsumer) {
        consumer.bind(Events.subscribe(PlayerJoinEvent::class.java, EventPriority.HIGH).handler { e ->
            stats.markDirty(e.player, StatDirtyReason.JOIN)
            Schedulers.run(e.player) { applyAttributes(e.player, setHealth = true) }
        })
        consumer.bind(Events.subscribe(PlayerArmorChangeEvent::class.java).handler { e ->
            stats.markDirty(e.player, StatDirtyReason.EQUIPMENT_CHANGED)
            Schedulers.run(e.player) { applyAttributes(e.player) }
        })
        consumer.bind(Events.subscribe(PlayerSwapHandItemsEvent::class.java).handler { e ->
            stats.markDirty(e.player, StatDirtyReason.EQUIPMENT_CHANGED)
            Schedulers.run(e.player) { applyAttributes(e.player) }
        })
        consumer.bind(Events.subscribe(PlayerItemBreakEvent::class.java).handler { e ->
            stats.markDirty(e.player, StatDirtyReason.EQUIPMENT_CHANGED)
            Schedulers.run(e.player) { applyAttributes(e.player) }
        })
        consumer.bind(Events.subscribe(PlayerChangedWorldEvent::class.java).handler { e ->
            if (e.player.gameMode.isInvulnerable) return@handler
            stats.markDirty(e.player, StatDirtyReason.WORLD_CHANGED)
            Schedulers.run(e.player) { applyAttributes(e.player) }
        })
        consumer.bind(Events.subscribe(PlayerItemHeldEvent::class.java).handler { e ->
            stats.markDirty(e.player, StatDirtyReason.EQUIPMENT_CHANGED)
            Schedulers.run(e.player) { applyAttributes(e.player) }
        })
        consumer.bind(Events.subscribe(InventoryClickEvent::class.java).handler { e ->
            val p = e.whoClicked as? org.bukkit.entity.Player ?: return@handler
            stats.markDirty(p, StatDirtyReason.EQUIPMENT_CHANGED)
            Schedulers.run(p) { applyAttributes(p) }
        })
    }

    fun applyAttributes(player: Player, setHealth: Boolean = false) =
        applyPlayerAttributes(stats, player, setHealth)
}
