package dev.willram.ramrpg.ui

import com.destroystokyo.paper.profile.PlayerProfile
import dev.willram.ramcore.item.ItemStackBuilder
import dev.willram.ramcore.menu.Gui
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.stats.Stat
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.profile.PlayerTextures
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.util.UUID

class StatsMenu(player: Player) : Gui(player, 4, "${player.name}'s Stats") {
    override fun redraw() {
        for (i in 0..<handle.size) {
            this.setItem(i, ItemStackBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name("").build {})
        }

        this.setItem(0, ItemStackBuilder.of(Material.PLAYER_HEAD).name(player.name).transformMeta { meta ->
            val skullMeta = meta as SkullMeta
            skullMeta.owningPlayer = player
        }.build {});

        val playerStats = RamRPG.get().players.get(player.uniqueId).statPoints
        val stats = RamRPG.get().stats
        var slot = 3
        for (stat in playerStats.keys) {
            val actualStat = stats[stat]
            this.setItem(slot, ItemStackBuilder.of(Material.ENCHANTED_BOOK).name(MiniMessage.miniMessage().deserialize("${actualStat.prefix} ${actualStat.displayName}")).lore("    ${actualStat.prefix} ${playerStats[stat]} ${actualStat.symbol}").build {})
            slot++
        }


        this.setItem(27, ItemStackBuilder.of(Material.BARRIER).name("<red>Close").build {
            this.player.closeInventory()
        })

        this.setItem(
            35,
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
            }.build {
                player.closeInventory()
                val menu = SkillsMenu(player)
                menu.open()
            })
    }

    override fun clickHandler(e: InventoryClickEvent): Boolean { return false }

    override fun closeHandler(e: InventoryCloseEvent) {}

    override fun invalidateHandler() {}
}