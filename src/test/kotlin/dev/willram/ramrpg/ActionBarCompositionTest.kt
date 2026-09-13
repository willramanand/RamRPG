package dev.willram.ramrpg

import dev.willram.ramrpg.api.stats.StatFormat
import dev.willram.ramrpg.core.listeners.composeActionBar
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionBarCompositionTest {

    @Test
    fun `composes the health, defense and mana segments`() {
        val component = composeActionBar(
            hp = 100.0, maxHp = 100.0, def = 50.0, mana = 30.0, maxMana = 60.0,
            hpFmt = StatFormat.WHOLE, defFmt = StatFormat.WHOLE, manaFmt = StatFormat.WHOLE,
        )
        val plain = PlainTextComponentSerializer.plainText().serialize(component)
        assertTrue(plain.contains("❤"), plain)
        assertTrue(plain.contains("❈"), plain)
        assertTrue(plain.contains("✎"), plain)
        assertTrue(plain.contains("100/100"), plain)
        assertTrue(plain.contains("50"), plain)
        assertTrue(plain.contains("30/60"), plain)
    }
}
