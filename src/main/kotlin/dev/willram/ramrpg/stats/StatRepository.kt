package dev.willram.ramrpg.stats

import dev.willram.ramcore.config.Configs
import dev.willram.ramcore.configurate.hocon.HoconConfigurationLoader
import dev.willram.ramcore.data.DataRepository
import dev.willram.ramcore.data.PDCs
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.data.PlayerData
import dev.willram.ramrpg.items.Items
import dev.willram.ramrpg.skills.Skill
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.nio.file.Path

class StatRepository(private val plugin: RamRPG) : DataRepository<Stat, LoadedStat>() {
    override fun setup() {
        for (stat in Stat.entries) {
            val loader = this.file(stat)
            val node = loader.load()
            val defaultStatData = loadDefaultStatInfo(stat)
            val loadedStat = node.get(LoadedStat::class.java)

            if (loadedStat?.displayName?.isEmpty()!! && loadedStat.modifierName.isEmpty()) {
                plugin.log("<yellow>Config file for stat <light_purple>${stat.name} <yellow>is missing or corrupted! Setting defaults.")
                this.add(stat, defaultStatData)
            } else {
                this.add(stat, loadedStat)
            }

            node.set(LoadedStat::class.java, this.get(stat));
            loader.save(node)
        }
    }

    override fun saveAll() {
        for (id in this.registry().keys) {
            val data = this.get(id)
            if (!data.shouldNotSave() || !data.isSaving) {
                data.isSaving = true
                val loader = this.file(id)
                val node = loader.load()
                node.set(LoadedStat::class.java, data);
                loader.save(node);
                data.isSaving = false
            }
        }
    }

    private fun file(stat: Stat): HoconConfigurationLoader {
        return HoconConfigurationLoader.builder()
            .path(Path.of("${plugin.dataFolder}/stats/${stat.name.lowercase()}.conf"))
            .defaultOptions {opts -> opts.serializers {build -> build.registerAll(Configs.typeSerializers())}}
            .build()
    }

    fun allocateStats(playerData: PlayerData) {
        // Add perLvl amounts
        val newStatMap: MutableMap<Stat, Double> = mutableMapOf()
        for (skill in Skill.entries) {
            val skillData = plugin.skills.get(skill)
            for (stat in skillData.stats) {
                val statData = plugin.stats.get(stat)
                val currentPoints = newStatMap.getOrDefault(stat, 0.0)
                var lvl = playerData.skillsLvl[skill]!! - 1
                if (lvl < 0) {
                    lvl = 0
                }
                newStatMap[stat] = currentPoints + (lvl * statData.perLvl)
            }
        }

        // Add base stat amounts
        for (stat in Stat.entries) {
            val statData = plugin.stats.get(stat)
            val currentPoints = newStatMap.getOrDefault(stat, 0.0)
            newStatMap[stat] = currentPoints + statData.base
        }

        handleItemStatModifiers(playerData, newStatMap)

        playerData.statPoints = newStatMap
    }

    private fun handleItemStatModifiers(playerData: PlayerData, newStatMap: MutableMap<Stat, Double>){
        try {
            val player = playerData.player!!
            val equippedItems: List<ItemStack> = listOf(
                player.equipment.itemInMainHand,
                player.equipment.itemInOffHand,
                player.equipment.helmet,
                player.equipment.chestplate,
                player.equipment.boots,
                player.equipment.leggings
            )

            for (stat in Stat.entries) {
                var statAdded = 0.0
                for (item in equippedItems) {
                    if (item == null) continue
                    val itemStats = Items.retrieve(item.type.name)
                    statAdded += itemStats?.stats?.get(stat) ?: 0.0
                }
                val currentValue = newStatMap.getOrDefault(stat, 0.0)
                newStatMap[stat] = currentValue + statAdded
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadDefaultStatInfo(stat: Stat): LoadedStat {
        val statData = LoadedStat()
        statData.displayName = stat.displayName
        statData.modifierName = stat.modifierName
        statData.symbol = stat.symbol
        statData.prefix = stat.prefix
        statData.base = stat.base
        statData.perLvl = stat.perLvl
        return statData
    }
}