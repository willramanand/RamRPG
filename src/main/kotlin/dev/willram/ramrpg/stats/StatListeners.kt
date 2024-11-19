package dev.willram.ramrpg.stats

import dev.willram.ramcore.event.Events
import dev.willram.ramcore.random.VariableAmount
import dev.willram.ramcore.utils.ItemUtils
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.enchants.Enchantments
import dev.willram.ramrpg.entity.EntityStats
import dev.willram.ramrpg.enums.CriticalType
import dev.willram.ramrpg.events.CriticalStrikeEvent
import org.bukkit.Material
import org.bukkit.entity.Damageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.inventory.ItemStack
import kotlin.random.Random


class StatListeners {

    companion object {

        private val nonConvertMats: List<Material> = listOf(
            Material.ANCIENT_DEBRIS,
            Material.OAK_LOG,
            Material.OAK_WOOD,
            Material.SPRUCE_LOG,
            Material.SPRUCE_WOOD,
            Material.BIRCH_LOG,
            Material.BIRCH_WOOD,
            Material.ACACIA_LOG,
            Material.ACACIA_WOOD,
            Material.DARK_OAK_LOG,
            Material.DARK_OAK_WOOD,
            Material.MANGROVE_LOG,
            Material.CRIMSON_STEM,
            Material.CRIMSON_HYPHAE,
            Material.WARPED_STEM,
            Material.WARPED_HYPHAE,
            Material.STRIPPED_OAK_LOG,
            Material.STRIPPED_OAK_WOOD,
            Material.STRIPPED_SPRUCE_LOG,
            Material.STRIPPED_SPRUCE_WOOD,
            Material.STRIPPED_BIRCH_LOG,
            Material.STRIPPED_BIRCH_WOOD,
            Material.STRIPPED_ACACIA_LOG,
            Material.STRIPPED_ACACIA_WOOD,
            Material.STRIPPED_DARK_OAK_LOG,
            Material.STRIPPED_DARK_OAK_WOOD,
            Material.STRIPPED_CRIMSON_STEM,
            Material.STRIPPED_CRIMSON_HYPHAE,
            Material.STRIPPED_WARPED_STEM,
            Material.STRIPPED_WARPED_HYPHAE,
            Material.STRIPPED_MANGROVE_LOG,
            Material.MUDDY_MANGROVE_ROOTS,
            Material.MANGROVE_ROOTS,
            Material.WHEAT,
            Material.POTATOES,
            Material.CARROTS,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.PUMPKIN,
            Material.SUGAR_CANE,
            Material.BAMBOO,
            Material.CACTUS,
            Material.BROWN_MUSHROOM,
            Material.RED_MUSHROOM,
            Material.KELP,
            Material.SEA_PICKLE,
            Material.GLOW_BERRIES,
        )

        private val convertMats:  List<Material> = listOf(
            Material.COAL_ORE,
            Material.IRON_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.REDSTONE_ORE,
            Material.GOLD_ORE,
            Material.LAPIS_ORE,
            Material.DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.NETHER_GOLD_ORE,
            Material.COPPER_ORE,
            Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_COPPER_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.DEEPSLATE_REDSTONE_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.DEEPSLATE_LAPIS_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.COCOA,
            Material.MELON,
        )

        fun register() {
            // Base Damage, Strength, Critical Chance/Damage, Ferocity
            Events.subscribe(EntityDamageByEntityEvent::class.java, EventPriority.LOWEST)
                .filter { e -> !e.isCancelled }
                .filter { e -> e.cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK || e.cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK || e.cause == EntityDamageEvent.DamageCause.PROJECTILE }
                .handler { e ->
                    val damager: LivingEntity = if (e.damager is LivingEntity) {
                        e.damager as LivingEntity
                    } else if (e.damager is Projectile && (e.damager as Projectile).shooter is LivingEntity) {
                        (e.damager as Projectile).shooter as LivingEntity
                    } else return@handler

                    val totalDamage: Double
                    if (damager is Player) {
                        val data = RamRPG.get().players[damager.uniqueId]!!
                        val mainHand = damager.inventory.itemInMainHand
                        var baseDamage = (1 + data.statPoints[Stat.DAMAGE]!!)
                        var lifesteal = 0.0

                        if (mainHand != null && mainHand.hasItemMeta()) {
                            if (Enchantments.hasEnchant(mainHand, Enchantments.SHARPNESS)) {
                                val lvl = Enchantments.getEnchantmentLevel(mainHand, Enchantments.SHARPNESS)
                                val multiplier = 1.0 + ((10.0 * lvl) / 100.0)
                                baseDamage *= multiplier
                            }

                            if (Enchantments.hasEnchant(mainHand, Enchantments.LIFESTEAL)) {
                                val lvl = Enchantments.getEnchantmentLevel(mainHand, Enchantments.LIFESTEAL)
                                lifesteal = (Enchantments.LIFESTEAL.handleStats(lvl) / 100.0)
                            }
                        }

                        val strengthMultiplier = (1 + (data.statPoints[Stat.STRENGTH]!! / 100.0))
                        val currentAttackPower = damager.getCooledAttackStrength(0.0f)
                        val chance = VariableAmount.range(0.0, 1.0)
                        val critChance = data.statPoints[Stat.CRIT_CHANCE]!! / 100.0
                        var critLvl = 0
                        val splitCrit = critChance.toString().split('.')
                        val fullCritLvl = splitCrit[0].toInt()
                        val remainder = ("0." + splitCrit[1]).toDouble()

                        critLvl = fullCritLvl
                        if (critLvl < 3) {
                            val chanceCalc = chance.amount
                            if (remainder >= chanceCalc) {
                                critLvl++
                            }
                        }
                        val criticalType = CriticalType.getByLevel(critLvl)
                        val critDamage = (1 + ((data.statPoints[Stat.CRIT_DAMAGE]!! / 100.0) * critLvl))

                        totalDamage = if (criticalType == CriticalType.NONE) {
                            baseDamage * strengthMultiplier
                        } else {
                            val event = CriticalStrikeEvent(e.damager as Player, criticalType)
                            Events.call(event)
                            baseDamage * strengthMultiplier * critDamage
                        }

                        var ferocityActivated = false
                        if (e.entity is Damageable) {
                            val ferocityChance = data.statPoints[Stat.FEROCITY]!! / 100.0

                            if (ferocityChance >= chance.amount) {
                                ferocityActivated = true
                                val damageable = e.entity as Damageable
                                damageable.damage(totalDamage)
                            }
                        }

                        if (lifesteal > 0.0) {
                            var regen = totalDamage * lifesteal
                            if (ferocityActivated) {
                                regen *= 2
                            }
                            val event = EntityRegainHealthEvent(e.damager, regen, EntityRegainHealthEvent.RegainReason.CUSTOM)
                            Events.call(event)
                            if (!event.isCancelled) {
                                (e.damager as LivingEntity).heal(regen)
                            }
                        }
                    } else {
                        totalDamage = try {
                            EntityStats.valueOf(damager.type.toString()).damage
                        } catch (e: Exception) {
                            2.0
                        }
                    }

                    e.damage = totalDamage
                }

            // Defense Listener
            Events.subscribe(EntityDamageByEntityEvent::class.java, EventPriority.HIGH)
                .filter { e -> !e.isCancelled }
                .filter { e -> e.entity is LivingEntity }
                .handler { e ->
                    var defense: Double
                    if (e.entity is Player) {
                        val player = e.entity as Player
                        val data = RamRPG.get().players.get(e.entity.uniqueId)
                        defense = data.statPoints[Stat.DEFENSE] ?: 0.0

                        // Only check armor
                        val equippedItems: List<ItemStack> = listOf(
                            player.equipment.helmet,
                            player.equipment.chestplate,
                            player.equipment.boots,
                            player.equipment.leggings
                        )

                        // Handles Blast Protection
                        if (e.cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || e.cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                            for (equippedItem in equippedItems) {
                                if (equippedItem == null) continue
                                if (!Enchantments.hasEnchant(equippedItem, Enchantments.BLAST_PROTECTION)) continue
                                val enchantLvl = Enchantments.getEnchantmentLevel(equippedItem, Enchantments.BLAST_PROTECTION)
                                val additionalDefense = Enchantments.BLAST_PROTECTION.handleStats(enchantLvl)
                                defense += additionalDefense
                            }
                        }

                        // Handles Projectile Protection
                        if (e.cause == EntityDamageEvent.DamageCause.PROJECTILE) {
                            for (equippedItem in equippedItems) {
                                if (equippedItem == null) continue
                                if (!Enchantments.hasEnchant(equippedItem, Enchantments.PROJECTILE_PROTECTION)) continue
                                val enchantLvl = Enchantments.getEnchantmentLevel(equippedItem, Enchantments.PROJECTILE_PROTECTION)
                                val additionalDefense = Enchantments.PROJECTILE_PROTECTION.handleStats(enchantLvl)
                                defense += additionalDefense
                            }
                        }

                    } else {
                        defense = try {
                            EntityStats.valueOf(e.entity.type.toString()).defense
                        } catch (e: Exception) {
                            0.0
                        }
                    }

                    val calculateMitigation = defense / (defense + 100)
                    val newDamage = e.finalDamage * (1 - calculateMitigation)

                    e.damage = newDamage
                }

            Events.subscribe(EntityDamageEvent::class.java)
                .filter { e -> !e.isCancelled }
                .filter { e -> e.entity is Player }
                .filter { e -> e.cause == EntityDamageEvent.DamageCause.FIRE || e.cause == EntityDamageEvent.DamageCause.FIRE || e.cause == EntityDamageEvent.DamageCause.LAVA }
                .handler { e ->
                    val player = e.entity as Player
                    val data = RamRPG.get().players[player.uniqueId]
                    var trueDefense = data.statPoints[Stat.TRUE_DEFENSE]!!

                    // Only check armor
                    val equippedItems: List<ItemStack> = listOf(
                        player.equipment.helmet,
                        player.equipment.chestplate,
                        player.equipment.boots,
                        player.equipment.leggings
                    )

                    // Handles Fire Protection
                    for (equippedItem in equippedItems) {
                        if (equippedItem == null) continue
                        if (!Enchantments.hasEnchant(equippedItem, Enchantments.FIRE_PROTECTION)) continue
                        val enchantLvl = Enchantments.getEnchantmentLevel(equippedItem, Enchantments.FIRE_PROTECTION)
                        val additionalDefense = Enchantments.FIRE_PROTECTION.handleStats(enchantLvl)
                        trueDefense += additionalDefense
                    }

                    val mitigation = trueDefense / (trueDefense + 100)

                    e.damage = e.finalDamage * (1 - mitigation)
                }

            // Health Regen
            Events.subscribe(EntityRegainHealthEvent::class.java, EventPriority.HIGHEST)
                .filter { e -> e.entity is Player }
                .filter { e -> e.regainReason != EntityRegainHealthEvent.RegainReason.CUSTOM }
                .handler { e ->
                    val player = e.entity as Player
                    val data = RamRPG.get().players[player.uniqueId]
                    val maxHealth = data.statPoints[Stat.HEALTH]!!
                    val regen = data.statPoints[Stat.HEALTH_REGEN]!!
                    e.amount = (1.5 + (maxHealth / 100.0)) * (regen / 100.0)
                }

            Events.subscribe(BlockBreakEvent::class.java)
                .handler { e ->
                    var dropMat: Material = e.block.type
                    if (convertMats.contains(dropMat)) {
                        dropMat = convertOre(e.block.type, e.player)
                    } else if (!(nonConvertMats.contains(e.block.type))) {
                        return@handler
                    }

                    //if (BlockUtils.isPlayerPlaced(event.getBlock())) return

                    val fortuneAdd: Int = fortuneCalc(e.player)

                    val newItem = ItemStack(dropMat)
                    newItem.amount = fortuneAdd
                    if (dropMat == Material.AIR || newItem.amount == 0) return@handler
                    e.player.world.dropItem(e.block.location, newItem)
                }
        }

        private fun fortuneCalc(player: Player): Int {
            val data = RamRPG.get().players[player.uniqueId]

            var fortuneMult = 0
            val decimalSplit = (data.statPoints[Stat.FORTUNE]!! / 100.0).toString().split(".")

            val fullLevel = decimalSplit[0].toDouble()
            val randDouble: Double = Random.nextDouble(0.01, 1.01)
            val remainder = ("0." + decimalSplit[1]).toDouble()

            // Get full 100s
            fortuneMult = (fortuneMult + fullLevel).toInt()
            // < 100% Fortune
            if (remainder >= randDouble) {
                fortuneMult++
            }
            return fortuneMult
        }

        private fun convertOre(inputOre: Material, player: Player): Material {
            if (player.inventory.itemInMainHand != null && ItemUtils.hasSilkTouch(player.inventory.itemInMainHand)) {
                return inputOre
            }
            return when (inputOre) {
                Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE -> Material.COAL
                Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE -> Material.RAW_COPPER
                Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE -> Material.RAW_IRON
                Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE -> Material.REDSTONE
                Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE -> Material.LAPIS_LAZULI
                Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE -> Material.RAW_GOLD
                Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE -> Material.DIAMOND
                Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE -> Material.EMERALD
                Material.NETHER_QUARTZ_ORE -> Material.QUARTZ
                Material.NETHER_GOLD_ORE -> Material.GOLD_NUGGET
                Material.MELON -> Material.MELON_SLICE
                Material.COCOA -> Material.COCOA_BEANS
                else -> Material.AIR
            }
        }
    }
}