//package dev.willram.ramrpg.ability
//
//import dev.willram.ramcore.utils.ItemUtils
//import dev.willram.ramcore.utils.VBlockFace
//import dev.willram.ramrpg.RamRPG
//import dev.willram.ramrpg.data.PlayerData
//import dev.willram.ramrpg.events.AbilityFortuneEvent
//import dev.willram.ramrpg.skills.Skill
//import dev.willram.ramrpg.source.excavation.ExcavationSource
//import dev.willram.ramrpg.source.woodcutting.WoodcuttingSource
//import dev.willram.ramrpg.utils.BlockUtils.Companion.isPlayerPlaced
//import org.bukkit.Bukkit
//import org.bukkit.Material
//import org.bukkit.NamespacedKey
//import org.bukkit.Sound
//import org.bukkit.block.Block
//import org.bukkit.block.BlockFace
//import org.bukkit.block.data.Ageable
//import org.bukkit.enchantments.Enchantment
//import org.bukkit.entity.AbstractArrow
//import org.bukkit.entity.Arrow
//import org.bukkit.entity.Player
//import org.bukkit.event.EventHandler
//import org.bukkit.event.EventPriority
//import org.bukkit.event.Listener
//import org.bukkit.event.block.Action
//import org.bukkit.event.block.BlockBreakEvent
//import org.bukkit.event.player.PlayerInteractEvent
//import org.bukkit.event.player.PlayerItemHeldEvent
//import org.bukkit.inventory.EquipmentSlot
//import org.bukkit.inventory.ItemStack
//import org.bukkit.inventory.meta.Damageable
//import org.bukkit.metadata.FixedMetadataValue
//import org.bukkit.scheduler.BukkitRunnable
//import java.util.*
//
//class ManaAbility(private val plugin: RamRPG) : Listener {
//    private val veinBuffer: MutableList<Block?>
//    private val treeBuffer: MutableList<Block?>
//    private val bowPaused: MutableSet<Player?>
//
//    init {
//        this.veinBuffer = ArrayList<Block?>()
//        this.treeBuffer = ArrayList<Block?>()
//        this.bowPaused = HashSet<Player?>()
//    }
//
//    @EventHandler
//    fun readyAbility(event: PlayerInteractEvent) {
//        if (event.getAction() != Action.RIGHT_CLICK_AIR && (event.getAction() != Action.RIGHT_CLICK_BLOCK)) return
//        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !(event.getPlayer().isSneaking)) return
//        if (event.hand != EquipmentSlot.HAND) return
//
//        val player = event.getPlayer()
//        if (!(validWeapon(player.inventory.itemInMainHand)) && !(player.inventory.itemInMainHand
//                .itemMeta.persistentDataContainer.has(
//                    NamespacedKey(plugin, "vein_miner_applicable")
//                ))
//        ) return
//
//        if (player.getMetadata("readied")[0].asBoolean()) {
//            plugin.actionBar.sendAbilityActionBar(player, "Weapon unreadied!")
//            player.setMetadata("readied", FixedMetadataValue(plugin, false))
//        } else {
//            plugin.actionBar.sendAbilityActionBar(player, "Weapon readied!")
//            player.setMetadata("readied", FixedMetadataValue(plugin, true))
//        }
//    }
//
//    @EventHandler
//    fun cancelReady(event: PlayerItemHeldEvent) {
//        if (event.getPlayer().hasMetadata("readied")) if (event.getPlayer().getMetadata("readied")[0].asBoolean()) {
//            event.getPlayer().setMetadata("readied", FixedMetadataValue(plugin, false))
//            plugin.actionBar.sendAbilityActionBar(event.getPlayer(), "Weapon unreadied!")
//        }
//    }
//
//    @EventHandler
//    fun bowAbility(event: PlayerInteractEvent) {
//        var manaCost = Ability.QUICKSHOT.manaCost
//        val player = event.getPlayer()
//
//        if (!(ItemUtils.isBow(player.inventory.itemInMainHand))) return
//        if (!(event.getAction().isLeftClick)) return
//        if (player.isSneaking) return
//        if (bowPaused.contains(player)) {
//            plugin.actionBar.sendAbilityActionBar(player, "&4On Cooldown!")
//            return
//        }
//
//        val playerData = plugin.players.get(player.uniqueId)
//        if (playerData.skillsLvl[Skill.COMBAT]!! < Ability.QUICKSHOT.unlock) return
//        if (playerData.skillsLvl[Skill.COMBAT]!! >= Ability.QUICKSHOT.upgrade) manaCost /= 2.0
//        if (checkMana(playerData, manaCost)) {
//            playerData.currentMana = playerData.currentMana - manaCost
//        } else {
//            plugin.actionBar.sendAbilityActionBar(player, "&4Not enough mana!")
//            return
//        }
//
//        player.playSound(player.location, Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f)
//
//        val bow = player.inventory.itemInMainHand
//
//        val arrow = player.launchProjectile<Arrow>(Arrow::class.java)
//        arrow.shooter = player
//
//        arrow.damage = calculatePowerDmg(bow, arrow.damage)
//        arrow.fireTicks = checkBowFire(bow)
//        arrow.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
//
//        consumeDurability(player, bow)
//        plugin.actionBar.sendAbilityActionBar(player, "Quickshot activated!")
//        setBowPaused(player, 10)
//    }
//
//    @EventHandler
//    fun hoeAbility(event: PlayerInteractEvent) {
//        var isSuccessful = false
//        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return
//        if (!(ItemUtils.isHoe(event.getPlayer().inventory.itemInMainHand))) return
//
//        val player = event.getPlayer()
//        val playerData = plugin.players.get(player.uniqueId)
//        var manaCost = Ability.DEMETERS_TOUCH.manaCost
//
//        // Ability size 5x5
//        var startBlock = -2
//        var endBlock = 3
//
//        if (playerData.skillsLvl[Skill.FARMING]!! < Ability.DEMETERS_TOUCH.unlock) {
//            return
//        } else if (playerData.skillsLvl[Skill.FARMING]!! >= Ability.DEMETERS_TOUCH.upgrade) {
//            // Ability size 9x9
//            startBlock = -4
//            endBlock = 5
//
//            // Reduced mana cost
//            manaCost /= 2.0
//        }
//
//        var block: Block?
//        for (x in startBlock..<endBlock) {
//            for (z in startBlock..<endBlock) {
//                block = event.clickedBlock!!.location.add(x.toDouble(), 0.0, z.toDouble()).getBlock()
//                if (validCrop(block.type)) {
//                    if (!(checkMana(playerData, manaCost))) {
//                        plugin.actionBar.sendAbilityActionBar(player, "&4Not enough mana!")
//                        return
//                    }
//
//                    val age = block.getBlockData() as Ageable
//                    if (age.getAge() != age.maximumAge) {
//                        block.applyBoneMeal(BlockFace.UP)
//                        consumeDurability(player, player.getInventory().getItemInMainHand())
//                        playerData.currentMana = playerData.currentMana - manaCost
//                        isSuccessful = true
//                    }
//                }
//            }
//        }
//        if (isSuccessful) plugin.actionBar.sendAbilityActionBar(player, "Demeter's Touch activated!")
//    }
//
//    @EventHandler(priority = EventPriority.HIGHEST)
//    fun pickAbility(event: BlockBreakEvent) {
//        if (!(ItemUtils.isPick(event.player.inventory.itemInMainHand))) return
//        if (!(validVein(event.getBlock().type))) return
//        if (!(event.getPlayer().getMetadata("readied").get(0).asBoolean())) return
//
//        val player = event.player
//        val pick = player.inventory.itemInMainHand
//        val skillPlayer: SkillPlayer = plugin.getPlayerManager().getPlayerData(player)
//
//        val type = event.getBlock().type
//
//        var maxVeinSize = 10
//        var manaCost = Ability.VEIN_MINER.manaCost
//
//        if (skillPlayer.getSkillLevel(Skills.MINING) < Ability.VEIN_MINER.unlock) {
//            return
//        } else if (skillPlayer.getSkillLevel(Skills.MINING) >= Ability.VEIN_MINER.upgrade) {
//            // Vein mine 20 blocks
//            maxVeinSize *= 2
//
//            // Reduced mana cost
//            manaCost /= 2.0
//        }
//
//        val veinBlocks: MutableSet<Block> = HashSet<Block>()
//        veinBlocks.add(event.getBlock())
//
//        while (veinBlocks.size < maxVeinSize) {
//            val trackedBlocks = veinBlocks.iterator()
//            while (trackedBlocks.hasNext() && veinBlocks.size + this.veinBuffer.size <= maxVeinSize) {
//                val current = trackedBlocks.next()
//                for (face in VBlockFace.entries) {
//                    if (veinBlocks.size + this.veinBuffer.size >= maxVeinSize) {
//                        break
//                    }
//
//                    val nextBlock = face.getConnectedBlock(current)
//                    if (veinBlocks.contains(nextBlock) || nextBlock.type != event.getBlock()
//                            .type || isPlayerPlaced(event.getBlock())
//                    ) {
//                        continue
//                    }
//
//                    this.veinBuffer.add(nextBlock)
//                }
//            }
//
//            if (veinBuffer.isEmpty()) {
//                break
//            }
//
//            veinBlocks.addAll(veinBuffer)
//            veinBuffer.clear()
//        }
//
//        manaCost *= veinBlocks.size.toDouble()
//        if (!(checkMana(skillPlayer, manaCost))) {
//            plugin.actionBar.sendAbilityActionBar(player, "&4Not enough mana!")
//            return
//        }
//        skillPlayer.removeMana(manaCost)
//
//        plugin.getMiningLeveler().level(player, type, veinBlocks.size)
//
//        for (block in veinBlocks) {
//            block.breakNaturally(pick, true)
//            consumeDurability(player, pick)
//        }
//        Bukkit.getPluginManager()
//            .callEvent(AbilityFortuneEvent(player, type, event.getBlock().getLocation(), veinBlocks.size))
//        plugin.actionBar.sendAbilityActionBar(player, "Vein Mine activated!")
//    }
//
//    @EventHandler(priority = EventPriority.NORMAL)
//    fun shovelAbility(event: BlockBreakEvent) {
//        var isSuccessful = false
//        if (!(ItemUtils.isShovel(event.getPlayer().getInventory().getItemInMainHand()))) return
//        if (!(event.getPlayer().getMetadata("readied").get(0).asBoolean())) return
//
//        val player = event.getPlayer()
//        val shovel = player.getInventory().getItemInMainHand()
//        val skillPlayer: SkillPlayer = plugin.getPlayerManager().getPlayerData(player)
//        var manaCost = Ability.EXCAVATOR.manaCost
//
//        // Ability size 5x5
//        var startBlock = -2
//        var endBlock = 3
//
//        if (skillPlayer.getSkillLevel(Skills.EXCAVATION) < Ability.EXCAVATOR.unlock) {
//            return
//        } else if (skillPlayer.getSkillLevel(Skills.EXCAVATION) >= Ability.EXCAVATOR.upgrade) {
//            // Ability size 9x9
//            startBlock = -4
//            endBlock = 5
//
//            // Reduced mana cost
//            manaCost /= 2.0
//        }
//
//        var blocksBroken = 0
//        var block = event.getBlock()
//
//        if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) return
//
//        val originalType = event.getBlock().getType()
//
//        for (x in startBlock..<endBlock) {
//            for (z in startBlock..<endBlock) {
//                block = event.getBlock().getLocation().add(x.toDouble(), 0.0, z.toDouble()).getBlock()
//                if (originalType == block.getType() && validDig(block.getType())) {
//                    if (!(checkMana(skillPlayer, manaCost))) {
//                        plugin.actionBar.sendAbilityActionBar(player, "&4Not enough mana!")
//                        return
//                    }
//                    block.breakNaturally(shovel, true)
//                    consumeDurability(player, shovel)
//                    skillPlayer.removeMana(manaCost)
//                    isSuccessful = true
//                    blocksBroken++
//                }
//            }
//        }
//
//        if (blocksBroken == 0) return
//        plugin.getExcavationLeveler().level(player, originalType, blocksBroken)
//        if (isSuccessful) plugin.actionBar.sendAbilityActionBar(player, "Excavator activated!")
//    }
//
//    @EventHandler(priority = EventPriority.MONITOR)
//    fun axeAbility(event: BlockBreakEvent) {
//        if (!(ItemUtils.isAxe(event.getPlayer().getInventory().getItemInMainHand()))) return
//        if (!(validChop(event.getBlock().getType()))) return
//        if (!(event.getPlayer().getMetadata("readied").get(0).asBoolean())) return
//
//        val player = event.getPlayer()
//        val axe = player.getInventory().getItemInMainHand()
//        val skillPlayer: SkillPlayer = plugin.getPlayerManager().getPlayerData(player)
//        val type = event.getBlock().getType()
//
//        var maxTreeSize = 40
//        var manaCost = Ability.TREECAPTITOR.manaCost
//
//        if (skillPlayer.getSkillLevel(Skills.WOODCUTTING) < Ability.TREECAPTITOR.unlock) {
//            return
//        } else if (skillPlayer.getSkillLevel(Skills.WOODCUTTING) >= Ability.TREECAPTITOR.upgrade) {
//            // Vein mine 20 blocks
//            maxTreeSize *= 2
//
//            // Reduced mana cost
//            manaCost /= 2.0
//        }
//
//        val treeBlocks: MutableSet<Block> = HashSet<Block>()
//        treeBlocks.add(event.getBlock())
//
//        while (treeBlocks.size < maxTreeSize) {
//            val trackedBlocks = treeBlocks.iterator()
//            while (trackedBlocks.hasNext() && treeBlocks.size + this.treeBuffer.size <= maxTreeSize) {
//                val current = trackedBlocks.next()
//                for (face in VBlockFace.entries) {
//                    if (treeBlocks.size + this.treeBuffer.size >= maxTreeSize) {
//                        break
//                    }
//
//                    val nextBlock = face.getConnectedBlock(current)
//                    if (treeBlocks.contains(nextBlock) || nextBlock.type != event.getBlock()
//                            .type || isPlayerPlaced(event.getBlock())
//                    ) {
//                        continue
//                    }
//
//                    this.treeBuffer.add(nextBlock)
//                }
//            }
//
//            if (treeBuffer.isEmpty()) {
//                break
//            }
//
//            treeBlocks.addAll(treeBuffer)
//            treeBuffer.clear()
//        }
//
//        manaCost *= treeBlocks.size.toDouble()
//        if (!(checkMana(skillPlayer, manaCost))) {
//            plugin.actionBar.sendAbilityActionBar(player, "&4Not enough mana!")
//            return
//        }
//        skillPlayer.removeMana(manaCost)
//
//        for (block in treeBlocks) {
//            block.breakNaturally(axe, true)
//            consumeDurability(player, axe)
//        }
//
//        plugin.leveler.addXp(player, Skill.WOODCUTTING, treeBlocks.size.toDouble())
//        Bukkit.getPluginManager()
//            .callEvent(AbilityFortuneEvent(player, type, event.getBlock().getLocation(), treeBlocks.size))
//        plugin.actionBar.sendAbilityActionBar(player, "Treecaptitor activated!")
//    }
//
//    fun consumeDurability(player: Player, item: ItemStack) {
//        if (!(validWeapon(item))) return
//        var breakChance = 1.0
//        if (item.getItemMeta() != null && item.getItemMeta().hasEnchant(Enchantment.UNBREAKING)) {
//            val enchantLvl = item.getItemMeta().getEnchantLevel(Enchantment.UNBREAKING)
//            breakChance /= (enchantLvl + 1).toDouble()
//        }
//
//        if (item.getItemMeta() == null) return
//
//        val damage = item.getItemMeta() as Damageable
//        if (damage.isUnbreakable()) return
//        val randDouble = Random().nextDouble(0.01, 1.01)
//        if (randDouble < breakChance) {
//            damage.setDamage(damage.getDamage() + 1)
//        }
//
//        item.setItemMeta(damage)
//
//        if (damage.getDamage() > item.getType().getMaxDurability()) {
//            player.getInventory().remove(item)
//            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f)
//        }
//    }
//
//    fun setBowPaused(player: Player?, ticks: Int) {
//        bowPaused.add(player)
//        object : BukkitRunnable() {
//            override fun run() {
//                bowPaused.remove(player)
//            }
//        }.runTaskLater(plugin, ticks.toLong())
//    }
//
//    fun checkBowFire(bow: ItemStack): Int {
//        if (!(bow.getItemMeta().hasEnchant(Enchantment.FLAME))) return 0
//        return 100
//    }
//
//    fun calculatePowerDmg(bow: ItemStack, damage: Double): Double {
//        if (!(bow.getItemMeta().hasEnchant(Enchantment.POWER))) return damage
//        val enchantLvl = bow.getItemMeta().getEnchantLevel(Enchantment.POWER)
//
//        return damage + ((damage * 0.5) * (enchantLvl + 1))
//    }
//
//    fun checkMana(playerData: PlayerData, manaCost: Double): Boolean {
//        val currentMana = playerData.currentMana
//        return !(currentMana < manaCost)
//    }
//
//    fun validWeapon(item: ItemStack): Boolean {
//        return ItemUtils.isShovel(item) || ItemUtils.isPick(item) || ItemUtils.isAxe(item) || ItemUtils.isSword(item)
//    }
//
//    fun validCrop(type: Material): Boolean {
//        return when (type) {
//            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.BAMBOO, Material.MELON_STEM, Material.PUMPKIN_STEM, Material.SWEET_BERRY_BUSH -> true
//            else -> false
//        }
//    }
//
//    fun validVein(type: Material): Boolean {
//        return when (type) {
//            Material.COAL_ORE, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS, Material.DEEPSLATE_COAL_ORE -> true
//            else -> false
//        }
//    }
//
//    fun validChop(type: Material): Boolean {
//        for (source in WoodcuttingSource.values()) {
//            if (type.name.equals(source.toString(), ignoreCase = true)) {
//                return true
//            }
//        }
//        return false
//    }
//
//    fun validDig(type: Material): Boolean {
//        for (source in ExcavationSource.values()) {
//            if (type.name.equals(source.toString(), ignoreCase = true)) {
//                return true
//            }
//        }
//        return false
//    }
//}