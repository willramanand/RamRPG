package dev.willram.ramrpg.source

import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.skills.Skill
import java.lang.reflect.InvocationTargetException
import java.util.*

class SourceRegistry(private val plugin: RamRPG) {
    private val registry: MutableMap<Skill, Class<*>> = EnumMap(Skill::class.java)

    init {
        try {
            for (skill in Skill.entries) {
                val className: String = plugin.skills[skill].displayName + "Source"
                val clazz = Class.forName(
                    ("dev.willram.ramrpg.source." + skill.toString().lowercase(Locale.getDefault())) + "." + className
                )
                registry[skill] = clazz
            }
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
    }

    fun register(skill: Skill, clazz: Class<out Source?>) {
        registry[skill] = clazz
    }

    fun values(skill: Skill): Array<Source?> {
        val clazz = registry[skill] ?: return arrayOfNulls(0)
        try {
            val method = clazz.getMethod("values")
            val `object` = method.invoke(null)
            if (`object` is Array<*> && `object`.isArrayOf<Source>()) {
                return `object` as Array<Source?>
            }
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
        return arrayOfNulls<Source>(0)
    }

    fun values(): Set<Source> {
        val sourceSet: MutableSet<Source> = HashSet<Source>()
        for (sourceClass in registry.values) {
            try {
                val method = sourceClass.getMethod("values")
                val `object` = method.invoke(null)
                if (`object` is Array<*> && `object`.isArrayOf<Source>()) {
                    sourceSet.addAll(`object` as Array<Source>)
                }
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
        }
        return sourceSet
    }

    fun valueOf(sourceString: String): Source? {
        for (source in values()) {
            if (source.toString() == sourceString.uppercase(Locale.getDefault())) {
                return source
            }
        }
        return null
    }
}