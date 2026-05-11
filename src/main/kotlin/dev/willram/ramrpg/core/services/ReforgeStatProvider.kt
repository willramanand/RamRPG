package dev.willram.ramrpg.core.services

import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatContext
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.api.stats.StatProvider

class ReforgeStatProvider(
    private val items: ItemInstanceService,
    private val defs: ItemDefinitionRegistry,
    private val reforges: ReforgeRegistry,
) : StatProvider {
    override fun provideStats(ctx: StatContext, output: MutableList<StatModifier>) {
        val eq = ctx.player.equipment
        val stacks = listOf(eq.itemInMainHand, eq.itemInOffHand, eq.helmet, eq.chestplate, eq.leggings, eq.boots)
        for (s in stacks) {
            if (s == null) continue
            val data = items.identify(s) ?: continue
            val refKey = data.reforge ?: continue
            val def = defs.get(data.identity.key) ?: continue
            val rDef = reforges.get(refKey) ?: continue
            for ((stat, amt) in rDef.universal) {
                output += StatModifier(stat, amt, ModifierOperation.ADD, ModifierSource(SourceType.REFORGE, rDef.key.id))
            }
            for (cat in def.categories) {
                val perCat = rDef.bonusesByCategory[cat] ?: continue
                for ((stat, amt) in perCat) {
                    output += StatModifier(stat, amt, ModifierOperation.ADD, ModifierSource(SourceType.REFORGE, rDef.key.id))
                }
            }
        }
    }
}
