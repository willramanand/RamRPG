/** 3-row inventory GUI of computed stat snapshot; auto-redraws while open. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.menu.Gui
import dev.willram.ramcore.menu.Item
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.scheduler.Task
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.rendering.markGuiIcon
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class StatsGui(player: Player, private val stats: StatService) : Gui(player, 3, "RamRPG Stats") {

    private var refreshTask: Task? = null

    override fun open() {
        super.open()
        refreshTask = Schedulers.forEntity(player).runRepeating({ _: Task ->
            if (!isValid) { refreshTask?.stop(); refreshTask = null; return@runRepeating }
            redraw()
        }, 10L, 10L)
    }

    override fun redraw() {
        clearItems()
        val snap = stats.snapshot(player)
        val defs = stats.definitions().sortedBy { it.key.id.value() }
        for ((idx, def) in defs.withIndex()) {
            if (idx >= 27) break
            val mat = pickIcon(def.key)
            val stack = ItemStack(mat)
            val meta = stack.itemMeta
            meta.displayName(def.displayName.color(def.color))
            val value = def.format.format(snap[def.key])
            meta.lore(listOf(
                Component.text("Value: $value", NamedTextColor.GRAY),
                Component.text("Base: ${def.format.format(def.defaultBase)}", NamedTextColor.DARK_GRAY),
            ))
            stack.itemMeta = meta
            setItem(idx, Item.builder(stack.markGuiIcon()).build())
        }
    }

    override fun clickHandler(event: org.bukkit.event.inventory.InventoryClickEvent): Boolean {
        event.isCancelled = true
        return false
    }

    override fun closeHandler(event: org.bukkit.event.inventory.InventoryCloseEvent) {
        refreshTask?.stop()
        refreshTask = null
    }
    override fun invalidateHandler() {
        refreshTask?.stop()
        refreshTask = null
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
