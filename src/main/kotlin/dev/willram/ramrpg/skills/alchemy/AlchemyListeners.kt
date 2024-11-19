package dev.willram.ramrpg.skills.alchemy

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.metadata.Metadata
import dev.willram.ramcore.metadata.MetadataKey
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.levels.Leveler
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.alchemy.AlchemySource
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.BrewEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType


class AlchemyListeners {
    companion object {
        private val BREWING_STAND_OWNER_KEY = MetadataKey.createUuidKey("brewing_stand_owner")

        fun register() {
            Events.subscribe(BrewEvent::class.java)
                .filter { e ->  Metadata.provideForBlock(e.block).getOrNull(BREWING_STAND_OWNER_KEY) != null }
                .handler { e ->
                    val player = Bukkit.getPlayer(Metadata.provideForBlock(e.block).get(BREWING_STAND_OWNER_KEY).get())
                    if (player == null || !player.isOnline) return@handler
                    if (e.contents.ingredient == null) return@handler
                    if (player.isInvulnerable) return@handler
                    val amountBrewed = e.results.size
                    val source: AlchemySource = when (e.contents.ingredient!!.type) {
                        Material.REDSTONE -> AlchemySource.EXTENDED
                        Material.GLOWSTONE_DUST -> AlchemySource.UPGRADED
                        Material.NETHER_WART -> AlchemySource.AWKWARD
                        Material.GUNPOWDER -> AlchemySource.SPLASH
                        Material.DRAGON_BREATH -> AlchemySource.LINGERING
                        else -> AlchemySource.REGULAR
                    }
                    RamRPG.get().leveler.addXp(player, Skill.ALCHEMY, amountBrewed * RamRPG.get().sources.getXp(source))
                }

            Events.subscribe(BlockPlaceEvent::class.java)
                .filter { e -> !e.isCancelled}
                .filter { e -> e.block.type == Material.BREWING_STAND  }
                .handler { e ->
                    Metadata.provideForBlock(e.block).put(BREWING_STAND_OWNER_KEY, e.player.uniqueId)
                }

            Events.subscribe(BlockBreakEvent::class.java)
                .filter { e -> !e.isCancelled }
                .filter { e -> e.block.type == Material.BREWING_STAND }
                .filter { e ->  Metadata.provideForBlock(e.block).getOrNull(BREWING_STAND_OWNER_KEY) != null }
                .handler { e ->
                    Metadata.provideForBlock(e.block).remove(BREWING_STAND_OWNER_KEY)
                }

            Events.subscribe(InventoryOpenEvent::class.java)
                .filter { e -> !e.isCancelled }
                .filter { e -> e.inventory.type == InventoryType.BREWING }
                .filter { e -> e.inventory.holder != null }
                .filter { e -> e.inventory.location != null }
                .handler { e ->
                    val brewingStand = e.inventory.location?.block ?: return@handler
                    val metadataMap = Metadata.provideForBlock(brewingStand)
                    val hasId = metadataMap.getOrNull(BREWING_STAND_OWNER_KEY)
                    if (hasId == null) {
                        metadataMap.put(BREWING_STAND_OWNER_KEY, e.player.uniqueId)
                    }
                }
        }
    }
}