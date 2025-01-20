package dev.willram.ramrpg.ui

import dev.willram.ramcore.item.ItemStackBuilder
import dev.willram.ramcore.menu.Gui
import dev.willram.ramcore.menu.Item
import dev.willram.ramcore.pdc.PDCs
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.persistence.PersistentDataType
import kotlin.math.max


class SkillsMenu(player: Player) : Gui(player, 6, "${player.name}'s Skills") {
    override fun redraw() {
        for (i in 0..<handle.size) {
            this.setItem(i, ItemStackBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name("").build {})
        }

        this.setItem(0, ItemStackBuilder.of(Material.PLAYER_HEAD).name(player.name).transformMeta { meta ->
            val skullMeta = meta as SkullMeta
            skullMeta.owningPlayer = player
        }.build {});
        this.setItem(12, this.getSkillItem(Skill.AGILITY, Material.RABBIT_FOOT, player));
        this.setItem(13, this.getSkillItem(Skill.ALCHEMY, Material.POTION, player));
        this.setItem(14, this.getSkillItem(Skill.COMBAT, Material.DIAMOND_SWORD, player));
        this.setItem(20, this.getSkillItem(Skill.COOKING, Material.COOKED_BEEF, player));
        this.setItem(21, this.getSkillItem(Skill.DEFENSE, Material.SHIELD, player));
        this.setItem(22, this.getSkillItem(Skill.ENCHANTING, Material.ENCHANTING_TABLE, player));
        this.setItem(23, this.getSkillItem(Skill.EXCAVATION, Material.IRON_SHOVEL, player));
        this.setItem(24, this.getSkillItem(Skill.FARMING, Material.STONE_HOE, player));
        this.setItem(30, this.getSkillItem(Skill.FISHING, Material.FISHING_ROD, player));
        this.setItem(31, this.getSkillItem(Skill.MINING, Material.GOLDEN_PICKAXE, player));
        this.setItem(32, this.getSkillItem(Skill.SORCERY, Material.BLAZE_ROD, player));
        this.setItem(40, this.getSkillItem(Skill.WOODCUTTING, Material.NETHERITE_AXE, player));
        this.setItem(45, ItemStackBuilder.of(Material.BARRIER).name("<red>Close").build {
            this.player.closeInventory()
        })
        this.setItem(53, ItemStackBuilder.of(Material.BOOKSHELF).name("<blue>Stats").build {
            this.player.closeInventory()
            val menu = StatsMenu(this.player)
            menu.open()
        });
    }

    override fun clickHandler(e: InventoryClickEvent): Boolean { return false }

    override fun closeHandler(e: InventoryCloseEvent) {}

    override fun invalidateHandler() {}

    private fun getSkillItem(skill: Skill, material: Material, player: Player): Item {
        val data = RamRPG.get().players[player.uniqueId]
        val realSkill = RamRPG.get().skills[skill]!!

        return ItemStackBuilder.of(material)
            .name("<aqua>${realSkill.displayName}")
            .transformMeta { meta ->
            // Begin adding lore
            val lore: MutableList<Component> = ArrayList()
            lore.add(Component.empty())

            // Progress Section
            val lvl: Int = data.skillsLvl[skill]!!
            lore.add(MiniMessage.miniMessage().deserialize("<gold>LVL: <light_purple>$lvl").decoration(TextDecoration.ITALIC, false))
            if (lvl < RamRPG.get().leveler.xpRequirements.getMaxLevel(skill)) {
                lore.add(MiniMessage.miniMessage().deserialize("<gold>[${getProgressBar(data.skillsXp[skill]!!.toInt(), RamRPG.get().leveler.xpRequirements.getXpRequired(skill, lvl + 1).toInt(), 20, '=')}<gold>]")
                    .decoration(TextDecoration.ITALIC, false))
            } else {
                lore.add(MiniMessage.miniMessage().deserialize("<gold>[${getProgressBar(1, 1, 20, '=')}<gold>]")
                    .decoration(TextDecoration.ITALIC, false))

                meta.setEnchantmentGlintOverride(true)
            }
            lore.add(Component.empty())

            // Description Section
            lore.add(MiniMessage.miniMessage().deserialize("<grey>${realSkill.desc}").decoration(TextDecoration.ITALIC, false))
            lore.add(Component.empty())

            // Stats section
            lore.add(MiniMessage.miniMessage().deserialize("<aqua>Stats:").decoration(TextDecoration.ITALIC, false))
            for (stat in skill.stats) {
                val realStat = RamRPG.get().stats[stat]!!
                lore.add(MiniMessage.miniMessage().deserialize("${realStat.prefix} ${realStat.displayName} ${realStat.symbol}").decoration(TextDecoration.ITALIC, false))
            }
            lore.add(Component.empty())

            meta.lore(lore)
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        }.build {}
    }

    private fun getProgressBar(current: Int, max: Int, totalBars: Int, symbol: Char): String {
        val percent = current.toFloat() / max
        val progressBars = (totalBars * percent).toInt()
        var completedBars = "<green>"

        for (i in 0 until progressBars) {
            completedBars += symbol.toString()
        }

        var incompletedBars ="<gray>"
        for (i in 0 until max((totalBars - progressBars).toDouble(), 0.0).toInt()) {
            incompletedBars += symbol.toString()
        }

        return completedBars + incompletedBars
    }
}