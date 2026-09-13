package dev.willram.ramrpg

import dev.willram.ramrpg.api.abilities.formatCooldown
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class CooldownRemainingFormatTest {

    @Test
    fun `zero and negative render as 0s`() {
        assertEquals("0s", formatCooldown(Duration.ZERO))
        assertEquals("0s", formatCooldown(Duration.ofSeconds(-5)))
    }

    @Test
    fun `sub-ten-seconds keeps one decimal`() {
        assertEquals("3.2s", formatCooldown(Duration.ofMillis(3200)))
        assertEquals("0.5s", formatCooldown(Duration.ofMillis(500)))
    }

    @Test
    fun `ten-plus seconds round to whole seconds`() {
        assertEquals("45s", formatCooldown(Duration.ofSeconds(45)))
        assertEquals("12s", formatCooldown(Duration.ofMillis(12400)))
    }

    @Test
    fun `a minute or more shows minutes and seconds`() {
        assertEquals("1m 5s", formatCooldown(Duration.ofSeconds(65)))
        assertEquals("2m 0s", formatCooldown(Duration.ofSeconds(120)))
    }
}
