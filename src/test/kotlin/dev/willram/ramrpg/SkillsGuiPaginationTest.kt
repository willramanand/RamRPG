package dev.willram.ramrpg

import dev.willram.ramrpg.core.listeners.gridSlots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The 13 builtin skills fit a single 27-slot page, laid out sequentially. */
class SkillsGuiPaginationTest {
    @Test
    fun `all builtin skills fit one page`() {
        assertEquals((0..12).toList(), gridSlots(13))
    }
}
