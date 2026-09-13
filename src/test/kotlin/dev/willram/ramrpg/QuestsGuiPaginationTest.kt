package dev.willram.ramrpg

import dev.willram.ramcore.menu.Menus
import dev.willram.ramrpg.core.listeners.QuestsGui
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure page math for QuestsGui's PaginatedMenu using its real slot config. Does not construct a
 * MenuSession (that calls Bukkit.createInventory) and never invokes the button factory (the factory
 * builds ItemStacks, which need a live server); page math only slices the entry list.
 */
class QuestsGuiPaginationTest {

    private fun menu(entryCount: Int) =
        Menus.paginated<Int>(Component.text("Quests"), 6) { _, _ -> error("factory must not run for page math") }
            .entries((1..entryCount).toList())
            .slots(*QuestsGui.ITEM_SLOTS.toIntArray())
            .build()

    @Test
    fun `page size is the configured item-slot count`() {
        assertEquals(45, menu(100).pageSize())
    }

    @Test
    fun `page count and page contents follow the entry list`() {
        val m = menu(100)
        assertEquals(3, m.pageCount(), "ceil(100 / 45)")
        assertEquals(45, m.entriesForPage(0).size)
        assertEquals(45, m.entriesForPage(1).size)
        assertEquals(10, m.entriesForPage(2).size, "remainder on the last page")
    }

    @Test
    fun `clampPage keeps the page in range`() {
        val m = menu(100)
        assertEquals(2, m.clampPage(9))
        assertEquals(0, m.clampPage(-3))
    }

    @Test
    fun `an empty quest list is a single empty page`() {
        assertEquals(1, menu(0).pageCount())
        assertEquals(0, menu(0).entriesForPage(0).size)
    }
}
