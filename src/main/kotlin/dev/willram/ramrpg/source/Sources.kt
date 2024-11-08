package dev.willram.ramrpg.source

import dev.willram.ramcore.config.Configs
import dev.willram.ramcore.configurate.hocon.HoconConfigurationLoader
import dev.willram.ramrpg.RamRPG
import java.nio.file.Path


class Sources(private val plugin: RamRPG) {
    private var sources: MutableMap<Source, Double> = HashMap()

    fun loadSources() {
        val start = System.currentTimeMillis()
        val loader = HoconConfigurationLoader.builder()
            .path(Path.of("${plugin.dataFolder}/sources_config.conf"))
            .defaultOptions {opts -> opts.serializers {build -> build.registerAll(Configs.typeSerializers())}}
            .build()
        val sourcesConfig = loader.load(); // Load from file
        var sourcesLoaded = 0
        for (source in plugin.sourceRegistry.values()) {
            sources[source] = sourcesConfig.node(source.skill.name.lowercase(), source.toString().lowercase()).getDouble(source.defaultXp)
            sourcesLoaded++
        }
        loader.save(sourcesConfig)
        plugin.log("<yellow>Loaded <light_purple>$sourcesLoaded <yellow>sources in <light_purple>${System.currentTimeMillis() - start}<yellow>ms")
    }

    fun getXp(source: Source): Double {
        return sources[source]!!
    }
}