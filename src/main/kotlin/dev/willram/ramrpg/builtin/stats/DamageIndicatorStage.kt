/** Spawns short-lived TextDisplay popups showing finalDamage at the victim. */
package dev.willram.ramrpg.builtin.stats

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramrpg.api.combat.DamageContext
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.combat.DamageStage
import dev.willram.ramrpg.api.combat.DamageTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Transformation
import org.joml.Vector3f
import kotlin.random.Random

class DamageIndicatorStage : DamageStage {
    override val key: ContentId = ContentId.of("ramrpg", "damage_indicator")
    override val priority: Int = DamagePriority.INDICATOR

    override fun apply(ctx: DamageContext) {
        if (ctx.cancelled) return
        if (ctx.finalDamage < 0.5) return
        val world = ctx.victim.world
        val loc = ctx.victim.location.clone().add(
            Random.nextDouble(-0.5, 0.5),
            ctx.victim.height * 0.7 + Random.nextDouble(0.0, 0.4),
            Random.nextDouble(-0.5, 0.5),
        )
        val color: TextColor = when {
            DamageTag.CRIT in ctx.tags -> NamedTextColor.YELLOW
            DamageTag.TRUE in ctx.tags -> NamedTextColor.WHITE
            else -> NamedTextColor.RED
        }
        Schedulers.run(loc) {
            val display = world.spawn(loc, TextDisplay::class.java) { d ->
                d.text(Component.text("${"%.0f".format(ctx.finalDamage)}", color))
                d.billboard = org.bukkit.entity.Display.Billboard.CENTER
                d.isSeeThrough = true
                d.backgroundColor = org.bukkit.Color.fromARGB(0, 0, 0, 0)
                d.transformation = Transformation(
                    Vector3f(0f, 0f, 0f),
                    org.joml.Quaternionf(),
                    Vector3f(0.6f, 0.6f, 0.6f),
                    org.joml.Quaternionf(),
                )
            }
            Schedulers.runLater(loc, { display.remove() }, 16L)
        }
    }
}
