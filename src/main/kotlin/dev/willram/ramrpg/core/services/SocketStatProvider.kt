package dev.willram.ramrpg.core.services

import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatContext
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.api.stats.StatProvider

class SocketStatProvider(
    private val items: ItemInstanceService,
    private val gems: GemRegistry,
) : StatProvider {
    override fun provideStats(ctx: StatContext, output: MutableList<StatModifier>) {
        val eq = ctx.player.equipment
        val stacks = listOf(eq.itemInMainHand, eq.itemInOffHand, eq.helmet, eq.chestplate, eq.leggings, eq.boots)
        for (s in stacks) {
            if (s == null) continue
            val data = items.identify(s) ?: continue
            for (sock in data.sockets) {
                val gemId = sock.gem ?: continue
                val def = gems.get(GemKey(gemId)) ?: continue
                for ((stat, amt) in def.statContribution) {
                    output += StatModifier(stat, amt, ModifierOperation.ADD, ModifierSource(SourceType.SOCKET, def.key.id))
                }
            }
        }
    }
}
