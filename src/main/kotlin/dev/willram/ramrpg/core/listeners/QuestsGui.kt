/** Paginated quest list on RamCore's menu framework (was legacy menu.Gui). */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.menu.MenuButton
import dev.willram.ramcore.menu.Menus
import dev.willram.ramcore.menu.PaginatedMenu
import dev.willram.ramrpg.api.quests.QuestDefinition
import dev.willram.ramrpg.api.quests.QuestGoal
import dev.willram.ramrpg.api.quests.QuestRegistry
import dev.willram.ramrpg.api.quests.QuestReward
import dev.willram.ramrpg.core.rendering.markGuiIcon
import dev.willram.ramrpg.core.services.QuestService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack

/**
 * The quest list is a [PaginatedMenu]; page math is delegated to RamCore (unit-tested in
 * QuestsGuiPaginationTest). Quests are sorted incomplete-first. Right-click abandons a quest. (The old
 * filter tabs / category row / abandon-confirm were dropped in the migration — in-house, simpler UX.)
 */
object QuestsGui {

    internal val ITEM_SLOTS = (0..44).toList()
    private const val PREV_SLOT = 45
    private const val NEXT_SLOT = 53

    fun build(player: Player, registry: QuestRegistry, service: QuestService): PaginatedMenu<QuestDefinition> {
        val done = service.completedBy(player)
        val quests = registry.all()
            .sortedWith(compareBy({ it.key in done }, { it.category }, { it.key.id.value() }))
        val builder = Menus.paginated<QuestDefinition>(
            Component.translatable("ramrpg.quest.gui.title"), 6,
        ) { quest, _ -> questButton(player, service, quest, quest.key in done) }
            .entries(quests)
            .slots(*ITEM_SLOTS.toIntArray())
            .previous(PREV_SLOT, navButton("<-"))
            .next(NEXT_SLOT, navButton("->"))
        if (quests.isEmpty()) {
            builder.decorate { view ->
                val empty = ItemStack(Material.BARRIER)
                empty.editMeta { it.displayName(Component.translatable("ramrpg.quest.gui.empty", NamedTextColor.GRAY)) }
                view.button(22, MenuButton.of(empty.markGuiIcon()))
            }
        }
        return builder.build()
    }

    fun open(player: Player, registry: QuestRegistry, service: QuestService) {
        build(player, registry, service).open(player)
    }

    private fun navButton(label: String): MenuButton {
        val stack = ItemStack(Material.ARROW)
        stack.editMeta { it.displayName(Component.text(label, NamedTextColor.YELLOW)) }
        return MenuButton.of(stack.markGuiIcon())
    }

    private fun questButton(player: Player, service: QuestService, q: QuestDefinition, isDone: Boolean): MenuButton {
        val mat = if (isDone) Material.MAP else when (q.goal) {
            is QuestGoal.KillEntityProfile -> Material.IRON_SWORD
            is QuestGoal.BreakBlocks -> Material.IRON_PICKAXE
            is QuestGoal.GainSkillXp -> Material.EXPERIENCE_BOTTLE
        }
        val stack = ItemStack(mat)
        stack.editMeta { meta ->
            meta.displayName(q.displayName.color(if (isDone) NamedTextColor.DARK_GRAY else NamedTextColor.YELLOW))
            val lore = ArrayList<Component>()
            lore += q.description.color(NamedTextColor.GRAY)
            val status = if (isDone) "Complete" else "${service.progressOf(player, q.key)} / ${q.goal.target}"
            lore += Component.text("Progress: $status", if (isDone) NamedTextColor.GREEN else NamedTextColor.AQUA)
            if (q.daily) lore += Component.text("[Daily]", NamedTextColor.LIGHT_PURPLE)
            for (r in q.rewards) lore += when (r) {
                is QuestReward.Currency -> Component.text("Reward: ${r.amount.toInt()} coins", NamedTextColor.GOLD)
                is QuestReward.Xp -> Component.text("Reward: ${r.amount.toInt()} ${r.skill.id.value()} xp", NamedTextColor.GOLD)
            }
            lore += Component.text("Right-click to abandon", NamedTextColor.DARK_GRAY)
            meta.lore(lore)
        }
        return MenuButton.builder(stack.markGuiIcon())
            .on(ClickType.RIGHT, Runnable { service.abandon(player, q.key) })
            .build()
    }
}
