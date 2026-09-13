package dev.willram.ramrpg.core.services

import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.items.ItemRequirementState
import dev.willram.ramrpg.api.items.isInert
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatContext
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.api.stats.StatProvider
import dev.willram.ramrpg.core.listeners.ItemRequirementServices
import dev.willram.ramrpg.core.listeners.requirementStateFor

/**
 * WP-2.1c: [data]'s socketed-gem contribution, or empty when [def] is known and [isInert] against
 * [state]. [def] is nullable for the same structural reason as [enchantmentStatsFor] -- see its KDoc.
 *
 * WP-3.3d: this is the SINGLE stat path for socketed gems and its output depends ONLY on which gems
 * occupy [data]'s sockets. The three socket crafting operations (add_socket / insert_gem / remove_gem)
 * merely change that occupancy via [dev.willram.ramrpg.api.crafting.RecipeOutcome.plan] -- cutting an
 * empty slot contributes nothing, inserting a gem makes this method emit exactly that gem's
 * [dev.willram.ramrpg.api.sockets.GemDefinition.statContribution], and removing it withdraws exactly
 * that again. There is no bespoke socket-crafting stat path: an already-set gem produces byte-identical
 * modifiers here regardless of how it came to be set (drop, `/skills give`, or a WP-3.3d craft).
 */
internal fun socketStatsFor(
    def: ItemDefinition?,
    data: ItemInstanceData,
    state: ItemRequirementState,
    gems: GemRegistry,
): List<StatModifier> {
    if (def != null && def.isInert(data, state)) return emptyList()
    val out = ArrayList<StatModifier>()
    for (sock in data.sockets) {
        val gemId = sock.gem ?: continue
        val gemDef = gems.get(GemKey(gemId)) ?: continue
        for ((stat, amt) in gemDef.statContribution) {
            out += StatModifier(stat, amt, ModifierOperation.ADD, ModifierSource(SourceType.SOCKET, gemDef.key.id))
        }
    }
    return out
}

class SocketStatProvider(
    private val items: ItemInstanceService,
    private val gems: GemRegistry,
    /** WP-2.1c: optional -- needed to look up the [ItemDefinition] for the inert check; see [socketStatsFor]. */
    private val defs: ItemDefinitionRegistry? = null,
    /** WP-2.1c: optional -- see [ItemRequirementServices]'s KDoc for why this can't be required yet. */
    private val requirementServices: ItemRequirementServices? = null,
) : StatProvider {
    override fun provideStats(ctx: StatContext, output: MutableList<StatModifier>) {
        val eq = ctx.player.equipment
        val stacks = listOf(eq.itemInMainHand, eq.itemInOffHand, eq.helmet, eq.chestplate, eq.leggings, eq.boots)
        for (s in stacks) {
            if (s.type.isAir) continue
            val data = items.identify(s) ?: continue
            val def = defs?.get(data.identity.key)
            output += socketStatsFor(def, data, requirementStateFor(ctx.player, requirementServices), gems)
        }
    }
}
