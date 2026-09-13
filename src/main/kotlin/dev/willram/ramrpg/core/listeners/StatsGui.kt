/** Stat snapshot on a RamCore MenuView (was legacy menu.Gui). Static snapshot, opened on demand. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.menu.MenuButton
import dev.willram.ramcore.menu.MenuView
import dev.willram.ramcore.menu.Menus
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.rendering.markGuiIcon
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Slots for a single-page 3-row grid: entry i at slot i, capped at 27. Pure (unit-tested). */
internal fun gridSlots(count: Int, cap: Int = 27): List<Int> = (0 until minOf(count, cap)).toList()

object StatsGui {

    fun open(player: Player, stats: StatService) {
        val snap = stats.snapshot(player)
        val defs = stats.definitions().sortedBy { it.key.id.value() }
        val view = MenuView.builder(Component.translatable("ramrpg.stats.gui.title"), 3)
        for (slot in gridSlots(defs.size)) {
            val def = defs[slot]
            val stack = ItemStack(pickIcon(def.key))
            stack.editMeta { meta ->
                meta.displayName(def.displayName.color(def.color))
                meta.lore(listOf(
                    Component.text("Value: ${def.format.format(snap[def.key])}", NamedTextColor.GRAY),
                    Component.text("Base: ${def.format.format(def.defaultBase)}", NamedTextColor.DARK_GRAY),
                ))
            }
            view.button(slot, MenuButton.of(stack.markGuiIcon()))
        }
        Menus.open(player, view.build())
    }

    private fun pickIcon(k: StatKey): Material = when (k.id.value()) {
        "damage", "strength" -> Material.IRON_SWORD
        "health", "health_regen" -> Material.RED_DYE
        "defense" -> Material.IRON_CHESTPLATE
        "true_defense" -> Material.NETHERITE_INGOT
        "speed" -> Material.SUGAR
        "attack_speed" -> Material.GOLDEN_SWORD
        "crit_chance", "crit_damage" -> Material.GOLDEN_APPLE
        "ferocity" -> Material.DIAMOND_AXE
        "lifesteal" -> Material.GHAST_TEAR
        "fortune" -> Material.GOLD_INGOT
        "wisdom" -> Material.LAPIS_LAZULI
        else -> Material.PAPER
    }
}
