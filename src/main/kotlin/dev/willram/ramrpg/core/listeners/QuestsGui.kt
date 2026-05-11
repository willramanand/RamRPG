/** GUI listing quests with progress + reward summary + filter tabs. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.menu.Gui
import dev.willram.ramcore.menu.Item
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.scheduler.Task
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

class QuestsGui(
    player: Player,
    private val registry: QuestRegistry,
    private val service: QuestService,
) : Gui(player, 6, "RamRPG Quests") {

    enum class Filter { ALL, ACTIVE, COMPLETED }

    private var filter: Filter = Filter.ALL
    private var category: String? = null
    private var pendingAbandon: dev.willram.ramrpg.api.quests.QuestKey? = null
    private var pendingAt: Long = 0L
    private var refreshTask: Task? = null

    override fun open() {
        super.open()
        refreshTask = Schedulers.forEntity(player).runRepeating({ _: Task ->
            if (!isValid) { refreshTask?.stop(); refreshTask = null; return@runRepeating }
            redraw()
        }, 20L, 20L)
    }

    override fun redraw() {
        clearItems()
        renderTabs()
        renderCategoryRow()
        renderQuests()
    }

    private fun renderTabs() {
        setItem(0, tab(Filter.ALL, Material.BOOK, "All"))
        setItem(1, tab(Filter.ACTIVE, Material.WRITABLE_BOOK, "Active"))
        setItem(2, tab(Filter.COMPLETED, Material.WRITTEN_BOOK, "Completed"))
    }

    private fun renderCategoryRow() {
        val cats = registry.all().map { it.category }.toSortedSet().toList()
        setItem(8, categoryButton(null, "All Categories"))
        for ((i, cat) in cats.withIndex()) {
            if (i >= 5) break
            setItem(3 + i, categoryButton(cat, cat))
        }
    }

    private fun categoryButton(target: String?, label: String): Item {
        val stack = ItemStack(Material.PAPER)
        val meta = stack.itemMeta
        val active = category == target
        meta.displayName(Component.text(label, if (active) NamedTextColor.GREEN else NamedTextColor.GRAY))
        stack.itemMeta = meta
        return Item.builder(stack.markGuiIcon())
            .bind(Runnable { category = target; redraw() }, ClickType.LEFT, ClickType.RIGHT)
            .build()
    }

    private fun tab(target: Filter, mat: Material, label: String): Item {
        val stack = ItemStack(mat)
        val meta = stack.itemMeta
        val active = filter == target
        meta.displayName(Component.text(label, if (active) NamedTextColor.GREEN else NamedTextColor.GRAY))
        stack.itemMeta = meta
        return Item.builder(stack.markGuiIcon())
            .bind(Runnable { filter = target; redraw() }, ClickType.LEFT, ClickType.RIGHT)
            .build()
    }

    private fun renderQuests() {
        val done = service.completedBy(player)
        val quests = registry.all()
            .filter { q ->
                val isDone = q.key in done
                val byFilter = when (filter) {
                    Filter.ALL -> true
                    Filter.ACTIVE -> !isDone
                    Filter.COMPLETED -> isDone
                }
                val byCat = category == null || q.category == category
                byFilter && byCat
            }
            .sortedWith(compareBy({ it.category }, { it.key.id.value() }))
        var slot = 9
        for (q in quests) {
            if (slot >= 54) break
            setItem(slot, renderQuest(q, q.key in done))
            slot++
        }
    }

    private fun renderQuest(q: QuestDefinition, isDone: Boolean): Item {
        val mat = if (isDone) Material.MAP else when (q.goal) {
            is QuestGoal.KillEntityProfile -> Material.IRON_SWORD
            is QuestGoal.BreakBlocks -> Material.IRON_PICKAXE
            is QuestGoal.GainSkillXp -> Material.EXPERIENCE_BOTTLE
        }
        val stack = ItemStack(mat)
        val meta = stack.itemMeta
        meta.displayName(q.displayName.color(if (isDone) NamedTextColor.DARK_GRAY else NamedTextColor.YELLOW))
        val lore = ArrayList<Component>()
        lore += q.description.color(NamedTextColor.GRAY)
        val progress = service.progressOf(player, q.key)
        val status = if (isDone) "Complete" else "$progress / ${q.goal.target}"
        lore += Component.text("Progress: $status", if (isDone) NamedTextColor.GREEN else NamedTextColor.AQUA)
        if (q.daily) lore += Component.text("[Daily]", NamedTextColor.LIGHT_PURPLE)
        for (r in q.rewards) {
            lore += when (r) {
                is QuestReward.Currency -> Component.text("Reward: ${r.amount.toInt()} coins", NamedTextColor.GOLD)
                is QuestReward.Xp -> Component.text("Reward: ${r.amount.toInt()} ${r.skill.id.value()} xp", NamedTextColor.GOLD)
            }
        }
        val pendingHere = pendingAbandon == q.key && System.currentTimeMillis() - pendingAt < 5000
        lore += Component.text(
            if (pendingHere) "Right-click again to confirm" else "Right-click to abandon",
            if (pendingHere) NamedTextColor.RED else NamedTextColor.DARK_GRAY
        )
        lore += Component.text("Category: ${q.category}", NamedTextColor.DARK_GRAY)
        meta.lore(lore)
        stack.itemMeta = meta
        return Item.builder(stack.markGuiIcon())
            .bind(Runnable {
                val now = System.currentTimeMillis()
                if (pendingAbandon == q.key && now - pendingAt < 5000) {
                    service.abandon(player, q.key)
                    pendingAbandon = null
                } else {
                    pendingAbandon = q.key
                    pendingAt = now
                }
                redraw()
            }, ClickType.RIGHT)
            .build()
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
}
