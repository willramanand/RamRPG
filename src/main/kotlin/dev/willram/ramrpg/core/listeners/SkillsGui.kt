/** 3-row inventory GUI listing skills with level + xp progress; auto-redraws while open. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.menu.Gui
import dev.willram.ramcore.menu.Item
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.scheduler.Task
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.rendering.markGuiIcon
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class SkillsGui(
    player: Player,
    private val registry: SkillRegistry,
    private val skills: SkillService,
    private val stats: StatService,
) : Gui(player, 3, "RamRPG Skills") {

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
        val all = registry.all().sortedBy { it.key.id.value() }
        for ((idx, def) in all.withIndex()) {
            if (idx >= 27) break
            val lvl = skills.level(player, def.key)
            val xp = skills.xp(player, def.key)
            val needed = def.xpCurve.xpToReach(lvl).coerceAtLeast(1.0)
            val mat = pickIcon(def.key.id.value())
            val stack = ItemStack(mat)
            val meta = stack.itemMeta
            meta.displayName(def.displayName.color(NamedTextColor.YELLOW))
            meta.lore(listOf(
                Component.text("Level: $lvl", NamedTextColor.GRAY),
                Component.text("XP: ${"%.0f".format(xp)} / ${"%.0f".format(needed)}", NamedTextColor.GRAY),
                Component.text("Max: ${def.maxLevel}", NamedTextColor.DARK_GRAY),
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

    private fun pickIcon(id: String): Material = when (id) {
        "combat" -> Material.IRON_SWORD
        "mining" -> Material.IRON_PICKAXE
        "woodcutting" -> Material.IRON_AXE
        "farming" -> Material.WHEAT
        "fishing" -> Material.FISHING_ROD
        "excavation" -> Material.IRON_SHOVEL
        "foraging" -> Material.OAK_SAPLING
        "enchanting" -> Material.ENCHANTING_TABLE
        "alchemy" -> Material.BREWING_STAND
        "cooking" -> Material.CAKE
        "defense" -> Material.IRON_CHESTPLATE
        "agility" -> Material.LEATHER_BOOTS
        "sorcery" -> Material.NETHER_STAR
        else -> Material.PAPER
    }
}
