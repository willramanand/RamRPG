package dev.willram.ramrpg.skills.mining

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.mining.MiningSource
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
                .handler { e ->
                    if (e.isCancelled) return@handler
                    if (!(validMats.contains(e.block.type))) return@handler
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