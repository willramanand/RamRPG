/** Spawns short-lived TextDisplay popups showing finalDamage at the victim, via RamCore DisplaySpawner. */
package dev.willram.ramrpg.builtin.stats

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.combat.DamageStage
import dev.willram.ramrpg.core.rendering.RpgPresentation
import kotlin.random.Random

class DamageIndicatorStage : DamageStage {
    override val key: ContentId = ContentId.of("ramrpg", "damage_indicator")
    override val priority: Int = DamagePriority.INDICATOR

    override fun apply(ctx: DamageContext) {
        if (ctx.cancelled) return
        val text = RpgPresentation.damageIndicatorText(ctx.finalDamage, ctx.tags) ?: return
        val loc = ctx.victim.location.clone().add(
            Random.nextDouble(-0.5, 0.5),
            ctx.victim.height * 0.7 + Random.nextDouble(0.0, 0.4),
            Random.nextDouble(-0.5, 0.5),
        )
        // Spawn is region-safe; bind the handle to a short-lived removal on the location's context.
        RpgPresentation.damageIndicator(loc, text).toCompletableFuture().thenAccept { handle ->
            Schedulers.runLater(loc, { handle.close() }, 16L)
        }
    }
}
