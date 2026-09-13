/**
 * Presentation helpers built on RamCore's display API instead of hand-spawned entities. The pure
 * [damageIndicatorText] mapping is unit-tested; [damageIndicator] spawns a region-safe TextDisplay
 * through DisplaySpawner and returns an owned, bindable [DisplayHandle].
 */
package dev.willram.ramrpg.core.rendering

import dev.willram.ramcore.display.DisplayHandle
import dev.willram.ramcore.display.DisplaySpawner
import dev.willram.ramcore.display.TextDisplaySpec
import dev.willram.ramcore.promise.Promise
import dev.willram.ramrpg.api.combat.DamageTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay

object RpgPresentation {

    /** Damage popup text, coloured by tag (crit = yellow, true = white, else red). Null below 0.5. */
    fun damageIndicatorText(damage: Double, tags: Set<DamageTag>): Component? {
        if (damage < 0.5) return null
        val color = when {
            DamageTag.CRIT in tags -> NamedTextColor.YELLOW
            DamageTag.TRUE in tags -> NamedTextColor.WHITE
            else -> NamedTextColor.RED
        }
        return Component.text("%.0f".format(damage), color)
    }

    /** Spawns a short-lived, center-billboarded damage popup. Caller binds/closes the handle. */
    fun damageIndicator(location: Location, text: Component): Promise<DisplayHandle<TextDisplay>> {
        val spec = TextDisplaySpec.text(text)
            .seeThrough(true)
            .background(Color.fromARGB(0, 0, 0, 0))
        spec.options().billboard(Display.Billboard.CENTER)
        return DisplaySpawner.spawn(location, spec)
    }
}
