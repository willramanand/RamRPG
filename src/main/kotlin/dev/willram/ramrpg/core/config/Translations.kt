/**
 * Loads lang/&lt;locale&gt;.json files into Adventure GlobalTranslator with
 * language-only fallback registration so locale variants resolve.
 */
package dev.willram.ramrpg.core.config

import com.google.gson.Gson
import net.kyori.adventure.key.Key
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.TranslationRegistry
import java.text.MessageFormat
import java.util.Locale

object Translations {
    private val key: Key = Key.key("ramrpg", "core")

    fun load(plugin: org.bukkit.plugin.Plugin) {
        val registry = TranslationRegistry.create(key)
        registry.defaultLocale(Locale.US)
        for (locale in listOf("en_us")) {
            val res = plugin.getResource("lang/$locale.json") ?: continue
            res.use { stream ->
                @Suppress("UNCHECKED_CAST")
                val map = Gson().fromJson(stream.reader(), Map::class.java) as Map<String, String>
                val full = parseLocale(locale)
                val langOnly = Locale.of(full.language)
                for ((k, v) in map) {
                    registry.register(k, full, MessageFormat(v, full))
                    // language-only fallback so en_GB / en_AU resolve via en
                    if (full != langOnly) {
                        registry.register(k, langOnly, MessageFormat(v, langOnly))
                    }
                }
            }
        }
        GlobalTranslator.translator().addSource(registry)
    }

    private fun parseLocale(s: String): Locale {
        val parts = s.split('_')
        return if (parts.size == 2) Locale.of(parts[0], parts[1].uppercase()) else Locale.of(parts[0])
    }
}
