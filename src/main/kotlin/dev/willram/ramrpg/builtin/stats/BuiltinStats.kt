/** Default RamRPG StatDefinitions registered at boot. */
package dev.willram.ramrpg.builtin.stats

import dev.willram.ramrpg.api.stats.StatDefinition
import dev.willram.ramrpg.api.stats.StatFormat
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.builtin.identity.RamStats
import net.kyori.adventure.text.Component
import dev.willram.ramrpg.api.identity.StatKey
import net.kyori.adventure.text.format.NamedTextColor

object BuiltinStats {
    private fun trKey(k: StatKey) = "ramrpg.stat.${k.id.value()}"
    private fun name(k: StatKey, fallback: String) =
        Component.translatable(trKey(k), Component.text(fallback))

    fun registerAll(stats: StatService) {
        fun reg(k: StatKey, fallback: String, sym: String?, color: NamedTextColor, base: Double = 0.0, min: Double? = null, max: Double? = null, fmt: StatFormat = StatFormat.WHOLE) {
            stats.registerDefinition(StatDefinition(k, name(k, fallback), sym, color, defaultBase = base, min = min, max = max, format = fmt, translationKey = trKey(k)))
        }
        reg(RamStats.DAMAGE, "Damage", "❁", NamedTextColor.RED, base = 5.0)
        reg(RamStats.STRENGTH, "Strength", "❁", NamedTextColor.DARK_RED)
        reg(RamStats.HEALTH, "Health", "❤", NamedTextColor.RED, base = 100.0, min = 1.0)
        reg(RamStats.HEALTH_REGEN, "Health Regen", "❣", NamedTextColor.RED)
        reg(RamStats.DEFENSE, "Defense", "❈", NamedTextColor.GREEN, min = 0.0)
        reg(RamStats.TRUE_DEFENSE, "True Defense", "❂", NamedTextColor.WHITE, min = 0.0)
        reg(RamStats.SPEED, "Speed", "✦", NamedTextColor.WHITE, min = 0.0)
        reg(RamStats.ATTACK_SPEED, "Attack Speed", "⚔", NamedTextColor.YELLOW, min = 0.0)
        reg(RamStats.CRIT_CHANCE, "Crit Chance", "☣", NamedTextColor.BLUE, min = 0.0, max = 100.0, fmt = StatFormat.PERCENT)
        reg(RamStats.CRIT_DAMAGE, "Crit Damage", "☠", NamedTextColor.BLUE, min = 0.0, fmt = StatFormat.PERCENT)
        reg(RamStats.FEROCITY, "Ferocity", "⫽", NamedTextColor.RED, min = 0.0)
        reg(RamStats.LIFESTEAL, "Lifesteal", "♥", NamedTextColor.DARK_RED, min = 0.0, fmt = StatFormat.PERCENT)
        reg(RamStats.FORTUNE, "Fortune", "☘", NamedTextColor.GOLD, min = 0.0)
        reg(RamStats.WISDOM, "Wisdom", "✎", NamedTextColor.AQUA, base = 100.0, min = 0.0)
    }
}
