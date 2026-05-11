/** Adds bonus ore drops scaled by RamStats.FORTUNE on player block break. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.builtin.identity.RamStats
import org.bukkit.Material
import org.bukkit.event.block.BlockBreakEvent
import kotlin.random.Random

private val ORE_DROP_MAP = mapOf(
    Material.COAL_ORE to Material.COAL,
    Material.DEEPSLATE_COAL_ORE to Material.COAL,
    Material.IRON_ORE to Material.RAW_IRON,
    Material.DEEPSLATE_IRON_ORE to Material.RAW_IRON,
    Material.GOLD_ORE to Material.RAW_GOLD,
    Material.DEEPSLATE_GOLD_ORE to Material.RAW_GOLD,
    Material.COPPER_ORE to Material.RAW_COPPER,
    Material.DEEPSLATE_COPPER_ORE to Material.RAW_COPPER,
    Material.DIAMOND_ORE to Material.DIAMOND,
    Material.DEEPSLATE_DIAMOND_ORE to Material.DIAMOND,
    Material.EMERALD_ORE to Material.EMERALD,
    Material.DEEPSLATE_EMERALD_ORE to Material.EMERALD,
    Material.LAPIS_ORE to Material.LAPIS_LAZULI,
    Material.DEEPSLATE_LAPIS_ORE to Material.LAPIS_LAZULI,
    Material.REDSTONE_ORE to Material.REDSTONE,
    Material.DEEPSLATE_REDSTONE_ORE to Material.REDSTONE,
    Material.NETHER_QUARTZ_ORE to Material.QUARTZ,
    Material.NETHER_GOLD_ORE to Material.GOLD_NUGGET,
    Material.ANCIENT_DEBRIS to Material.ANCIENT_DEBRIS,
)

class FortuneListener(private val stats: StatService) {
    fun register() {
        Events.subscribe(BlockBreakEvent::class.java).handler { e ->
            if (!e.isDropItems) return@handler
            val drop = ORE_DROP_MAP[e.block.type] ?: return@handler
            val fortune = stats.snapshot(e.player).get(RamStats.FORTUNE)
            if (fortune <= 0) return@handler
            val whole = fortune.toInt()
            val frac = fortune - whole
            var bonus = whole
            if (Random.nextDouble() < frac) bonus += 1
            if (bonus <= 0) return@handler
            val world = e.block.world
            val loc = e.block.location.add(0.5, 0.5, 0.5)
            Schedulers.run(loc) { world.dropItemNaturally(loc, org.bukkit.inventory.ItemStack(drop, bonus)) }
        }
    }
}
