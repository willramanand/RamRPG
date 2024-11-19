package dev.willram.ramrpg.skills.fishing

import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.fishing.FishingSource
import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.inventory.ItemStack
import kotlin.random.Random


class FishingListeners {
    companion object {
        private val rareItems: List<Material> = listOf(
            Material.COAL,
            Material.IRON_NUGGET,
            Material.IRON_INGOT,
            Material.GOLD_NUGGET,
            Material.GOLD_INGOT,
            Material.DIAMOND,
            Material.EMERALD,
            Material.LAPIS_LAZULI,
            Material.NETHERITE_SCRAP
        )
        private val epicItems: List<Material> = listOf(
            Material.COAL_BLOCK,
            Material.IRON_BLOCK,
            Material.GOLD_BLOCK,
            Material.DIAMOND_BLOCK,
            Material.LAPIS_BLOCK,
            Material.NETHERITE_INGOT,
            Material.NETHERITE_BLOCK
        )


        fun register() {
            Events.subscribe(PlayerFishEvent::class.java)
                .filter { e -> e.state == PlayerFishEvent.State.CAUGHT_FISH }
                .filter { e -> e.caught is Item }
                .handler { e ->
                    val item = randomizedItem(e.player, e.caught as Item)
                    if (item != e.caught) {
                        e.hook.hookedEntity = item
                    }

                    val player: Player = e.player

                    if (player.isInvulnerable) return@handler
                    val source: FishingSource = if (epicItems.contains(item.itemStack.type)) {
                        FishingSource.EPIC
                    } else if (rareItems.contains(item.itemStack.type)) {
                        FishingSource.RARE
                    } else {
                        FishingSource.valueOf(item.itemStack)
                    }
                    RamRPG.get().leveler.addXp(player, Skill.FISHING, RamRPG.get().sources.getXp(source))
                }
        }

        private fun randomizedItem(player: Player, item: Item): Item {
            val rng: Double = Random.nextDouble(0.0, 101.0)
            val playerData = RamRPG.get().players[player.uniqueId]

            val chance = 5.0 + playerData.skillsLvl[Skill.FISHING]!! / 2.0

            if (rng <= chance) {
                val roll: Int = Random.nextInt(0, 31)

                if (roll in 0..19) {
                    item.itemStack = ItemStack(rareItems[Random.nextInt(rareItems.size)])
                } else {
                    item.itemStack = ItemStack(epicItems[Random.nextInt(epicItems.size)])
                }

                return item
            }

            return item
        }
    }
}