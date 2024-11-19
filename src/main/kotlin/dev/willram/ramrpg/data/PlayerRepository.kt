package dev.willram.ramrpg.data

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import dev.willram.ramcore.bucket.Bucket
import dev.willram.ramcore.bucket.factory.BucketFactory
import dev.willram.ramcore.bucket.partitioning.PartitioningStrategies
import dev.willram.ramcore.config.Configs
import dev.willram.ramcore.configurate.hocon.HoconConfigurationLoader
import dev.willram.ramcore.data.DataRepository
import dev.willram.ramcore.event.Events
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.scheduler.Task
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.events.ManaRegenerateEvent
import dev.willram.ramrpg.events.SkillLevelUpEvent
import dev.willram.ramrpg.stats.Stat
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.player.*
import java.nio.file.Path
import java.util.*


class PlayerRepository(private val plugin: RamRPG) : DataRepository<UUID, PlayerData>() {

    var bucket: Bucket<Player> = BucketFactory.newHashSetBucket(20, PartitioningStrategies.lowestSize())

    override fun setup() {
        Events.subscribe(PlayerLoginEvent::class.java, EventPriority.HIGH)
            .filter { e -> e.result == PlayerLoginEvent.Result.ALLOWED }
            .handler { e ->
                val loader = this.file(e.player.uniqueId)
                val node = loader.load(); // Load from file
                val data = node.get(PlayerData::class.java, PlayerData());

                data.player = e.player
                plugin.stats.allocateStats(data)

                this.add(e.player.uniqueId, data);
                node.set(PlayerData::class.java, data);
                loader.save(node);

                bucket.add(e.player)

                Schedulers.sync().runLater({
                    applyModifiers(e.player, e)
                }, 10L)
            }

        Events.subscribe(PlayerQuitEvent::class.java, EventPriority.HIGH)
            .handler { e ->
                val data = this.get(e.player.uniqueId)
                val loader = this.file(e.player.uniqueId)
                val node = loader.load(); // Load from file
                node.set(PlayerData::class.java, data);
                loader.save(node);
                this.remove(e.player.uniqueId)
                bucket.remove(e.player)
            }

        Events.subscribe(EntityRegainHealthEvent::class.java)
            .handler { e ->
                if (e.isCancelled) return@handler
                if (e.entity !is Player) return@handler

                val data = this.get(e.entity.uniqueId)
                e.amount += data.statPoints[Stat.HEALTH]!! / 100.0
            }

        Events.subscribe(PlayerChangedWorldEvent::class.java)
            .handler { e ->
                if (e.player.gameMode.isInvulnerable) return@handler
                applyModifiers(e.player, e)
            }

        Events.subscribe(SkillLevelUpEvent::class.java)
            .handler { e ->
                val data = plugin.players.get(e.player.uniqueId)
                plugin.stats.allocateStats(data);
                applyModifiers(e.player, e)
            }

        Events.subscribe(PlayerSwapHandItemsEvent::class.java)
            .handler { e ->
                val data = plugin.players.get(e.player.uniqueId)
                plugin.stats.allocateStats(data);
                applyModifiers(e.player, e)
            }

        Events.subscribe(PlayerArmorChangeEvent::class.java)
            .handler { e ->
                val data = plugin.players.get(e.player.uniqueId)
                plugin.stats.allocateStats(data);
                applyModifiers(e.player, e)
            }

        Events.subscribe(PlayerItemBreakEvent::class.java)
            .handler { e ->
                val data = plugin.players.get(e.player.uniqueId)
                plugin.stats.allocateStats(data);
                applyModifiers(e.player, e)
            }

        Schedulers.async().runRepeating({ _: Task ->
            val part = bucket.asCycle().next()
            for (player in part) {
                val data = this.get(player.uniqueId)
                val originalMana = data.currentMana
                val maxMana = data.statPoints[Stat.WISDOM]!!
                if (originalMana < maxMana) {
                    val regen = data.statPoints[Stat.WISDOM]!! / 180
                    val event = ManaRegenerateEvent(player, regen)

                    Schedulers.sync().run {
                        Events.call(event)
                    }

                    if (!event.isCancelled) {
                        data.currentMana += regen
                    }
                }

                if (originalMana > maxMana) {
                    data.currentMana = maxMana
                }
            }
        }, 1L, 1L)

        Schedulers.async().runRepeating({ _: Task ->
            val part = bucket.asCycle().next()
            for (player in part) {
                val data = this.get(player.uniqueId)
                plugin.stats.allocateStats(data);
                applyModifiers(player, null)
            }
        }, 1L, 1L)
    }

    override fun saveAll() {
        for (id in this.registry().keys) {
            val data = this.get(id)
            if (!data.shouldNotSave() || !data.isSaving) {
                data.isSaving = true
                val loader = this.file(id)
                val node = loader.load()
                node.set(PlayerData::class.java, data);
                loader.save(node);
                data.isSaving = false
            }
        }
    }

    private fun file(id: UUID): HoconConfigurationLoader {
        return HoconConfigurationLoader.builder()
            .path(Path.of("${plugin.dataFolder}/playerdata/${id}.conf"))
            .defaultOptions {opts -> opts.serializers {build -> build.registerAll(Configs.typeSerializers())}}
            .build()
    }


    private fun applyModifiers(player: Player, event: Event?) {
        val playerData = this.get(player.uniqueId)

        val health: Double = playerData.statPoints[Stat.HEALTH]!!
        val swingAdd: Double = playerData.statPoints[Stat.ATTACK_SPEED]!!
        val speedAdd: Double = playerData.statPoints[Stat.SPEED]!!

        val baseMove = 0.2f
        if (speedAdd > 0.0) {
            player.walkSpeed = (baseMove + (0.8 * (speedAdd / (100 + speedAdd)))).toFloat()
        } else {
            player.walkSpeed = baseMove
        }
        player.getAttribute(Attribute.MAX_HEALTH)!!.baseValue = health
        val baseSwing = 4
        player.getAttribute(Attribute.ATTACK_SPEED)!!.baseValue = baseSwing + swingAdd
        if (event != null && event is PlayerJoinEvent)
            player.health = player.getAttribute(Attribute.MAX_HEALTH)!!.baseValue
        player.healthScale = 20.0
    }
}