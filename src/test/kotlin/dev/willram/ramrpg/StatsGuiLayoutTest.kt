package dev.willram.ramrpg

import dev.willram.ramrpg.core.listeners.gridSlots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pure slot layout for the single-page stat/skill grids (no MenuView built). */
class StatsGuiLayoutTest {
    @Test
    fun `entries fill sequential slots from zero`() {
        assertEquals((0..13).toList(), gridSlots(14))
    }

    @Test
    fun `layout caps at the 27-slot page`() {
        assertEquals(27, gridSlots(30).size)
        assertEquals(26, gridSlots(30).last())
    }

    @Test
    fun `no entries means no slots`() {
        assertTrue(gridSlots(0).isEmpty())
    }
}
