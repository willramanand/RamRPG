/** Default GemDefinitions: ruby, sapphire, emerald, amethyst, topaz, opal. */
package dev.willram.ramrpg.builtin.sockets

import dev.willram.ramrpg.api.sockets.GemDefinition
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.builtin.identity.RamStats
import net.kyori.adventure.text.Component

object BuiltinGems {
    fun registerAll(reg: GemRegistry) {
        val owner = "ramrpg-builtin"
        reg.register(owner, GemDefinition(GemKey.of("ramrpg", "ruby"), Component.text("Ruby"), mapOf(RamStats.STRENGTH to 5.0)))
        reg.register(owner, GemDefinition(GemKey.of("ramrpg", "sapphire"), Component.text("Sapphire"), mapOf(RamStats.WISDOM to 10.0)))
        reg.register(owner, GemDefinition(GemKey.of("ramrpg", "emerald"), Component.text("Emerald"), mapOf(RamStats.DEFENSE to 5.0)))
        reg.register(owner, GemDefinition(GemKey.of("ramrpg", "amethyst"), Component.text("Amethyst"), mapOf(RamStats.CRIT_DAMAGE to 8.0)))
        reg.register(owner, GemDefinition(GemKey.of("ramrpg", "topaz"), Component.text("Topaz"), mapOf(RamStats.CRIT_CHANCE to 4.0)))
        reg.register(owner, GemDefinition(GemKey.of("ramrpg", "opal"), Component.text("Opal"), mapOf(RamStats.HEALTH to 15.0)))
    }
}
