package dev.willram.ramrpg

import dev.willram.ramcore.RamPlugin
import dev.willram.ramcore.config.Configs
import dev.willram.ramcore.configurate.hocon.HoconConfigurationLoader
import dev.willram.ramcore.event.Events
import dev.willram.ramcore.menu.Gui
import dev.willram.ramcore.metadata.Metadata
import dev.willram.ramcore.scheduler.Schedulers
import dev.willram.ramcore.scheduler.Task
import dev.willram.ramrpg.commands.SkillsRootCommand
import dev.willram.ramrpg.config.RPGConfig
import dev.willram.ramrpg.data.PlayerRepository
import dev.willram.ramrpg.enchants.EnchantingSystem
import dev.willram.ramrpg.enchants.Enchantments
import dev.willram.ramrpg.entity.EntityListeners
import dev.willram.ramrpg.indicators.Indicators
import dev.willram.ramrpg.items.ItemListeners
import dev.willram.ramrpg.levels.Leveler
import dev.willram.ramrpg.skills.SkillListeners
import dev.willram.ramrpg.skills.SkillRepository
import dev.willram.ramrpg.source.SourceRegistry
import dev.willram.ramrpg.source.Sources
import dev.willram.ramrpg.stats.StatListeners
import dev.willram.ramrpg.stats.StatRepository
import dev.willram.ramrpg.ui.ActionBar
import dev.willram.ramrpg.ui.BossBar
import dev.willram.ramrpg.utils.BlockUtils
import io.papermc.paper.command.brigadier.Commands
import net.milkbowl.vault.economy.Economy
import org.bukkit.event.block.BlockPlaceEvent
import java.nio.file.Path


class RamRPG : RamPlugin() {

    private lateinit var autoSaveTask: Task;

    var vaultEnabled = false
    var econ: Economy? = null

    lateinit var conf: RPGConfig
    lateinit var stats: StatRepository
    lateinit var skills: SkillRepository
    lateinit var players: PlayerRepository
    lateinit var sourceRegistry: SourceRegistry
    lateinit var sources: Sources
    lateinit var leveler: Leveler
    lateinit var actionBar: ActionBar
    lateinit var bossBar: BossBar

    val GUI_ITEM_KEY = "ignore-gui-item"

    companion object {
        private lateinit var i: RamRPG;

        fun get(): RamRPG {
            return i;
        }
    }

    init {
        i = this
    }

    override fun enable() {
        if (vaultEnabled) {
            setupEconony()
        }

        this.loadConf()

        stats = StatRepository(this)
        skills = SkillRepository(this)
        players = PlayerRepository(this)
        leveler = Leveler(this)
        actionBar = ActionBar(this)
        bossBar = BossBar(this)

        stats.setup()
        skills.setup()
        players.setup()

        sourceRegistry = SourceRegistry(this)
        sources = Sources(this)

        leveler.loadLevelReqs()
        actionBar.startUpdateActionBar()
        bossBar.load()
        sources.loadSources()

        Enchantments.register()
        EnchantingSystem.register()

        Indicators.register()

        SkillListeners.register()
        StatListeners.register()

        EntityListeners.register()

        ItemListeners.register()

        Events.subscribe(BlockPlaceEvent::class.java)
            .handler { e ->
                BlockUtils.setPlayerPlaced(e.block)
            }

        this.autoSaveTask = Schedulers.async().runRepeating({ _: Task ->
            stats.saveAll()
            skills.saveAll()
            players.saveAll()
        }, 12000L, 12000L)
    }

    override fun disable() {
        autoSaveTask.stop()

        this.saveConf()

        stats.saveAll()
        skills.saveAll()
        players.saveAll()

        actionBar.resetActionBars()

        //close custom guis if open
        for (entry in Metadata.players().getAllWithKey(Gui.OPEN_GUI_KEY)) {
            entry.value.close()
        }
    }

    override fun load() {
        checkVault()
    }

    @Suppress("UnstableApiUsage")
    override fun registerCommands(commands: Commands) {
        commands.register("skills", "Root command for all skills related commands.", listOf("sk"), SkillsRootCommand(this))
    }

    private fun loadConf() {
        val loader = HoconConfigurationLoader.builder()
            .path(Path.of("${this.dataFolder}/config.conf"))
            .defaultOptions {opts -> opts.serializers {build -> build.registerAll(Configs.typeSerializers())}}
            .build()
        val node = loader.load(); // Load from file
        this.conf = node.get(RPGConfig::class.java)!!
        loader.save(node)
    }

    private fun saveConf() {
        val loader = HoconConfigurationLoader.builder()
            .path(Path.of("${this.dataFolder}/config.conf"))
            .defaultOptions {opts -> opts.serializers {build -> build.registerAll(Configs.typeSerializers())}}
            .build()
        val node = loader.load(); // Load from file
        node.set(RPGConfig::class.java, conf)
        loader.save(node)
    }

    private fun checkVault() {
        if (server.pluginManager.getPlugin("Vault") == null) return
        vaultEnabled = true
        this.log("<yellow>Vault found: <green>INTEGRATION ENABLED")
    }

    private fun setupEconony() {
        val rsp = server.servicesManager.getRegistration(
            Economy::class.java
        )
        if (rsp == null) {
            vaultEnabled = false
            return
        }
        econ = rsp.provider
    }
}
