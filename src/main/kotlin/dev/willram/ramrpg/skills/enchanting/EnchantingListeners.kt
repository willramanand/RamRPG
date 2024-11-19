package dev.willram.ramrpg.skills.enchanting

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent
import dev.willram.ramcore.event.Events
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.events.CustomEnchantEvent
import dev.willram.ramrpg.events.XpGainEvent
import dev.willram.ramrpg.skills.Skill
import dev.willram.ramrpg.source.enchanting.EnchantingSource
import org.bukkit.enchantments.EnchantmentTarget
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Player
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.inventory.ItemStack


class EnchantingListeners {

    companion object {
        fun register() {
            Events.subscribe(EnchantItemEvent::class.java)
                .handler { e ->
                    if (e.isCancelled) return@handler
                    if (e.getEnchanter() == null) return@handler
                    if (e.getItem() == null) return@handler

                    val enchantedItem: ItemStack = e.item
                    val player: Player = e.enchanter

                    if (player.isInvulnerable) return@handler

                    val source = if (EnchantmentTarget.ARMOR.includes(enchantedItem)) {
                        EnchantingSource.ARMOR_PER_LEVEL
                    } else if (EnchantmentTarget.TOOL.includes(enchantedItem)) {
                        EnchantingSource.TOOL_PER_LEVEL
                    } else if (EnchantmentTarget.WEAPON.includes(enchantedItem)) {
                        EnchantingSource.WEAPON_PER_LEVEL
                    } else {
                        EnchantingSource.BOOK_PER_LEVEL
                    }
                    RamRPG.get().leveler.addXp(player, Skill.ENCHANTING, RamRPG.get().sources.getXp(source))
                }

            Events.subscribe(CustomEnchantEvent::class.java)
                .filter { e -> !e.isCancelled }
                .handler { e ->
                    val enchantedItem: ItemStack = e.item
                    val player: Player = e.enchanter

                    if (player.isInvulnerable) return@handler

                    val source = if (EnchantmentTarget.ARMOR.includes(enchantedItem)) {
                        EnchantingSource.ARMOR_PER_LEVEL
                    } else if (EnchantmentTarget.TOOL.includes(enchantedItem)) {
                        EnchantingSource.TOOL_PER_LEVEL
                    } else if (EnchantmentTarget.WEAPON.includes(enchantedItem)) {
                        EnchantingSource.WEAPON_PER_LEVEL
                    } else {
                        EnchantingSource.BOOK_PER_LEVEL
                    }
                    val multiplier = e.enchantment.xpCosts(e.lvl, e.item) / 10.0
                    RamRPG.get().leveler.addXp(player, Skill.ENCHANTING, multiplier * RamRPG.get().sources.getXp(source))
                }

            Events.subscribe(PlayerPickupExperienceEvent::class.java)
                .filter { e -> !e.isCancelled }
                .handler { e ->
                    val player: Player = e.player
                    val data = RamRPG.get().players[player.uniqueId]
                    val xpIncrease = 1 + ((data.skillsLvl[Skill.ENCHANTING]!! * 5.0) / 100.0)
                    val orb = e.experienceOrb
                    val newExperience = (orb.experience * xpIncrease).toInt()
                    orb.experience = newExperience
                }

            Events.subscribe(XpGainEvent::class.java)
                .filter { e -> !e.isCancelled }
                .handler { e ->
                    val player: Player = e.player
                    val data = RamRPG.get().players[player.uniqueId]
                    val xpIncrease = 1 + ((data.skillsLvl[Skill.ENCHANTING]!! * 5.0) / 100.0)
                    val newXp = e.amount * xpIncrease
                    e.amount = newXp
                }
        }
    }
}