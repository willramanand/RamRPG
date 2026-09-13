/** ActionBar HUD: HP / DEF / Mana every 5 ticks per online player. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramrpg.api.stats.StatFormat
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.builtin.identity.RamStats
import dev.willram.ramrpg.core.platform.Cancellable
import dev.willram.ramrpg.core.platform.PlatformScheduler
import dev.willram.ramrpg.core.storage.PlayerStore
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ActionBarUi(
    private val stats: StatService,
    private val store: PlayerStore,
    private val platform: PlatformScheduler,
) : AutoCloseable {
    private val tasks = ConcurrentHashMap<UUID, Cancellable>()

    fun register(consumer: TerminableConsumer) {
        consumer.bind(Events.subscribe(PlayerJoinEvent::class.java).handler { e -> start(e.player) })
        consumer.bind(Events.subscribe(PlayerQuitEvent::class.java).handler { e -> stop(e.player.uniqueId) })
    }

    private fun start(player: Player) {
        val handle = platform.repeatForEntity(player, 5L) {
            val snap = stats.snapshot(player)
            val data = store.get(player.uniqueId)
            val mana = (data?.currentMana ?: 0.0).coerceAtLeast(0.0)
            val maxMana = snap[RamStats.WISDOM]
            val def = snap[RamStats.DEFENSE]
            val hp = player.health
            val maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: hp
            val hpFmt = stats.definition(RamStats.HEALTH)?.format ?: StatFormat.WHOLE
            val defFmt = stats.definition(RamStats.DEFENSE)?.format ?: StatFormat.WHOLE
            val manaFmt = stats.definition(RamStats.WISDOM)?.format ?: StatFormat.WHOLE
            player.sendActionBar(composeActionBar(hp, maxHp, def, mana, maxMana, hpFmt, defFmt, manaFmt))
        }
        tasks[player.uniqueId] = handle
    }

    private fun stop(id: UUID) { tasks.remove(id)?.cancel() }

    /** Bound to the UiModule; RamCore closes it on disable, cancelling every per-player task. */
    override fun close() {
        for (t in tasks.values) t.cancel()
        tasks.clear()
    }
}

/** HUD line: ❤ hp/max  ❈ def  ✎ mana/max. Pure so it is unit-testable. Slot budget: 3 segments. */
fun composeActionBar(
    hp: Double, maxHp: Double, def: Double, mana: Double, maxMana: Double,
    hpFmt: StatFormat, defFmt: StatFormat, manaFmt: StatFormat,
): Component = Component.text()
    .append(Component.text("❤ ", NamedTextColor.RED))
    .append(Component.text("${hpFmt.format(hp)}/${hpFmt.format(maxHp)}  ", NamedTextColor.RED))
    .append(Component.text("❈ ", NamedTextColor.GREEN))
    .append(Component.text("${defFmt.format(def)}  ", NamedTextColor.GREEN))
    .append(Component.text("✎ ", NamedTextColor.AQUA))
    .append(Component.text("${manaFmt.format(mana)}/${manaFmt.format(maxMana)}", NamedTextColor.AQUA))
    .build()
