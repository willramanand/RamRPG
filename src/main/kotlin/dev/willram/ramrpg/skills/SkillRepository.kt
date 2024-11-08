package dev.willram.ramrpg.skills

import dev.willram.ramcore.config.Configs
import dev.willram.ramcore.configurate.hocon.HoconConfigurationLoader
import dev.willram.ramcore.data.DataRepository
import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.stats.Stat
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.player.PlayerJoinEvent
import java.nio.file.Path


class SkillRepository(private val plugin: RamRPG) : DataRepository<Skill, LoadedSkill>() {
    override fun setup() {
        for (skill in Skill.entries) {
            val loader = this.file(skill)
            val node = loader.load()
            val defaultSkillData = loadDefaultSkillInfo(skill)
            val loadedSkill = node.get(LoadedSkill::class.java)

            if (loadedSkill?.desc?.isEmpty()!! && loadedSkill.displayName.isEmpty()) {
                plugin.log("<yellow>Config file for skill <light_purple>${skill.name} <yellow>is missing or corrupted! Setting defaults.")
                this.add(skill, defaultSkillData)
            } else {
                this.add(skill, loadedSkill)
            }

            node.set(LoadedSkill::class.java, this.get(skill));
            loader.save(node)
        }
    }

    override fun saveAll() {
        for (id in this.registry().keys) {
            val data = this.get(id)
            if (!data.shouldNotSave() || !data.isSaving) {
                data.isSaving = true
                val loader = this.file(id)
                val node = loader.load()
                node.set(LoadedSkill::class.java, data);
                loader.save(node);
                data.isSaving = false
            }
        }
    }

    private fun file(skill: Skill): HoconConfigurationLoader {
        return HoconConfigurationLoader.builder()
            .path(Path.of("${plugin.dataFolder}/skills/${skill.name.lowercase()}.conf"))
            .defaultOptions {opts -> opts.serializers {build -> build.registerAll(Configs.typeSerializers())}}
            .build()
    }

    private fun loadDefaultSkillInfo(skill: Skill): LoadedSkill {
        val skillData = LoadedSkill()
        skillData.displayName = skill.displayName
        skillData.desc = skill.description
        skillData.barColor = skill.barColor
        skillData.maxLvl = skill.maxLvl
        skillData.base = skill.base
        skillData.multiplier = skill.multiplier
        skillData.stats = skill.stats.toMutableList()
        return skillData
    }
}