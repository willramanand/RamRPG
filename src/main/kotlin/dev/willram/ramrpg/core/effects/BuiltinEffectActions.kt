/**
 * WP-1.5b: the single place that fills the three effect registries with builtin ids. Registration here
 * is the ENTIRE mechanism by which a builtin becomes referenceable from HOCON -- there is no `when` over
 * these ids anywhere; adding one is one more [register] call in this file. A reviewer can confirm the
 * design rule holds by grepping: the only knowledge of `ramrpg:heal`, `ramrpg:sneaking`, `ramrpg:ores`,
 * etc. is the data lines below.
 *
 * This is the one effect file (besides the dispatcher) that touches Bukkit: the actions heal/ignite/
 * message live entities, the conditions read live state, and the matchers test `Material`. They execute
 * on the server on the correct thread (via [TriggeredEffectDispatcher] / the damage pipeline), so the
 * Bukkit contact is legitimate here -- unlike the pure `core/config/specs/` layer.
 *
 * The set is deliberately SMALL and generic: enough to prove "content is data" end-to-end, but it does
 * NOT pre-build later systems (no potion/buff/perk actions -- those are their own WPs' content).
 */
package dev.willram.ramrpg.core.effects

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.effects.BlockMatchers
import dev.willram.ramrpg.api.effects.Condition
import dev.willram.ramrpg.api.effects.EffectAction
import dev.willram.ramrpg.core.platform.PlatformScheduler
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import java.util.concurrent.ThreadLocalRandom

object BuiltinEffectActions {

    private const val NS = "ramrpg"
    private fun id(v: String): ContentId = ContentId.of(NS, v)
    private val MM = MiniMessage.miniMessage()

    /** Registers every builtin id into all three registries. Called from ContentModule before load. */
    fun registerAll(
        actions: EffectActionRegistry,
        conditions: EffectConditionRegistry,
        matchers: BlockMatcherRegistry,
        platform: PlatformScheduler,
    ) {
        registerActions(actions, platform)
        registerConditions(conditions)
        registerMatchers(matchers)
    }

    // ---- Actions (id -> EffectAction factory) ------------------------------------------------------

    fun registerActions(reg: EffectActionRegistry, platform: PlatformScheduler) {
        // ramrpg:heal { amount = <double> } -- heal the acting entity, capped at its max health.
        reg.register(id("heal")) { params ->
            val amount = params.node("amount").getDouble(1.0)
            EffectAction { ctx ->
                val entity = ctx.player ?: ctx.source ?: return@EffectAction
                val max = entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: entity.health
                entity.health = (entity.health + amount).coerceAtMost(max)
            }
        }

        // ramrpg:message { text = "<green>..." } -- MiniMessage line to the acting player.
        reg.register(id("message")) { params ->
            val raw = params.node("text").string
                ?: throw ContentDeserializeException("effect action 'ramrpg:message' requires 'text'")
            val line = MM.deserialize(raw)
            EffectAction { ctx -> ctx.player?.sendMessage(line) }
        }

        // ramrpg:ignite { ticks = <int> } -- set the target on fire (never shortens an existing burn).
        // The target is not always in the region the trigger dispatched on (on_hit runs on the
        // attacker; the target is the victim), so the mutation is re-dispatched onto the target's own
        // entity context -- Folia-safe regardless of which trigger fires this action.
        reg.register(id("ignite")) { params ->
            val ticks = params.node("ticks").getInt(40)
            EffectAction { ctx ->
                val target = ctx.target ?: return@EffectAction
                platform.runForEntity(target) { target.fireTicks = maxOf(target.fireTicks, ticks) }
            }
        }
    }

    // ---- Conditions (id -> Condition factory) ------------------------------------------------------

    fun registerConditions(reg: EffectConditionRegistry) {
        reg.register(id("always")) { Condition { true } }
        reg.register(id("sneaking")) { Condition { ctx -> ctx.player?.isSneaking == true } }
        reg.register(id("has_target")) { Condition { ctx -> ctx.target != null } }
        // ramrpg:chance { chance = 0.0..1.0 } -- probabilistic gate; 1.0 (default) always passes.
        reg.register(id("chance")) { params ->
            val chance = params.node("chance").getDouble(1.0)
            Condition { ThreadLocalRandom.current().nextDouble() < chance }
        }
    }

    // ---- Block matchers (id -> BlockMatcher) -------------------------------------------------------

    fun registerMatchers(reg: BlockMatcherRegistry) {
        reg.register(id("any"), BlockMatchers.ANY)
        reg.register(id("ores"), BlockMatchers.ofMaterials(*ORES))
        reg.register(id("logs"), BlockMatchers.ofMaterials(*LOGS))
        reg.register(id("crops"), BlockMatchers.ofMaterials(*CROPS))
    }

    private val ORES: Array<Material> = arrayOf(
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS,
    )

    private val LOGS: Array<Material> = arrayOf(
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
        Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
        Material.CRIMSON_STEM, Material.WARPED_STEM,
    )

    private val CROPS: Array<Material> = arrayOf(
        Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART,
    )
}
