/**
 * WP-1.5b: the dispatcher [dev.willram.ramrpg.api.effects.TriggeredEffect] never had. It gives every one
 * of the NINE triggers a real firing path, on the correct Folia thread context.
 *
 * TWO layers, deliberately separated so the routing is testable off-server:
 *
 *  1. [dispatch] -- the PURE core. Given a [TriggerKind] occurrence (plus the parameters that
 *     distinguish `on_interact:<type>`, `on_block_break:<matcher>`, and `custom:<id>`) and an
 *     [EffectContext], it runs every registered effect whose trigger matches AND whose conditions all
 *     pass. No Bukkit, no scheduling -- [TriggeredEffectDispatcherTest] drives all nine kinds through it
 *     directly. (Block-matcher evaluation is injected as [matcherEval] so the pure test never needs a
 *     live `Block`.)
 *
 *  2. [bind] -- the Bukkit/Folia wiring. It subscribes to one event per event-driven trigger and a
 *     repeating task for `tick`, each bound to the module's [TerminableConsumer] (RamCore tears them
 *     down on disable -- rule 4, and the reason this lives in ContentModule, not RamRPG.kt / B5). Each
 *     handler reads what it needs on the event thread, then runs [dispatch] on the RIGHT TaskContext via
 *     [PlatformScheduler] (RamCore `Schedulers`): the acting entity/player's scheduler for entity-scoped
 *     triggers, the player's (owning) region for `tick`. `custom:<id>` has no Bukkit event -- other
 *     systems fire it through [fireCustom].
 *
 * In this WP the dispatcher starts with ZERO registered effects: no system yet activates a holder's
 * effects (enchants-on-equip, buffs, perks, set bonuses are later WPs). The subscriptions are live and
 * correct so those WPs only need to [register] their effects. This is the schema + mechanism, not the
 * content.
 */
package dev.willram.ramrpg.core.effects

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.event.Events
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramrpg.api.effects.BlockMatcher
import dev.willram.ramrpg.api.effects.EffectContext
import dev.willram.ramrpg.api.effects.EffectTrigger
import dev.willram.ramrpg.api.effects.InteractType
import dev.willram.ramrpg.api.effects.TriggeredEffect
import dev.willram.ramrpg.core.platform.PlatformScheduler
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The runtime kind of a trigger occurrence. A stable, Bukkit-free enum so [dispatch] and its test speak
 * the same vocabulary; it mirrors the fixed nine-case [EffectTrigger] hierarchy one-to-one.
 */
enum class TriggerKind { EQUIP, HIT, HURT, KILL, INTERACT, BLOCK_BREAK, TICK, CONSUME, CUSTOM }

class TriggeredEffectDispatcher(private val platform: PlatformScheduler) {

    /** Registered effects. Copy-on-write: events fire on many region threads and iterate this live. */
    private val effects = CopyOnWriteArrayList<TriggeredEffect>()

    fun register(effect: TriggeredEffect) { effects.add(effect) }
    fun registerAll(items: Iterable<TriggeredEffect>) { items.forEach(effects::add) }
    fun unregister(effect: TriggeredEffect): Boolean = effects.remove(effect)
    fun clear() { effects.clear() }
    fun registeredCount(): Int = effects.size

    /**
     * PURE dispatch core. Runs every registered effect that (a) matches [kind], (b) matches the trigger
     * parameter ([interactType] for INTERACT, [customId] for CUSTOM, [matcherEval] for BLOCK_BREAK), and
     * (c) passes all its conditions against [ctx]. Returns how many actions ran. No Bukkit; no threads.
     *
     * [matcherEval] is how a block-break handler tells the pure core whether an effect's [BlockMatcher]
     * accepted the broken block, without this method ever holding a `Block`.
     */
    fun dispatch(
        kind: TriggerKind,
        ctx: EffectContext,
        interactType: InteractType? = null,
        customId: ContentId? = null,
        matcherEval: (BlockMatcher) -> Boolean = { false },
    ): Int {
        var ran = 0
        for (effect in effects) {
            val trigger = effect.trigger
            if (kindOf(trigger) != kind) continue
            val paramMatches = when (trigger) {
                is EffectTrigger.OnInteract -> trigger.type == interactType
                is EffectTrigger.OnBlockBreak -> matcherEval(trigger.matcher)
                is EffectTrigger.Custom -> trigger.key == customId
                else -> true
            }
            if (!paramMatches) continue
            if (effect.conditions.any { !it.test(ctx) }) continue
            effect.action.execute(ctx)
            ran++
        }
        return ran
    }

    /** Fire a `custom:<id>` trigger from another system (abilities/quests, later WPs). */
    fun fireCustom(id: ContentId, ctx: EffectContext): Int =
        dispatch(TriggerKind.CUSTOM, ctx, customId = id)

    /**
     * Wire the nine triggers to Bukkit and bind every subscription/task to [consumer] so RamCore closes
     * them on disable. Each handler dispatches on the acting entity/player's scheduler (or the player's
     * region for `tick`) -- see the per-trigger notes in `docs/design/1.5b-effect-schema.md`.
     */
    fun bind(consumer: TerminableConsumer) {
        // on_equip -> armor change. Subject: the player. (Held-weapon "equip" is PlayerItemHeldEvent;
        // armor is the canonical equip signal and matches StatService's own invalidation trigger.)
        consumer.bind(Events.subscribe(PlayerArmorChangeEvent::class.java).handler { e ->
            val player = e.player
            val ctx = RpgEffectContext.forTrigger(source = player, player = player)
            platform.runForPlayer(player) { dispatch(TriggerKind.EQUIP, ctx) }
        })

        // on_hit -> the wielder deals melee/projectile damage. Subject: the attacker.
        consumer.bind(Events.subscribe(EntityDamageByEntityEvent::class.java).handler { e ->
            val attacker = resolveAttacker(e) ?: return@handler
            val victim = e.entity as? LivingEntity
            val ctx = RpgEffectContext.forTrigger(source = attacker, target = victim, player = attacker as? Player)
            platform.runForEntity(attacker) { dispatch(TriggerKind.HIT, ctx) }
        })

        // on_hurt -> the holder takes damage (any cause). Subject: the victim; source known only for
        // entity-dealt damage.
        consumer.bind(Events.subscribe(EntityDamageEvent::class.java).handler { e ->
            val victim = e.entity as? LivingEntity ?: return@handler
            val attacker = (e as? EntityDamageByEntityEvent)?.let { resolveAttacker(it) }
            val ctx = RpgEffectContext.forTrigger(source = attacker, target = victim, player = victim as? Player)
            platform.runForEntity(victim) { dispatch(TriggerKind.HURT, ctx) }
        })

        // on_kill -> the holder kills something. Subject: the killer (player-only in vanilla).
        consumer.bind(Events.subscribe(EntityDeathEvent::class.java).handler { e ->
            val killer = e.entity.killer ?: return@handler
            val ctx = RpgEffectContext.forTrigger(source = killer, target = e.entity, player = killer)
            platform.runForEntity(killer) { dispatch(TriggerKind.KILL, ctx) }
        })

        // on_interact:<type> -> left/right click, shift-modified. Subject: the player.
        consumer.bind(Events.subscribe(PlayerInteractEvent::class.java).handler { e ->
            val type = interactTypeOf(e) ?: return@handler
            val player = e.player
            val ctx = RpgEffectContext.forTrigger(source = player, player = player)
            platform.runForPlayer(player) { dispatch(TriggerKind.INTERACT, ctx, interactType = type) }
        })

        // on_block_break:<matcher> -> the block and player share a region. Subject: the player.
        consumer.bind(Events.subscribe(BlockBreakEvent::class.java).handler { e ->
            val player = e.player
            val block = e.block
            val ctx = RpgEffectContext.forTrigger(source = player, player = player)
            platform.runForPlayer(player) {
                dispatch(TriggerKind.BLOCK_BREAK, ctx, matcherEval = { it.matches(block) })
            }
        })

        // on_consume -> finished eating/drinking. Subject: the player.
        consumer.bind(Events.subscribe(PlayerItemConsumeEvent::class.java).handler { e ->
            val player = e.player
            val ctx = RpgEffectContext.forTrigger(source = player, player = player)
            platform.runForPlayer(player) { dispatch(TriggerKind.CONSUME, ctx) }
        })

        // tick -> a global 1s heartbeat that fans out to each online player's own (owning) region
        // scheduler, so a tick action runs Folia-safe on its player. Skipped entirely while no tick
        // effect is registered, so it costs nothing in this schema-only WP.
        val tickHandle = platform.repeatGlobal(TICK_PERIOD_TICKS) {
            if (effects.none { kindOf(it.trigger) == TriggerKind.TICK }) return@repeatGlobal
            for (player in Bukkit.getOnlinePlayers()) {
                val ctx = RpgEffectContext.forTrigger(source = player, player = player)
                platform.runForPlayer(player) { dispatch(TriggerKind.TICK, ctx) }
            }
        }
        consumer.bind(AutoCloseable { tickHandle.cancel() })
    }

    private fun kindOf(trigger: EffectTrigger): TriggerKind = when (trigger) {
        EffectTrigger.OnEquip -> TriggerKind.EQUIP
        EffectTrigger.OnHit -> TriggerKind.HIT
        EffectTrigger.OnHurt -> TriggerKind.HURT
        EffectTrigger.OnKill -> TriggerKind.KILL
        is EffectTrigger.OnInteract -> TriggerKind.INTERACT
        is EffectTrigger.OnBlockBreak -> TriggerKind.BLOCK_BREAK
        EffectTrigger.Tick -> TriggerKind.TICK
        EffectTrigger.OnConsume -> TriggerKind.CONSUME
        is EffectTrigger.Custom -> TriggerKind.CUSTOM
    }

    /** The attacking [LivingEntity]: the damager itself, or a projectile's living shooter. */
    private fun resolveAttacker(e: EntityDamageByEntityEvent): LivingEntity? = when (val d = e.damager) {
        is LivingEntity -> d
        is Projectile -> d.shooter as? LivingEntity
        else -> null
    }

    /** Map click action + sneak state to the four [InteractType]s; null for PHYSICAL (pressure plates). */
    private fun interactTypeOf(e: PlayerInteractEvent): InteractType? {
        val left = when (e.action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> true
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> false
            else -> return null
        }
        val sneak = e.player.isSneaking
        return when {
            left && sneak -> InteractType.SHIFT_LEFT
            left -> InteractType.LEFT
            sneak -> InteractType.SHIFT_RIGHT
            else -> InteractType.RIGHT
        }
    }

    companion object {
        /** One-second heartbeat for `tick` effects; a whole-second grain is plenty for RPG upkeep. */
        const val TICK_PERIOD_TICKS: Long = 20L
    }
}
