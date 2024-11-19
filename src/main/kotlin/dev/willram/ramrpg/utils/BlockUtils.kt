package dev.willram.ramrpg.utils

import dev.willram.ramcore.metadata.Metadata
import dev.willram.ramcore.metadata.MetadataKey
import org.bukkit.block.Block

class BlockUtils {

    companion object {
        val PLAYER_PLACED_KEY = MetadataKey.createBooleanKey("player-placed")
        fun setPlayerPlaced(block: Block) {
            Metadata.provideForBlock(block).put(PLAYER_PLACED_KEY, true)
        }
        fun isPlayerPlaced(block: Block): Boolean {
            return Metadata.provideForBlock(block).getOrDefault(PLAYER_PLACED_KEY, false)
        }
    }
}