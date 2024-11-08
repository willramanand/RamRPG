package dev.willram.ramrpg.indicators

import com.comphenix.protocol.PacketType.Play
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.AdventureComponentConverter
import com.comphenix.protocol.wrappers.WrappedDataValue
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject
import com.google.common.collect.Lists
import dev.willram.ramcore.event.Events
import dev.willram.ramcore.protocol.Protocol
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.utils.Formatter
import io.papermc.paper.event.entity.EntityMoveEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.*
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import java.util.*


class Indicators private constructor() {

    companion object {
        fun register() {
            Protocol.subscribe(Play.Server.ENTITY_METADATA)
                .handler { e ->
                    val packet = e.packet
                    val entity = packet.getEntityModifier(e).read(0);

                    if (entity !is LivingEntity) return@handler
                    if (!validEntity(entity)) return@handler

                    handlePacket(packet, entity)
                    e.packet = packet
                }

            Events.subscribe(PlayerMoveEvent::class.java).handler { e ->
                val to = e.to.getNearbyEntities(7.5, 7.5, 7.5).filterIsInstance<LivingEntity>()
                    .filter { livingEntity -> validEntity(livingEntity) }.toList()
                val from = e.from.getNearbyEntities(7.5, 7.5, 7.5).filterIsInstance<LivingEntity>()
                    .filter { livingEntity -> validEntity(livingEntity) }.toList()

                to.forEach { entity ->
                    if (e.player.hasLineOfSight(entity)) entity.isCustomNameVisible = true
                }

                from.forEach { entity ->
                    if (!to.contains(entity)) {
                        entity.isCustomNameVisible = false
                    }
                }
            }

            Events.subscribe(EntityMoveEvent::class.java).handler { e ->
                if (!validEntity(e.entity)) return@handler
                var playerExists = false
                e.entity.getNearbyEntities(7.5, 7.5, 7.5).filterIsInstance<Player>().forEach { player ->
                    if (player.hasLineOfSight(e.entity)) {
                        playerExists = true
                        return@forEach
                    }
                }
                if (!playerExists) {
                    e.entity.isCustomNameVisible = false
                    return@handler
                }
                e.entity.isCustomNameVisible = true
            }

            Events.subscribe(EntityDamageEvent::class.java, EventPriority.MONITOR).handler { e ->
                if (e.isCancelled) return@handler
                if (e.entity.type == EntityType.ITEM) return@handler
                if (!checkVisible(e.entity)) return@handler

                if (e.cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK || e.cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || e.cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK || e.cause == EntityDamageEvent.DamageCause.PROJECTILE) return@handler
                val location = e.entity.location
                val damage = e.damage
                location.y += e.entity.height
                spawnIndicator(e.entity as LivingEntity, location, damage)
            }

            Events.subscribe(EntityDamageByEntityEvent::class.java, EventPriority.MONITOR).handler { e ->
                if (e.isCancelled) return@handler
                if (!checkVisible(e.entity)) return@handler
                val location = e.entity.location
                val damage = e.damage
                location.y += e.entity.height
                spawnIndicator(e.entity as LivingEntity, location, damage)
            }
        }

        private fun validEntity(entity: LivingEntity): Boolean {
            if (entity is Boss) return false
            if (entity is Player) return false
            return true
        }

        private fun handlePacket(packet: PacketContainer, entity: LivingEntity) {
            val dataWatcher = WrappedDataWatcher.getEntityWatcher(entity).deepClone()
            val chatSerializer: WrappedDataWatcher.Serializer =
                WrappedDataWatcher.Registry.getChatComponentSerializer(true)
            val optChatFieldWatcher = WrappedDataWatcherObject(2, chatSerializer)

            val entityName = entity.customName() ?: MiniMessage.miniMessage().deserialize(entity.name)
            val currentHealth = entity.health
            val maxHealth = entity.getAttribute(Attribute.MAX_HEALTH)?.value
            val percentageOfHealth = (currentHealth / maxHealth!!) * 100
            val healthColor: String = when (percentageOfHealth) {
                in 71.0..99.9 -> {
                    "<yellow>"
                }

                in 41.0..70.9 -> {
                    "<gold>"
                }

                in 0.0..40.9 -> {
                    "<red>"
                }

                else -> {
                    "<white>"
                }
            }
            val healthFormat = "<entityname>: <red>❤ ${healthColor}${currentHealth.toInt()}<white>/${maxHealth.toInt()}"

            val entityNameFormat =
                MiniMessage.miniMessage().deserialize(healthFormat, Placeholder.component("entityname", entityName))

            val optChatField = Optional.of(AdventureComponentConverter.fromComponent(entityNameFormat).handle)

            dataWatcher.setObject(optChatFieldWatcher, optChatField)
            //dataWatcher.setObject(3, true) // set CustomNameVisible=true

            val wrappedDataValueList = writeWatchableObjects(dataWatcher)

            packet.dataValueCollectionModifier.write(0, wrappedDataValueList)
        }

        private fun checkVisible(entity: Entity): Boolean {
            var isVisible = false
            for (player in Bukkit.getOnlinePlayers()) {
                isVisible = player.hasLineOfSight(entity) || player == entity
                if (isVisible) break
            }
            return isVisible
        }

        private fun spawnIndicator(entity: LivingEntity, location: Location, damage: Double) {
            location.x += Random().nextDouble(-0.5, 0.5)
            location.z += Random().nextDouble(-0.5, 0.5)
            location.y += Random().nextDouble(-0.25, 0.25)

            val entityId = (Math.random() * Integer.MAX_VALUE).toInt()

            val packet = PacketContainer(Play.Server.SPAWN_ENTITY)
            val metadataPacket = PacketContainer(Play.Server.ENTITY_METADATA)


            packet.modifier.writeDefaults()
            metadataPacket.modifier.writeDefaults()

            // Crucial data, do not remove
            packet.integers.write(0, entityId)
            packet.uuiDs.write(0, UUID.randomUUID())
            packet.entityTypeModifier.write(0, EntityType.TEXT_DISPLAY)

            packet.doubles
                .write(0, location.x)
                .write(1, location.y)
                .write(2, location.z)

            metadataPacket.integers.write(0, entityId)

            // metadata packet
            val damageComponent = MiniMessage.miniMessage().deserialize("<red>${Formatter.decimalFormat(damage, 1)}</red>")
            val watcher = WrappedDataWatcher()
            val alignment = WrappedDataWatcherObject(15, WrappedDataWatcher.Registry.get(java.lang.Byte::class.java))
            val text = WrappedDataWatcherObject(23, WrappedDataWatcher.Registry.getChatComponentSerializer(false))
            watcher.setObject(text, AdventureComponentConverter.fromComponent(damageComponent).handle)
            watcher.setObject(alignment, 0x03.toByte())

            val wrappedDataValueList = writeWatchableObjects(watcher)
            metadataPacket.dataValueCollectionModifier.write(0, wrappedDataValueList)

            // Send the packet to all players
            val playersSeen = mutableSetOf<Player>()
            entity.getNearbyEntities(20.0, 20.0, 20.0).filterIsInstance<Player>().forEach { e ->
                Protocol.sendPacket(e, packet);
                Protocol.sendPacket(e, metadataPacket);
                playersSeen.add(e)
            }

            Schedulers.sync().runLater({
                val destroyPacket = PacketContainer(Play.Server.ENTITY_DESTROY)
                destroyPacket.intLists.write(0, listOf(entityId))

                playersSeen.forEach { player ->
                    Protocol.sendPacket(player, destroyPacket)
                }
            }, 30L)

//            val display = location.world.spawn(location, TextDisplay::class.java) { display ->
//                display.text(MiniMessage.miniMessage().deserialize("<red>${Formatter.decimalFormat(damage, 1)}</red>"));
//                display.billboard = Display.Billboard.CENTER;
//            }
//            Schedulers.sync().runLater({
//                display.remove()
//            }, 30L)
        }

        private fun writeWatchableObjects(dataWatcher: WrappedDataWatcher): MutableList<WrappedDataValue> {
            val wrappedDataValueList: MutableList<WrappedDataValue> = Lists.newArrayList()
            dataWatcher.watchableObjects.stream().filter(Objects::nonNull).forEach { entry ->
                val dataWatcherObject: WrappedDataWatcherObject = entry.watcherObject
                if (dataWatcherObject.serializer != null) {
                    wrappedDataValueList.add(
                        WrappedDataValue(
                            dataWatcherObject.index,
                            dataWatcherObject.serializer,
                            entry.rawValue
                        )
                    )
                }
            }
            return wrappedDataValueList
        }
    }
}