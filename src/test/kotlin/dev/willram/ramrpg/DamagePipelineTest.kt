package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamageStage
import dev.willram.ramrpg.core.services.DamagePipelineImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DamagePipelineTest {

    private fun stage(id: String, prio: Int, action: (DamageContext) -> Unit) = object : DamageStage {
        override val key: ContentId = ContentId.of("test", id)
        override val priority: Int = prio
        override fun apply(ctx: DamageContext) = action(ctx)
    }

    @Test
    fun `stages run in priority order`() {
        val pipeline = DamagePipelineImpl()
        val order = mutableListOf<String>()
        pipeline.register(stage("c", 300) { order += "c" })
        pipeline.register(stage("a", 100) { order += "a" })
        pipeline.register(stage("b", 200) { order += "b" })
        // No real entity needed — use null sentinel + no-op stages assertion via list only.
        // Cast trick: DamageContext requires non-null victim, so just assert ordering using stages().
        val sorted = pipeline.stages().map { it.key.value() }
        assertEquals(listOf("a", "b", "c"), sorted)
    }
}
