package dev.willram.ramrpg.skills.mining

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.mining.MiningSource
import dev.willram.ramrpg.utils.BlockUtils
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent


class MiningListeners {

    companion object {
        private val validMats: List<Material> = MiningSource.entries.map { s -> Material.matchMaterial(s.toString())!! }

        fun register() {
            Events.subscribe(BlockBreakEvent::class.java)
                .filter { e -> !e.isCancelled }
                .filter { e -> !BlockUtils.isPlayerPlaced(e.block) }
                .filter { e -> validMats.contains(e.block.type) }
                .handler { e ->
                    //if (BlockUtils.isPlayerPlaced(event.getBlock())) return
                    val player: Player = e.player
                    //if (blockXpGainPlayer(player)) return

                    val block: Block = e.block
                    val source = MiningSource.valueOf(block.type.name)
                    if (source.requiresSilkTouch) {
                        val handItem = player.inventory.itemInMainHand
                        if (!handItem.enchantments.containsKey(Enchantment.SILK_TOUCH)) return@handler
                    }

                    RamRPG.get().leveler.addXp(player, Skill.MINING, RamRPG.get().sources.getXp(source))
                }
        }
    }
}