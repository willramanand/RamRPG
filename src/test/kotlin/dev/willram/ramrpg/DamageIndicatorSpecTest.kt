package dev.willram.ramrpg

import dev.willram.ramrpg.api.combat.DamageTag
import dev.willram.ramrpg.core.rendering.RpgPresentation
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DamageIndicatorSpecTest {

    private fun text(damage: Double, tags: Set<DamageTag>) =
        RpgPresentation.damageIndicatorText(damage, tags)

    @Test
    fun `crit damage is yellow, true is white, normal is red`() {
        assertEquals(NamedTextColor.YELLOW, text(10.0, setOf(DamageTag.CRIT))!!.color())
        assertEquals(NamedTextColor.WHITE, text(10.0, setOf(DamageTag.TRUE))!!.color())
        assertEquals(NamedTextColor.RED, text(10.0, emptySet())!!.color())
    }

    @Test
    fun `damage below the threshold renders no indicator`() {
        assertNull(text(0.4, emptySet()))
    }

    @Test
    fun `damage is rounded to a whole number`() {
        assertEquals("13", (text(12.7, emptySet()) as TextComponent).content())
        assertEquals("10", (text(10.0, emptySet()) as TextComponent).content())
    }
}
