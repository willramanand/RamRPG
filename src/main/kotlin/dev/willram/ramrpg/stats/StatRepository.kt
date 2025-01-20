package dev.willram.ramrpg.stats

import dev.willram.ramcore.config.Configs
import dev.willram.ramcore.configurate.hocon.HoconConfigurationLoader
import dev.willram.ramcore.data.DataRepository
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.data.PlayerData
import dev.willram.ramrpg.enchants.Enchantments
import dev.willram.ramrpg.items.Items
import dev.willram.ramrpg.skills.Skill
import org.bukkit.inventory.ItemStack
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
            .defaultOptions { opts -> opts.serializers { build -> build.registerAll(Configs.typeSerializers()) } }
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

    private fun handleItemStatModifiers(playerData: PlayerData, newStatMap: MutableMap<Stat, Double>) {
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
                    val itemStats = Items.retrieve(item)
                    statAdded += itemStats?.stats?.get(stat) ?: 0.0
                }
                val currentValue = newStatMap.getOrDefault(stat, 0.0)
                newStatMap[stat] = currentValue + statAdded
            }

            val armorItems = listOf(
                player.equipment.helmet,
                player.equipment.chestplate,
                player.equipment.boots,
                player.equipment.leggings
            )

            // Handle armor enchants
            for (item in armorItems) {
                if (item == null) continue
                val enchants = Enchantments.getEnchants(item)

                // Handle Protection
                if (enchants.containsKey(Enchantments.PROTECTION)) {
                    val statAdded = Enchantments.PROTECTION.handleStats(enchants[Enchantments.PROTECTION]!!)
                    val currentValue = newStatMap.getOrDefault(Stat.DEFENSE, 0.0)
                    newStatMap[Stat.DEFENSE] = currentValue + statAdded
                }

                // Handle Growth
                if (enchants.containsKey(Enchantments.MENDING)) {
                    val statAdded = Enchantments.MENDING.handleStats(enchants[Enchantments.MENDING]!!)
                    val currentValue = newStatMap.getOrDefault(Stat.HEALTH_REGEN, 0.0)
                    newStatMap[Stat.HEALTH_REGEN] = currentValue + statAdded
                }

                // Handle Mending
                if (enchants.containsKey(Enchantments.GROWTH)) {
                    val statAdded = Enchantments.GROWTH.handleStats(enchants[Enchantments.GROWTH]!!)
                    val currentValue = newStatMap.getOrDefault(Stat.HEALTH, 0.0)
                    newStatMap[Stat.HEALTH] = currentValue + statAdded
                }
            }

            val handItems = listOf(
                player.equipment.itemInMainHand,
                player.equipment.itemInOffHand,
            )

            for (item in handItems) {
                if (item == null) continue
                val enchants = Enchantments.getEnchants(item)

                // Handle Critical
                if (enchants.containsKey(Enchantments.CRITICAL)) {
                    val statAdded = Enchantments.CRITICAL.handleStats(enchants[Enchantments.CRITICAL]!!)
                    val currentValue = newStatMap.getOrDefault(Stat.CRIT_DAMAGE, 0.0)
                    newStatMap[Stat.CRIT_DAMAGE] = currentValue + statAdded
                }

                // Handle True Strike
                if (enchants.containsKey(Enchantments.TRUE_STRIKE)) {
                    val statAdded = Enchantments.TRUE_STRIKE.handleStats(enchants[Enchantments.TRUE_STRIKE]!!)
                    val currentValue = newStatMap.getOrDefault(Stat.CRIT_CHANCE, 0.0)
                    newStatMap[Stat.CRIT_CHANCE] = currentValue + statAdded
                }

                // Handle Ferocious
                if (enchants.containsKey(Enchantments.FEROCIOUS)) {
                    val statAdded = Enchantments.FEROCIOUS.handleStats(enchants[Enchantments.FEROCIOUS]!!)
                    val currentValue = newStatMap.getOrDefault(Stat.FEROCITY, 0.0)
                    newStatMap[Stat.FEROCITY] = currentValue + statAdded
                }

                // Handle Fortune
                if (enchants.containsKey(Enchantments.FORTUNE)) {
                    val statAdded = Enchantments.FORTUNE.handleStats(enchants[Enchantments.FORTUNE]!!)
                    val currentValue = newStatMap.getOrDefault(Stat.FORTUNE, 0.0)
                    newStatMap[Stat.FORTUNE] = currentValue + statAdded
                }
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