/**
 * WP-1.5b: the PURE (no Bukkit) deserializer for an `effects = [...]` bundle. It turns each effect node
 * into a concrete [Effect] -- [StatEffect], [DamagePipelineEffect] or [TriggeredEffect] -- resolving the
 * extensible pieces (action / condition / block-matcher ids) through the three registries handed in.
 *
 * WHY it stays pure: [StatEffect]/[DamagePipelineEffect]/[TriggeredEffect] and their collaborators
 * ([ScalingFormula], [EffectAction], [Condition], [BlockMatcher], [DamageStageHook]) are all referenced
 * by TYPE only here; the one Bukkit-touching construction -- wrapping an action in a [DamageStageHook]
 * with a live [EffectContext] -- is delegated to [EffectActionRegistry.damageHook] in `core/effects`.
 * A grep for `org.bukkit` in `core/config/specs/` must stay empty (DONE criterion).
 *
 * WHAT is a fixed grammar vs. what is data:
 *  - The three `type`s, the nine `trigger` forms, the three scaling shapes, and the damage-stage names
 *    are a CLOSED grammar owned by `api/effects` + `api/combat`; a `when` / lookup-map over them is
 *    correct (only this WP or an api change touches them).
 *  - The action / condition / matcher IDS are OPEN data resolved by registry lookup -- adding one never
 *    edits this file. An unknown id becomes a [ContentDeserializeException] naming it, so it aggregates.
 *
 * HOCON forms (namespaced ids MUST be quoted -- ':' is a HOCON separator):
 * ```
 * effects = [
 *   { type = stat, stat = "ramrpg:strength", op = ADD, amount = { linear = 5.0 } }
 *   { type = damage_stage, stage = ENCHANT_OFFENSE, action = "ramrpg:ignite", params { ticks = 60 } }
 *   { type = triggered, trigger = "on_block_break:ramrpg:ores", conditions = ["ramrpg:sneaking"],
 *     action = "ramrpg:message", params { text = "<gold>Vein!" } }
 * ]
 * ```
 */
package dev.willram.ramrpg.core.config.specs

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.effects.Condition
import dev.willram.ramrpg.api.effects.DamagePipelineEffect
import dev.willram.ramrpg.api.effects.Effect
import dev.willram.ramrpg.api.effects.EffectTrigger
import dev.willram.ramrpg.api.effects.InteractType
import dev.willram.ramrpg.api.effects.Scaling
import dev.willram.ramrpg.api.effects.ScalingFormula
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.effects.TriggeredEffect
import dev.willram.ramrpg.api.identity.EffectKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.core.config.SpecNodes
import dev.willram.ramrpg.core.effects.BlockMatcherRegistry
import dev.willram.ramrpg.core.effects.EffectActionRegistry
import dev.willram.ramrpg.core.effects.EffectConditionRegistry
import org.spongepowered.configurate.ConfigurationNode

object EffectSpec {

    /** The three registries an effect bundle resolves against, bundled so specs take one parameter. */
    class Registries(
        val actions: EffectActionRegistry,
        val conditions: EffectConditionRegistry,
        val matchers: BlockMatcherRegistry,
    )

    /**
     * Parses the effect list at [listKey] into concrete [Effect]s. Absent key -> empty list. A null
     * [reg] means the caller wired no registries (the effect-free load path, e.g. the legacy
     * `RpgContentLoader.load(root)`); it yields an empty list rather than failing. Each effect's
     * [EffectKey] is its explicit `key`, else a stable synthesized `<owner>_fx<index>`.
     */
    fun bundle(node: ConfigurationNode, listKey: String, owner: ContentId, reg: Registries?): List<Effect> {
        if (reg == null) return emptyList()
        val list = node.node(listKey)
        if (list.virtual()) return emptyList()
        return list.childrenList().mapIndexed { index, effectNode ->
            one(effectNode, effectKey(effectNode, owner, index), reg)
        }
    }

    /** Parses ONE effect node into its [Effect] subtype. Throws [ContentDeserializeException] (which the
     *  SpecLoader records as a source-tagged [ValidationError]) on any malformed value or unknown id. */
    fun one(node: ConfigurationNode, key: EffectKey, reg: Registries): Effect {
        return when (val type = SpecNodes.requiredString(node, "type", "effect type").lowercase()) {
            "stat" -> StatEffect(
                key = key,
                stat = StatKey(SpecNodes.requireIdAt(node, "stat")),
                amount = scaling(node.node("amount")),
                operation = SpecNodes.enumOr(node, "op", ModifierOperation.ADD, "stat operation"),
            )
            "damage_stage" -> DamagePipelineEffect(
                key = key,
                priority = stagePriority(node),
                hook = reg.actions.damageHook(SpecNodes.requireIdAt(node, "action"), node.node("params")),
            )
            "triggered" -> TriggeredEffect(
                key = key,
                trigger = trigger(SpecNodes.requiredString(node, "trigger"), reg.matchers),
                conditions = conditions(node, reg.conditions),
                action = reg.actions.create(SpecNodes.requireIdAt(node, "action"), node.node("params")),
            )
            else -> throw ContentDeserializeException(
                "unknown effect type '$type'; expected stat, damage_stage or triggered",
            )
        }
    }

    // ---- Scaling: flat / linear / linear_with_base (reusing the api Scaling factory) ---------------

    private fun scaling(node: ConfigurationNode): ScalingFormula {
        if (node.virtual()) throw ContentDeserializeException("effect type 'stat' requires 'amount'")
        // Scalar shorthand: `amount = 5.0` == `amount = { flat = 5.0 }`.
        if (node.rawScalar() != null) return Scaling.flat(node.getDouble())
        node.node("flat").let { if (!it.virtual()) return Scaling.flat(it.getDouble()) }
        node.node("linear").let { if (!it.virtual()) return Scaling.linear(it.getDouble()) }
        node.node("linear_with_base").let { lwb ->
            if (!lwb.virtual()) {
                return Scaling.linearWithBase(lwb.node("base").getDouble(), lwb.node("per_level").getDouble())
            }
        }
        throw ContentDeserializeException(
            "effect 'amount' must be a number or one of flat / linear / linear_with_base",
        )
    }

    // ---- Damage stage: a named api priority or a raw integer --------------------------------------

    private fun stagePriority(node: ConfigurationNode): Int {
        val raw = SpecNodes.requiredString(node, "stage").uppercase()
        STAGE_PRIORITIES[raw]?.let { return it }
        return raw.toIntOrNull() ?: throw ContentDeserializeException(
            "unknown damage stage '$raw'; use an integer priority or one of ${STAGE_PRIORITIES.keys.joinToString(", ")}",
        )
    }

    // ---- Triggers: the nine forms; on_interact/on_block_break/custom carry an arg after the ':' -----

    private fun trigger(raw: String, matchers: BlockMatcherRegistry): EffectTrigger {
        val trimmed = raw.trim()
        // Split on the FIRST ':' only: the arg of on_block_break/custom is itself a `ns:value` id.
        val colon = trimmed.indexOf(':')
        val prefix = if (colon >= 0) trimmed.substring(0, colon) else trimmed
        val arg = if (colon >= 0) trimmed.substring(colon + 1).trim() else null
        return when (prefix.lowercase()) {
            "on_equip" -> EffectTrigger.OnEquip
            "on_hit" -> EffectTrigger.OnHit
            "on_hurt" -> EffectTrigger.OnHurt
            "on_kill" -> EffectTrigger.OnKill
            "tick" -> EffectTrigger.Tick
            "on_consume" -> EffectTrigger.OnConsume
            "on_interact" -> EffectTrigger.OnInteract(interactArg(arg))
            "on_block_break" -> EffectTrigger.OnBlockBreak(matchers.require(idArg(arg, "on_block_break")))
            "custom" -> EffectTrigger.Custom(idArg(arg, "custom"))
            else -> throw ContentDeserializeException(
                "unknown trigger '$raw'; expected on_equip, on_hit, on_hurt, on_kill, on_interact:<type>, " +
                    "on_block_break:<matcher-id>, tick, on_consume or custom:<content-id>",
            )
        }
    }

    private fun interactArg(arg: String?): InteractType {
        val value = arg ?: throw ContentDeserializeException("on_interact requires a type, e.g. on_interact:RIGHT")
        return try {
            InteractType.valueOf(value.uppercase())
        } catch (unknown: IllegalArgumentException) {
            throw ContentDeserializeException(
                "unknown interact type '$value'; expected LEFT, RIGHT, SHIFT_LEFT or SHIFT_RIGHT",
            )
        }
    }

    private fun idArg(arg: String?, what: String): ContentId {
        val value = arg ?: throw ContentDeserializeException("$what requires a content id, e.g. $what:ramrpg:example")
        return SpecNodes.parseId(value, "$what id")
    }

    // ---- Conditions: each entry is an id string, or a map { id = "...", params { ... } } -----------

    private fun conditions(node: ConfigurationNode, reg: EffectConditionRegistry): List<Condition> {
        val list = node.node("conditions")
        if (list.virtual()) return emptyList()
        return list.childrenList().map { entry ->
            val idNode = entry.node("id")
            val (idRaw, params) = if (!idNode.virtual()) {
                (idNode.getString() ?: throw ContentDeserializeException("condition entry 'id' is blank")) to entry.node("params")
            } else {
                (entry.getString() ?: throw ContentDeserializeException(
                    "condition entry must be an id string or a map with 'id'",
                )) to entry
            }
            reg.create(SpecNodes.parseId(idRaw, "condition id"), params)
        }
    }

    // ---- Effect key: explicit `key`, else a stable synthesized one ---------------------------------

    private fun effectKey(node: ConfigurationNode, owner: ContentId, index: Int): EffectKey {
        val explicit = node.node("key").getString()
        if (!explicit.isNullOrBlank()) return EffectKey(SpecNodes.parseId(explicit, "effect key"))
        return EffectKey(ContentId.of(owner.namespace(), "${owner.value()}_fx$index"))
    }

    private val STAGE_PRIORITIES: Map<String, Int> = linkedMapOf(
        "WEAPON_BASE" to DamagePriority.WEAPON_BASE,
        "STRENGTH" to DamagePriority.STRENGTH,
        "ENCHANT_OFFENSE" to DamagePriority.ENCHANT_OFFENSE,
        "EFFECT_OFFENSE" to DamagePriority.EFFECT_OFFENSE,
        "CRIT_ROLL" to DamagePriority.CRIT_ROLL,
        "ELEMENTAL_BREAKDOWN" to DamagePriority.ELEMENTAL_BREAKDOWN,
        "ABILITY_MOD" to DamagePriority.ABILITY_MOD,
        "ARMOR_MITIGATION" to DamagePriority.ARMOR_MITIGATION,
        "TRUE_DEFENSE" to DamagePriority.TRUE_DEFENSE,
        "ENCHANT_DEFENSE" to DamagePriority.ENCHANT_DEFENSE,
        "SHIELDS" to DamagePriority.SHIELDS,
        "THORNS" to DamagePriority.THORNS,
        "LIFESTEAL" to DamagePriority.LIFESTEAL,
        "FEROCITY" to DamagePriority.FEROCITY,
        "INDICATOR" to DamagePriority.INDICATOR,
        "APPLY" to DamagePriority.APPLY,
    )
}
