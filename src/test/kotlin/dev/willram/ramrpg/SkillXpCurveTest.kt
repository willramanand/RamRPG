package dev.willram.ramrpg

import dev.willram.ramrpg.api.skills.XpCurves
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkillXpCurveTest {
    @Test
    fun `polynomial reference values`() {
        val c = XpCurves.polynomial(50.0, 25.0)
        assertEquals(50.0, c.xpToReach(0), 0.0001)
        assertEquals(75.0, c.xpToReach(1), 0.0001)
        assertEquals(150.0, c.xpToReach(2), 0.0001)
        assertEquals(275.0, c.xpToReach(3), 0.0001)
    }

    @Test
    fun `linear reference values`() {
        val c = XpCurves.linear(10.0, 5.0)
        assertEquals(10.0, c.xpToReach(0), 0.0001)
        assertEquals(15.0, c.xpToReach(1), 0.0001)
        assertEquals(60.0, c.xpToReach(10), 0.0001)
    }
}
