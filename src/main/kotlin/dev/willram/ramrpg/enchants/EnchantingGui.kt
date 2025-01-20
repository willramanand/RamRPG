package dev.willram.ramrpg.enchants

import com.destroystokyo.paper.profile.PlayerProfile
import dev.willram.ramcore.event.Events
import dev.willram.ramcore.item.ItemStackBuilder
import dev.willram.ramcore.menu.Gui
import dev.willram.ramcore.menu.Item
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.scheduler.Task
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.events.CustomEnchantEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.profile.PlayerTextures
import org.bukkit.util.Vector
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.util.*


class EnchantingGui(player: Player, val table: Block) : Gui(player, 6, "Enchant Item", true) {

    private val ENCHANTMENTS_SLOTS = listOf(12, 13, 14, 15, 16, 21, 22, 23, 24, 25, 30, 31, 32, 33, 34)
    private val INPUT_SLOT = 19
    private val BACK_SLOT = 45
    private var inputItem: ItemStack? = null;
    private var tickingTask: Task;

    private val bookshelfVectors = listOf<Vector>(
        Vector(2, 0, -2),
        Vector(2, 1, -2),
        Vector(2, 0, 2),
        Vector(2, 1, 2),
        Vector(-2, 0, -2),
        Vector(-2, 1, -2),
        Vector(-2, 0, 2),
        Vector(-2, 1, 2),
        Vector(1, 0, -2),
        Vector(0, 0, -2),
        Vector(-1, 0, -2),
        Vector(1, 1, -2),
        Vector(0, 1, -2),
        Vector(-1, 1, -2),
        Vector(1, 0, 2),
        Vector(0, 0, 2),
        Vector(-1, 0, 2),
        Vector(1, 1, 2),
        Vector(0, 1, 2),
        Vector(-1, 1, 2),
        Vector(2, 0, -1),
        Vector(2, 0, 0),
        Vector(2, 0, 1),
        Vector(2, 1, -1),
        Vector(2, 1, 0),
        Vector(2, 1, 1),
        Vector(-2, 0, -1),
        Vector(-2, 0, 0),
        Vector(-2, 0, 1),
        Vector(-2, 1, -1),
        Vector(-2, 1, 0),
        Vector(-2, 1, 1)
    )
    private var bookshelfPower = 0

    init {
        tickingTask = Schedulers.async().runRepeating({ _ ->
            val currentInput = handle.getItem(INPUT_SLOT)
            if (currentInput != null) {
                if (inputItem != currentInput) {
                    inputItem = currentInput
                    handleEnchantments(currentInput)
                }
            } else {
                if (inputItem != null) {
                    inputItem = null
                    redraw()
                }
            }
        }, 0L, 1L)

        bookshelfPower = 0
        for (vec in bookshelfVectors) {
            if (bookshelfPower >= 30 ) break
            val tableLocation = table.location
            val currentBlock = tableLocation.add(vec)
            if (currentBlock == null || currentBlock.block.type != Material.BOOKSHELF) continue
            bookshelfPower++
        }
    }

    override fun redraw() {
        for (i in 0..<handle.size) {
            this.setItem(i, ItemStackBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name("").build {})
        }

        this.setItem(INPUT_SLOT, ItemStackBuilder.of(Material.AIR).build {})

        this.setItem(
            23,
            ItemStackBuilder.of(Material.GRAY_DYE).name("<red>Enchant Item")
                .lore("<grey>Place and item in the open slot to enchant it!").build {})
        this.setItem(
            28,
            ItemStackBuilder.of(Material.ENCHANTING_TABLE).name("<green>Enchant Item")
                .lore("<grey>Add an item to the slot above to view enchantment options!").build {})

        val bookPower = ItemStackBuilder.of(Material.BOOKSHELF)
            .name("<light_purple>Bookshelf Power")
            .lore(
                MiniMessage.miniMessage().deserialize("<grey>Stronger enchantments require"),
                MiniMessage.miniMessage().deserialize("<grey>more bookshelf power which"),
                MiniMessage.miniMessage().deserialize("<grey>can be increased by"),
                MiniMessage.miniMessage().deserialize("<grey>placing bookshelves nearby."),
                MiniMessage.miniMessage().deserialize(""),
                MiniMessage.miniMessage().deserialize("<grey>Current Bookshelf Power: <light_purple>${bookshelfPower}")
            )
            .build {}
        this.setItem(48, bookPower)
        this.setItem(49, ItemStackBuilder.of(Material.BARRIER).name("<red>Close").build {
            this.player.closeInventory()
        })

        val enchantGuide = ItemStackBuilder.of(Material.BOOK)
            .name("<green>Enchantments Guide")
            .lore(
                MiniMessage.miniMessage().deserialize("<grey>View a complete list of all"),
                MiniMessage.miniMessage().deserialize("<grey>enchantments and their requirements.")
            )
            .build {
                this.player.closeInventory()
                val guideGui = EnchantmentGuideGui(this.player)
                guideGui.open()
            }

        this.setItem(50, enchantGuide)
    }

    private fun handleEnchantments(item: ItemStack) {
        val applicable: MutableMap<CustomEnchantment, Boolean> = mutableMapOf()
        val currentEnchantments = Enchantments.getEnchants(item)

        for (enchantment in Enchantments.all()) {
            if (enchantment.allowed(item)) {
                var conflicts = false
                for (enchantmentEntry in currentEnchantments) {
                    conflicts = enchantment.conflicts(enchantmentEntry.key)
                    if (conflicts) break
                }
                applicable[enchantment] = conflicts
            }
        }

        for (i in ENCHANTMENTS_SLOTS) {
            this.setItem(i, ItemStackBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name("").build {})
        }

        this.setItem(BACK_SLOT, ItemStackBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name("").build {})

        if (applicable.isEmpty()) {
            this.setItem(
                23,
                ItemStackBuilder.of(Material.RED_DYE).name("<red>Invalid Item!")
                    .lore("<grey>This item cannot be enchanted!").build {})
            return
        }

        var count = 0
        for (enchant in applicable) {
            if (count > 14) break
            val hasEnchant = if (Enchantments.hasEnchant(item, enchant.key)) "<green><b>✔" else "<red><b>✖"
            val enchantLvl = if (Enchantments.hasEnchant(item, enchant.key)) currentEnchantments[enchant.key] else null
            val hasConflicts = if (!enchant.value) "<green><b>✔" else "<red><b>✖"
            val hasBookshelfPower = if (this.bookshelfPower >= enchant.key.requiredBookshelfPower()) "<yellow>Click to view!" else "<red><b>Bookshelf Power required: ${enchant.key.requiredBookshelfPower()}"
            val slot = ENCHANTMENTS_SLOTS[count]
            val lore: MutableList<Component> = ArrayList()
            for (line in enchant.key.description(null)) {
                lore.add(MiniMessage.miniMessage().deserialize(line))
            }
            lore.addAll(
                listOf(
                    MiniMessage.miniMessage().deserialize(""),
                    MiniMessage.miniMessage().deserialize("<grey>${enchant.key.displayName(enchantLvl)}: $hasEnchant"),
                    MiniMessage.miniMessage().deserialize("<grey>Applicable: $hasConflicts"),
                    MiniMessage.miniMessage().deserialize(""),
                    MiniMessage.miniMessage().deserialize(hasBookshelfPower)
                )
            )
            val book = ItemStackBuilder.of(Material.ENCHANTED_BOOK)
                .name(
                    MiniMessage.miniMessage().deserialize("<gold>${enchant.key.displayName(null)}")
                )
                .lore(
                    lore
                )
                .build(ClickType.LEFT) {
                    if(!enchant.value && this.bookshelfPower >= enchant.key.requiredBookshelfPower()) handleEnchantmentLevels(enchant.key)
                }
            this.setItem(slot, book)
            count++
        }
    }

    private fun handleEnchantmentLevels(enchantment: CustomEnchantment) {
        for (i in ENCHANTMENTS_SLOTS) {
            this.setItem(i, ItemStackBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name("").build {})
        }

        var currentEnchantments: Map<CustomEnchantment, Int> = mapOf()
        if (handle.getItem(INPUT_SLOT) != null) {
            val item = handle.getItem(INPUT_SLOT)
            if (item != null) {
                currentEnchantments = Enchantments.getEnchants(item)
                this.setItem(
                    BACK_SLOT,
                    ItemStackBuilder.of(Material.PLAYER_HEAD).name("<white>Go Back").transformMeta { meta ->
                        val headMeta = meta as SkullMeta
                        val profile: PlayerProfile = Bukkit.createProfile(UUID.randomUUID()) // Get a new player profile
                        val textures: PlayerTextures = profile.textures
                        val urlObject: URL
                        try {
                            urlObject = URL.of(
                                URI.create("https://textures.minecraft.net/texture/cdc9e4dcfa4221a1fadc1b5b2b11d8beeb57879af1c42362142bae1edd5"),
                                null
                            ) // The URL to the skin, for example: https://textures.minecraft.net/texture/18813764b2abc94ec3c3bc67b9147c21be850cdf996679703157f4555997ea63a
                        } catch (exception: MalformedURLException) {
                            throw RuntimeException("Invalid URL", exception)
                        }
                        textures.skin = urlObject // Set the skin of the player profile to the URL
                        profile.setTextures(textures)
                        headMeta.playerProfile = profile
                    }.build { handleEnchantments(item) })
            }
        }

        var count = 0
        for (level in enchantment.startLvl..enchantment.maxLvl) {
            if (count > 14) break

            var canApply = true
            if (currentEnchantments.isNotEmpty() && currentEnchantments.containsKey(enchantment)) {
                val currentLvl = currentEnchantments[enchantment]!!
                if (currentLvl >= level) {
                    canApply = false
                }
            }

            val slot = ENCHANTMENTS_SLOTS[count]
            val lore: MutableList<Component> = ArrayList()
            for (line in enchantment.description(level)) {
                lore.add(MiniMessage.miniMessage().deserialize(line))
            }
            lore.add(MiniMessage.miniMessage().deserialize(""))
            val xpCost = enchantment.xpCosts(level, ItemStack(Material.AIR))
            val hasCost = if (player.level >= xpCost) "<green><b>✔" else "<red><b>✖"

            val material: Material
            if (canApply) {
                material = Material.ENCHANTED_BOOK
                lore.add(MiniMessage.miniMessage().deserialize("<gray>Cost:"))
                lore.add(MiniMessage.miniMessage().deserialize("<dark_aqua>$xpCost Exp Levels $hasCost"))
            } else {
                material = Material.GRAY_DYE
                lore.add(MiniMessage.miniMessage().deserialize("<red>Already applied!"))
            }

            val book = ItemStackBuilder.of(material)
                .name(
                    MiniMessage.miniMessage().deserialize(
                        "<gold>${enchantment.displayName(level)}"
                    )
                )
                .lore(
                    lore
                )
                .build(ClickType.LEFT) {
                    if (player.level >= xpCost && canApply) handleEnchanting(enchantment, level, xpCost)
                }
            this.setItem(slot, book)
            count++
        }
    }

    private fun handleEnchanting(enchantment: CustomEnchantment, lvl: Int, xpCost: Int) {
        if (handle.getItem(INPUT_SLOT) != null) {
            val currentItem = handle.getItem(INPUT_SLOT)
            if (currentItem != null) {
                val item: ItemStack
                if (currentItem.type != Material.BOOK) {
                    item = currentItem
                } else {
                    handle.setItem(INPUT_SLOT, ItemStackBuilder.of(Material.ENCHANTED_BOOK).build())
                    item = handle.getItem(INPUT_SLOT)!!
                }
                val event = CustomEnchantEvent(player, table, item, enchantment, lvl)
                Events.call(event)
                if (!event.isCancelled) {
                    player.level -= xpCost
                    Enchantments.addEnchant(item, enchantment, lvl)
                }
                handleEnchantments(item)
            }
        }
    }

    override fun clickHandler(e: InventoryClickEvent): Boolean {
        if (e.slot == INPUT_SLOT && e.cursor != null) {
            val item = e.inventory.getItem(INPUT_SLOT) ?: e.cursor
            handleEnchantments(item)
            return true
        }

        return false
    }

    override fun closeHandler(e: InventoryCloseEvent) {
        tickingTask.closeAndReportException()
//        val item = e.inventory.getItem(INPUT_SLOT) ?: return
//        var spotOpen = false
//
//        for (itemSlot in e.inventory.contents) {
//            if (itemSlot != null) continue
//            if (spotOpen) break
//            spotOpen = true
//        }
//
//        if (spotOpen) {
//            player.inventory.addItem(item)
//        } else {
//            Bukkit.getWorld(player.location.world.uid)?.dropItem(player.location, item)
//        }
    }

    override fun invalidateHandler() {
        val item = handle.getItem(INPUT_SLOT) ?: return
        var spotOpen = false

        for (itemSlot in handle.contents) {
            if (itemSlot != null) continue
            if (spotOpen) break
            spotOpen = true
        }

        if (spotOpen) {
            player.inventory.addItem(item)
        } else {
            Bukkit.getWorld(player.location.world.uid)?.dropItem(player.location, item)
        }
    }
}