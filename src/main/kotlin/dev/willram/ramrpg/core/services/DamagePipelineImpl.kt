/**
 * DamagePipeline implementation. Stages run sorted by priority. Secondary
 * contexts (ferocity re-enqueues) processed bounded to avoid runaway loops.
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamagePipeline
import dev.willram.ramrpg.api.combat.DamageStage
import java.util.concurrent.CopyOnWriteArrayList

class DamagePipelineImpl : DamagePipeline {
    private val stagesList = CopyOnWriteArrayList<DamageStage>()
    private val maxFerocityChain = 8

    override fun register(stage: DamageStage) {
        stagesList.removeAll { it.key == stage.key }
        stagesList.add(stage)
        stagesList.sortBy { it.priority }
    }

    override fun unregister(key: ContentId) {
        stagesList.removeAll { it.key == key }
    }

    override fun stages(): List<DamageStage> = stagesList.toList()

    override fun process(ctx: DamageContext): DamageContext {
        runStages(ctx)
        var depth = 0
        val queue = ArrayDeque(ctx.secondaryQueue)
        while (queue.isNotEmpty() && depth < maxFerocityChain) {
            val sec = queue.removeFirst()
            runStages(sec)
            depth++
        }
        return ctx
    }

    private fun runStages(ctx: DamageContext) {
        for (s in stagesList) {
            if (ctx.cancelled) return
            s.apply(ctx)
        }
    }
}
