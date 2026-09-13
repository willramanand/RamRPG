/**
 * WP-1.5b: the id -> [EffectAction] factory registry, plus the two ways a resolved action runs.
 *
 * DESIGN RULE (the whole point of this WP): builtin action ids are DATA in this registry, never a
 * `when` branch. A new action becomes referenceable from HOCON by [register]ing an id + factory (see
 * [dev.willram.ramrpg.core.effects.BuiltinEffectActions]); no existing code changes. [create] therefore
 * NEVER switches on the id -- it looks the id up and, if absent, raises a
 * [ContentDeserializeException] that NAMES the id, so [dev.willram.ramrpg.core.config.specs.EffectSpec]
 * (and, through it, the SpecLoader) aggregates the failure instead of crashing.
 *
 * A "factory" reads the effect's `params` node once, at parse time, and returns an [EffectAction] to run
 * later on the server. The registry itself touches no Bukkit; the actions it produces do (they execute
 * on the correct thread via [TriggeredEffectDispatcher]).
 *
 * This file also owns the single concrete [EffectContext] ([RpgEffectContext]) shared by both execution
 * surfaces, and the bridge that lets ONE action vocabulary drive BOTH surfaces:
 *  - a [dev.willram.ramrpg.api.effects.TriggeredEffect] runs its action when its trigger fires
 *    ([TriggeredEffectDispatcher] builds the context from the Bukkit event), and
 *  - a [dev.willram.ramrpg.api.effects.DamagePipelineEffect] (`type=damage_stage`) runs its action as a
 *    [DamageStageHook] mid-pipeline ([damageHook] wraps the action, building the context from the live
 *    [DamageContext]).
 * Keeping both on the same [EffectActionRegistry] is why this WP ships three registries, not four.
 */
package dev.willram.ramrpg.core.effects

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.effects.DamageStageHook
import dev.willram.ramrpg.api.effects.EffectAction
import dev.willram.ramrpg.api.effects.EffectContext
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.spongepowered.configurate.ConfigurationNode

/**
 * Builds one [EffectAction] from an effect's `params` node. Pure: it must not touch Bukkit at
 * construction -- only the returned [EffectAction] executes on the server.
 */
fun interface EffectActionFactory {
    fun create(params: ConfigurationNode): EffectAction
}

class EffectActionRegistry {

    private val factories = LinkedHashMap<ContentId, EffectActionFactory>()

    /** Registers a builtin/plugin action id. A duplicate id is a wiring bug, so it fails fast. */
    fun register(id: ContentId, factory: EffectActionFactory) {
        require(factories.put(id, factory) == null) { "duplicate effect action id '$id'" }
    }

    fun contains(id: ContentId): Boolean = id in factories

    /** The registered ids, insertion-ordered, for diagnostics and error messages. */
    fun ids(): Set<ContentId> = java.util.Collections.unmodifiableSet(LinkedHashSet(factories.keys))

    /**
     * Resolves [id] + [params] into a runnable [EffectAction]. Throws [ContentDeserializeException]
     * NAMING the id when it is not registered, so the caller aggregates it as a [ValidationError]
     * rather than crashing the load.
     */
    fun create(id: ContentId, params: ConfigurationNode): EffectAction {
        val factory = factories[id]
            ?: throw ContentDeserializeException(
                "unknown effect action '$id'; registered actions: ${factories.keys.joinToString(", ").ifEmpty { "<none>" }}",
            )
        return factory.create(params)
    }

    /**
     * Resolves [id] + [params] into a [DamageStageHook] for a `type=damage_stage` effect: the same
     * [EffectAction] the trigger path uses, wrapped so it runs mid-pipeline with an [EffectContext]
     * built from the live [DamageContext]. Throws (naming the id) when unknown, exactly like [create].
     */
    fun damageHook(id: ContentId, params: ConfigurationNode): DamageStageHook {
        val action = create(id, params)
        return object : DamageStageHook {
            override fun apply(ctx: DamageContext) = action.execute(RpgEffectContext.forDamage(ctx))
        }
    }
}

/**
 * The one concrete [EffectContext] in RamRPG, shared by the dispatcher and the damage-stage bridge so
 * there is a single place entities/level/damage are wired into the execution context. Touches Bukkit
 * (it holds `LivingEntity`/`Player`), which is why it lives in `core/effects` and never in the pure
 * `core/config/specs/` layer.
 *
 * `level` defaults to 1 in this WP: no system yet tells the dispatcher which holder (enchant/skill/perk)
 * a triggered effect came from, so there is no level to inject. Effect-holder scoping is later-WP work;
 * the field is here so those WPs can supply it without an API change.
 */
internal class RpgEffectContext(
    override val source: LivingEntity?,
    override val target: LivingEntity?,
    override val player: Player?,
    override val level: Int,
    override val damage: DamageContext?,
    override val extra: MutableMap<String, Any>,
) : EffectContext {

    companion object {
        /** Context for a `damage_stage` action: source/target/player read off the [DamageContext],
         *  and its metadata bag reused as [extra] so an action's scratch data flows with the damage. */
        fun forDamage(ctx: DamageContext, level: Int = 1): RpgEffectContext = RpgEffectContext(
            source = ctx.attacker,
            target = ctx.victim,
            player = ctx.attacker as? Player,
            level = level,
            damage = ctx,
            extra = ctx.metadata,
        )

        /** Context for a triggered action, built by [TriggeredEffectDispatcher] on the event thread. */
        fun forTrigger(
            source: LivingEntity? = null,
            target: LivingEntity? = null,
            player: Player? = null,
            level: Int = 1,
        ): RpgEffectContext = RpgEffectContext(source, target, player, level, damage = null, extra = HashMap())
    }
}
