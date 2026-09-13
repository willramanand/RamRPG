/** Skill list on a RamCore MenuView (was legacy menu.Gui). Static snapshot, opened on demand. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.menu.MenuButton
import dev.willram.ramcore.menu.MenuView
import dev.willram.ramcore.menu.Menus
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.core.rendering.markGuiIcon
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object SkillsGui {

    fun open(player: Player, registry: SkillRegistry, skills: SkillService) {
        val defs = registry.all().sortedBy { it.key.id.value() }
        val view = MenuView.builder(Component.translatable("ramrpg.skills.gui.title"), 3)
        for (slot in gridSlots(defs.size)) {
            val def = defs[slot]
            val lvl = skills.level(player, def.key)
            val xp = skills.xp(player, def.key)
            val needed = def.xpCurve.xpToReach(lvl).coerceAtLeast(1.0)
            val stack = ItemStack(pickIcon(def.key.id.value()))
            stack.editMeta { meta ->
                meta.displayName(def.displayName.color(NamedTextColor.YELLOW))
                meta.lore(listOf(
                    Component.text("Level: $lvl", NamedTextColor.GRAY),
                    Component.text("XP: ${"%.0f".format(xp)} / ${"%.0f".format(needed)}", NamedTextColor.GRAY),
                    Component.text("Max: ${def.maxLevel}", NamedTextColor.DARK_GRAY),
                ))
            }
            view.button(slot, MenuButton.of(stack.markGuiIcon()))
        }
        Menus.open(player, view.build())
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
