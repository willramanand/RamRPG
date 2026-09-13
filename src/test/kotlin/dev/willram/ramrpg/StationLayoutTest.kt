package dev.willram.ramrpg

import dev.willram.ramrpg.core.menus.StationLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-3.1b: [StationLayout] slot math as PURE data -- input/target slots vs the preview/output and craft
 * slots -- with no [dev.willram.ramcore.menu.MenuView] built and no server. The station GUI's runtime
 * click path is blocked off-server (MenuSession calls Bukkit.createInventory), so only the geometry is
 * asserted here; the interactive menu is untested by design (see the WP-3.1b report).
 */
class StationLayoutTest {

    @Test
    fun `the default three-row layout places each role on its own column`() {
        val l = StationLayout(3)
        assertEquals(27, l.size)
        assertEquals(9, l.targetSlot)   // control row (row 1), column 0
        assertEquals(15, l.previewSlot) // control row, column 6
        assertEquals(17, l.confirmSlot) // control row, column 8
        assertEquals(listOf(1, 2, 3, 10, 11, 12, 19, 20, 21), l.ingredientSlots)
    }

    @Test
    fun `roles are disjoint, in-bounds and partition the inventory for every legal row count`() {
        for (rows in 1..6) {
            val l = StationLayout(rows)
            val controls = listOf(l.targetSlot, l.previewSlot, l.confirmSlot)

            assertEquals(3, controls.toSet().size, "target/preview/confirm must be distinct (rows=$rows)")
            for (slot in controls + l.ingredientSlots) {
                assertTrue(slot in 0 until l.size, "slot $slot out of bounds (rows=$rows)")
            }
            assertTrue(l.ingredientSlots.none { it in controls }, "ingredients overlap a control slot (rows=$rows)")
            assertEquals(rows * 3, l.ingredientSlots.size, "three reagent slots per row (rows=$rows)")

            // filler + inputs (ingredients + target) + preview + confirm cover every slot exactly once.
            val all = l.fillerSlots + l.inputSlots + listOf(l.previewSlot, l.confirmSlot)
            assertEquals(all.size, all.toSet().size, "slots must not overlap across roles (rows=$rows)")
            assertEquals((0 until l.size).toList(), all.sorted(), "roles must partition the inventory (rows=$rows)")
        }
    }

    @Test
    fun `input slots are the reagent slots plus the single target slot`() {
        val l = StationLayout(5)
        assertEquals(l.ingredientSlots.toSet() + l.targetSlot, l.inputSlots.toSet())
        assertTrue(l.targetSlot in l.inputSlots)
    }

    @Test
    fun `row counts outside one to six are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { StationLayout(0) }
        assertThrows(IllegalArgumentException::class.java) { StationLayout(7) }
    }
}
