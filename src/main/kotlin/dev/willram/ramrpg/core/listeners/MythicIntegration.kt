/** Reflective MythicMobs adapter producing a mob-type resolver function. */
package dev.willram.ramrpg.core.listeners

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity

object MythicIntegration {
    val enabled: Boolean by lazy { Bukkit.getPluginManager().getPlugin("MythicMobs") != null }

    fun resolver(): ((LivingEntity) -> String?)? {
        if (!enabled) return null
        return { entity ->
            try {
                val cls = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
                val inst = cls.getMethod("inst").invoke(null)
                val mobMgr = cls.getMethod("getMobManager").invoke(inst)
                val isMythic = mobMgr.javaClass.getMethod("isMythicMob", org.bukkit.entity.Entity::class.java).invoke(mobMgr, entity) as Boolean
                if (!isMythic) null
                else {
                    val activeMob = mobMgr.javaClass.getMethod("getActiveMob", java.util.UUID::class.java).invoke(mobMgr, entity.uniqueId)
                    val opt = activeMob as? java.util.Optional<*>
                    val mob = opt?.orElse(null)
                    if (mob == null) null
                    else (mob.javaClass.getMethod("getMobType").invoke(mob) as? String)?.lowercase()
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
}
