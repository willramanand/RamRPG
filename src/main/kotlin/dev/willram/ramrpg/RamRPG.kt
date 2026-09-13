package dev.willram.ramrpg

import dev.willram.ramcore.RamPlugin
import dev.willram.ramcore.data.DataKeyCodec
import dev.willram.ramcore.playerdata.PlayerDataOptions
import dev.willram.ramcore.playerdata.PlayerDataService
import dev.willram.ramcore.store.StoreCodec
import dev.willram.ramcore.store.StoreMigrations
import dev.willram.ramcore.store.Stores
import dev.willram.ramrpg.api.stats.StatDirtyReason
import dev.willram.ramrpg.builtin.abilities.BuiltinAbilities
import dev.willram.ramrpg.builtin.enchants.BuiltinEnchants
import dev.willram.ramrpg.builtin.entities.BuiltinEntities
import dev.willram.ramrpg.builtin.items.BuiltinItems
import dev.willram.ramrpg.builtin.reforges.BuiltinReforges
import dev.willram.ramrpg.builtin.skills.BuiltinSkills
import dev.willram.ramrpg.builtin.sockets.BuiltinGems
import dev.willram.ramrpg.builtin.stats.BuiltinStats
import dev.willram.ramrpg.core.config.ContentOverrideLoader
import dev.willram.ramrpg.core.config.Translations
import dev.willram.ramrpg.core.listeners.AbilityListener
import dev.willram.ramrpg.core.listeners.DurabilityListener
import dev.willram.ramrpg.core.listeners.EnchantingListener
import dev.willram.ramrpg.core.listeners.EntitySpawnListener
import dev.willram.ramrpg.core.listeners.EquipmentListener
import dev.willram.ramrpg.core.listeners.FortuneListener
import dev.willram.ramrpg.core.listeners.InventoryRefreshListener
import dev.willram.ramrpg.core.listeners.ItemRequirementServices
import dev.willram.ramrpg.core.listeners.ManaRegen
import dev.willram.ramrpg.core.listeners.MythicIntegration
import dev.willram.ramrpg.core.listeners.NonCombatXpListener
import dev.willram.ramrpg.core.listeners.SkillsCommand
import dev.willram.ramrpg.core.listeners.XpListener
import dev.willram.ramrpg.core.listeners.applyPlayerAttributes
import dev.willram.ramrpg.core.modules.RpgModules
import dev.willram.ramrpg.core.platform.RamCorePlatformScheduler
import dev.willram.ramrpg.core.rendering.PacketItemRendererImpl
import dev.willram.ramrpg.core.rendering.PacketRenderListener
import dev.willram.ramrpg.core.services.AbilityRegistryImpl
import dev.willram.ramrpg.core.services.AbilityServiceImpl
import dev.willram.ramrpg.core.services.DamagePipelineImpl
import dev.willram.ramrpg.core.services.EnchantmentRegistryImpl
import dev.willram.ramrpg.core.services.EnchantmentStatProvider
import dev.willram.ramrpg.core.services.EntityProfileRegistryImpl
import dev.willram.ramrpg.core.services.EquipmentStatProvider
import dev.willram.ramrpg.core.services.GemRegistryImpl
import dev.willram.ramrpg.core.services.ItemDefinitionRegistryImpl
import dev.willram.ramrpg.core.services.ItemInstanceServiceImpl
import dev.willram.ramrpg.core.services.ReforgeRegistryImpl
import dev.willram.ramrpg.core.services.ReforgeStatProvider
import dev.willram.ramrpg.core.services.RpgServiceKeys
import dev.willram.ramrpg.core.services.SkillRegistryImpl
import dev.willram.ramrpg.core.services.SkillServiceImpl
import dev.willram.ramrpg.core.services.SkillStatProvider
import dev.willram.ramrpg.core.services.SocketStatProvider
import dev.willram.ramrpg.core.services.StatServiceImpl
import dev.willram.ramrpg.core.storage.FilePlayerStore
import dev.willram.ramrpg.core.storage.PlayerRpgData
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import java.io.File
import java.time.Duration

/**
 * WP-1.7b: RamRPG owns no subsystem `lateinit` fields any more. Every subsystem service is
 * constructed and registered under [RpgServiceKeys] in [load]; every subsystem listener/registration
 * is either migrated into its own [dev.willram.ramrpg.core.modules] `TerminableModule` (bound in
 * [enable]) or, for the always-on core listeners that have no later WP, registered in [enable] against
 * this plugin's terminable consumer. Collaborators are resolved from the service registry, never from
 * a singleton. Anything with lifecycle is bound via `bind`/`bindModule`, so there is no manual
 * `shutdown()` in [disable].
 */
class RamRPG : RamPlugin() {

    /** Command handler; created in [enable], registered in [registerCommands] (Paper lifecycle). */
    private var skillsCommand: SkillsCommand? = null
    var mythicMobsEnabled: Boolean = false

    companion object {
        private lateinit var i: RamRPG

        @Deprecated(
            "RamRPG.get() is a transitional shim kept for one release. Resolve services from the " +
                "RamCore ServiceRegistry (services().require(RpgServiceKeys.X)) or receive them via " +
                "constructor injection instead.",
            ReplaceWith("services().require(dev.willram.ramrpg.core.services.RpgServiceKeys.X)"),
        )
        fun get(): RamRPG = i
    }

    init { i = this }

    override fun load() {
        mythicMobsEnabled = server.pluginManager.getPlugin("MythicMobs") != null

        // Per-player persistence on RamCore's PlayerDataService (Stores API). Installed and keyed from
        // load() because the service registry refuses registrations after load. The store is
        // migration-capable from day one; the dataVersion ledger (docs/design/F5-dataversion-ledger.md)
        // starts at v1 and later WPs append v2 (quests), v3 (buffs), v4 (perks) to this chain.
        val playerData = PlayerDataService.install(this, PlayerDataOptions.defaults())
        val storeDir = File(dataFolder, "playerdata")
        if (!storeDir.exists()) storeDir.mkdirs()
        val store = Stores.cached(
            Stores.file(
                storeDir.toPath(),
                DataKeyCodec.uuidKeys(),
                StoreCodec.gson(PlayerRpgData::class.java),
                StoreMigrations.start<PlayerRpgData>(),
            ),
        )
        playerData.register(FilePlayerStore.RPG_KEY, store)
        val playerStore = FilePlayerStore(playerData)

        // WP-1.7a: the RPG subsystem services are constructed here -- not in enable() -- because
        // RamCore's SimpleServiceRegistry (services().register(...)) refuses registration once
        // loadAll() has run, and RamPlugin#onLoad calls loadAll() immediately after this method
        // returns. None of these constructors touch the Bukkit runtime. Construction order is the
        // topological order recorded in RpgServiceGraph: a service is built after the services its
        // constructor needs (playerStore before skillService, itemDefs before itemInstances/renderer,
        // etc.). Listener registration, content, stat providers and damage stages happen in enable().
        val platform = RamCorePlatformScheduler()
        val stats = StatServiceImpl()
        val skillRegistry = SkillRegistryImpl()
        val skillService = SkillServiceImpl(
            skillRegistry, playerStore,
            onLevelUp = { p, k, lvl -> handleLevelUp(p, k, lvl) },
            // XP-gain reactions (boss bar, quest progress) are registered by UiModule / QuestModule via
            // SkillServiceImpl.addXpGainListener, so the plugin holds no reference to those objects.
        )
        val itemDefs = ItemDefinitionRegistryImpl()
        val itemInstances = ItemInstanceServiceImpl(itemDefs)
        val enchantments = EnchantmentRegistryImpl()

        val entityProfiles = EntityProfileRegistryImpl()
        entityProfiles.mythicResolver = MythicIntegration.resolver()

        val abilities = AbilityRegistryImpl()
        val abilityService = AbilityServiceImpl(
            abilities, playerStore, skillService, dev.willram.ramrpg.builtin.identity.RamSkills.SORCERY,
        )
        val damagePipeline = DamagePipelineImpl()
        val reforges = ReforgeRegistryImpl()
        val gems = GemRegistryImpl()
        val renderer = PacketItemRendererImpl(itemDefs, itemInstances, stats, enchantments, reforges, gems)

        // economy is Vault-free at construction time (its Vault-backed properties are `by lazy`);
        // questRegistry and quests are pure in-memory/file wiring, same as the player-data store above.
        val economy = dev.willram.ramrpg.core.listeners.EconomyService()
        val questRegistry = dev.willram.ramrpg.core.services.QuestRegistryImpl()
        val questDir = File(dataFolder, "quests")
        if (!questDir.exists()) questDir.mkdirs()
        val quests = dev.willram.ramrpg.core.services.QuestService(
            questRegistry, skillService, economy, playerStore, questDir.toPath(),
        )

        registerServices(
            platform, playerStore, stats, skillRegistry, skillService, itemDefs, itemInstances,
            enchantments, entityProfiles, abilities, abilityService, damagePipeline, renderer,
            reforges, gems, economy, questRegistry, quests,
        )
    }

    /**
     * Registers every RPG subsystem service under its [RpgServiceKeys] key. Must run from [load]
     * (see the comment there) -- RamCore's registry rejects `register(...)` once `loadAll()` has run.
     */
    private fun registerServices(
        platform: dev.willram.ramrpg.core.platform.PlatformScheduler,
        playerStore: dev.willram.ramrpg.core.storage.PlayerStore,
        stats: dev.willram.ramrpg.api.stats.StatService,
        skillRegistry: dev.willram.ramrpg.api.skills.SkillRegistry,
        skillService: dev.willram.ramrpg.api.skills.SkillService,
        itemDefs: dev.willram.ramrpg.api.items.ItemDefinitionRegistry,
        itemInstances: dev.willram.ramrpg.api.items.ItemInstanceService,
        enchantments: dev.willram.ramrpg.api.enchants.EnchantmentRegistry,
        entityProfiles: dev.willram.ramrpg.api.entities.EntityProfileRegistry,
        abilities: dev.willram.ramrpg.api.abilities.AbilityRegistry,
        abilityService: dev.willram.ramrpg.api.abilities.AbilityService,
        damagePipeline: dev.willram.ramrpg.api.combat.DamagePipeline,
        renderer: dev.willram.ramrpg.core.rendering.PacketItemRenderer,
        reforges: dev.willram.ramrpg.api.reforges.ReforgeRegistry,
        gems: dev.willram.ramrpg.api.sockets.GemRegistry,
        economy: dev.willram.ramrpg.core.listeners.EconomyService,
        questRegistry: dev.willram.ramrpg.api.quests.QuestRegistry,
        quests: dev.willram.ramrpg.core.services.QuestService,
    ) {
        val registry = services()
        registry.register(RpgServiceKeys.PLATFORM, platform)
        registry.register(RpgServiceKeys.PLAYER_STORE, playerStore)
        registry.register(RpgServiceKeys.STATS, stats)
        registry.register(RpgServiceKeys.SKILL_REGISTRY, skillRegistry)
        registry.register(RpgServiceKeys.SKILL_SERVICE, skillService)
        registry.register(RpgServiceKeys.ITEM_DEFINITIONS, itemDefs)
        registry.register(RpgServiceKeys.ITEM_INSTANCES, itemInstances)
        registry.register(RpgServiceKeys.ENCHANTMENTS, enchantments)
        registry.register(RpgServiceKeys.ENTITY_PROFILES, entityProfiles)
        registry.register(RpgServiceKeys.ABILITIES, abilities)
        registry.register(RpgServiceKeys.ABILITY_SERVICE, abilityService)
        registry.register(RpgServiceKeys.DAMAGE_PIPELINE, damagePipeline)
        registry.register(RpgServiceKeys.RENDERER, renderer)
        registry.register(RpgServiceKeys.REFORGES, reforges)
        registry.register(RpgServiceKeys.GEMS, gems)
        registry.register(RpgServiceKeys.ECONOMY, economy)
        registry.register(RpgServiceKeys.QUEST_REGISTRY, questRegistry)
        registry.register(RpgServiceKeys.QUESTS, quests)
    }

    override fun enable() {
        Translations.load(this)

        // Content, providers and damage stages: foundational registration into the (already-enabled)
        // services. Damage stages live in CombatModule; the rest are core bootstrap here.
        registerBuiltins()
        applyContentOverrides()
        registerStatProviders()

        // Always-on core listeners with no dedicated subsystem module. Registered against this plugin
        // (a TerminableConsumer), so every subscription -- and ManaRegen's per-player tasks -- is bound
        // and torn down by RamCore on disable. Later WPs modify these listener classes, never this file.
        EquipmentListener(service(RpgServiceKeys.STATS)).register(this)
        XpListener(service(RpgServiceKeys.ENTITY_PROFILES), service(RpgServiceKeys.SKILL_SERVICE), service(RpgServiceKeys.ECONOMY)).register(this)
        val manaRegen = bind(ManaRegen(service(RpgServiceKeys.STATS), service(RpgServiceKeys.PLAYER_STORE), service(RpgServiceKeys.PLATFORM)))
        manaRegen.register(this)
        PacketRenderListener(service(RpgServiceKeys.RENDERER), service(RpgServiceKeys.ITEM_INSTANCES)).register(this)
        AbilityListener(service(RpgServiceKeys.ABILITY_SERVICE)).register(this)
        EntitySpawnListener(service(RpgServiceKeys.ENTITY_PROFILES)).register(this)
        FortuneListener(service(RpgServiceKeys.STATS)).register(this)
        NonCombatXpListener(service(RpgServiceKeys.SKILL_SERVICE)).register(this)
        EnchantingListener(service(RpgServiceKeys.ENCHANTMENTS), service(RpgServiceKeys.ITEM_INSTANCES), service(RpgServiceKeys.ITEM_DEFINITIONS)).register(this)
        DurabilityListener().register(this)
        InventoryRefreshListener(service(RpgServiceKeys.PLATFORM)).register(this)

        skillsCommand = SkillsCommand(
            service(RpgServiceKeys.SKILL_REGISTRY),
            service(RpgServiceKeys.SKILL_SERVICE),
            service(RpgServiceKeys.STATS),
            service(RpgServiceKeys.ENCHANTMENTS),
            service(RpgServiceKeys.ITEM_INSTANCES),
            service(RpgServiceKeys.ITEM_DEFINITIONS),
            service(RpgServiceKeys.REFORGES),
            service(RpgServiceKeys.GEMS),
            service(RpgServiceKeys.PLAYER_STORE),
            service(RpgServiceKeys.ABILITIES),
            service(RpgServiceKeys.ABILITY_SERVICE),
            service(RpgServiceKeys.QUESTS),
            service(RpgServiceKeys.QUEST_REGISTRY),
            reloadContent = ::reloadContent,
        )

        // Per-subsystem module seams (WP-1.7b): each resolves its collaborators from the service
        // registry and binds its listeners/tasks to RamCore. Six are populated; ten are seeded empty
        // for one named later WP each. See RpgModules.
        for (module in RpgModules.all(this)) bindModule(module)

        runCatching { dev.willram.ramrpg.core.config.RamRpgMetrics.register(this) }
        log("<yellow>RamRPG <green>enabled <gray>(rewrite scaffold)")
    }

    override fun disable() {
        // Nothing to tear down by hand: every listener, task and UI holder is bound via
        // bind/bindModule and closed by RamCore's terminable registry in LIFO order, and
        // PlayerDataService (installed in load()) flushes every online player's data on disable.
    }

    @Suppress("UnstableApiUsage")
    override fun registerCommands(commands: Commands) {
        skillsCommand?.register(commands)
    }

    /**
     * Re-applies HOCON content overrides at runtime (the `/skills reload` admin command). Resolves the
     * registries from the service registry rather than fields. A later WP (RpgCommand / ContentModule)
     * grows a fuller reload; this stays the minimal `/skills reload` path.
     */
    private fun reloadContent() {
        val stats = service(RpgServiceKeys.STATS)
        val itemDefs = service(RpgServiceKeys.ITEM_DEFINITIONS)
        val skillRegistry = service(RpgServiceKeys.SKILL_REGISTRY)
        val entityProfiles = service(RpgServiceKeys.ENTITY_PROFILES)
        val enchantments = service(RpgServiceKeys.ENCHANTMENTS)
        val renderer = service(RpgServiceKeys.RENDERER)
        itemDefs.unregisterOwner("ramrpg-override")
        skillRegistry.unregisterOwner("ramrpg-override")
        entityProfiles.unregisterOwner("ramrpg-override")
        enchantments.unregisterOwner("ramrpg-override")
        applyContentOverrides()
        renderer.invalidate()
        for (p in server.onlinePlayers) {
            stats.markDirty(p, StatDirtyReason.WORLD_CHANGED)
        }
    }

    private fun registerBuiltins() {
        BuiltinStats.registerAll(service(RpgServiceKeys.STATS))
        BuiltinSkills.registerAll(service(RpgServiceKeys.SKILL_REGISTRY))
        BuiltinItems.registerAll(service(RpgServiceKeys.ITEM_DEFINITIONS))
        BuiltinEnchants.registerAll(service(RpgServiceKeys.ENCHANTMENTS))
        BuiltinEntities.registerAll(service(RpgServiceKeys.ENTITY_PROFILES))
        BuiltinAbilities.registerAll(service(RpgServiceKeys.ABILITIES), service(RpgServiceKeys.SKILL_SERVICE))
        BuiltinReforges.registerAll(service(RpgServiceKeys.REFORGES))
        BuiltinGems.registerAll(service(RpgServiceKeys.GEMS))
    }

    private fun applyContentOverrides() {
        val contentDir = File(dataFolder, "content")
        if (!contentDir.exists()) contentDir.mkdirs()
        ContentOverrideLoader(contentDir.toPath())
            .apply(
                service(RpgServiceKeys.STATS),
                service(RpgServiceKeys.ITEM_DEFINITIONS),
                service(RpgServiceKeys.SKILL_REGISTRY),
                service(RpgServiceKeys.ENTITY_PROFILES),
                service(RpgServiceKeys.ENCHANTMENTS),
            )
    }

    private fun registerStatProviders() {
        val owner = "ramrpg-builtin"
        val stats = service(RpgServiceKeys.STATS)
        val skillRegistry = service(RpgServiceKeys.SKILL_REGISTRY)
        val skillService = service(RpgServiceKeys.SKILL_SERVICE)
        val itemInstances = service(RpgServiceKeys.ITEM_INSTANCES)
        val itemDefs = service(RpgServiceKeys.ITEM_DEFINITIONS)
        val enchantments = service(RpgServiceKeys.ENCHANTMENTS)
        val reforges = service(RpgServiceKeys.REFORGES)
        val gems = service(RpgServiceKeys.GEMS)
        // WP-2.1c: every item-based provider must receive the definition registry + requirement services
        // so inert items (unmet requirement / zero durability) contribute nothing. Enchantment/Socket
        // providers fail OPEN when their `defs` is null, so passing itemDefs to ALL FOUR here is a
        // security requirement, not a convenience. (Orchestrator bootstrap wiring at merge — the
        // providers are constructed here, not in a module; see the B5 note in the wave-progress memory.)
        val reqServices = ItemRequirementServices(skillRegistry, skillService, stats)
        stats.registerProvider(SkillStatProvider(skillRegistry, skillService), owner)
        stats.registerProvider(EquipmentStatProvider(itemInstances, itemDefs, reqServices), owner)
        stats.registerProvider(EnchantmentStatProvider(itemInstances, enchantments, itemDefs, reqServices), owner)
        stats.registerProvider(ReforgeStatProvider(itemInstances, itemDefs, reforges, reqServices), owner)
        stats.registerProvider(SocketStatProvider(itemInstances, gems, itemDefs, reqServices), owner)
    }

    private fun handleLevelUp(p: org.bukkit.entity.Player, key: dev.willram.ramrpg.api.identity.SkillKey, lvl: Int) {
        val skillRegistry = service(RpgServiceKeys.SKILL_REGISTRY)
        val stats = service(RpgServiceKeys.STATS)
        val def = skillRegistry.get(key)
        val name = def?.displayName ?: Component.text(key.id.value())
        val title = Title.title(
            Component.translatable("ramrpg.skill.level_up").color(NamedTextColor.GOLD),
            Component.translatable(
                "ramrpg.skill.level_up_subtitle",
                name.color(NamedTextColor.YELLOW),
                Component.text(lvl),
            ).color(NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500)),
        )
        p.showTitle(title)
        p.sendMessage(Component.translatable(
            "ramrpg.skill.level_up_message",
            name.color(NamedTextColor.YELLOW),
            Component.text(lvl),
        ).color(NamedTextColor.GOLD))
        p.playSound(p.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        stats.markDirty(p, StatDirtyReason.SKILL_LEVEL_CHANGED)
        applyPlayerAttributes(stats, p)
        // Milestones every 10 levels
        if (lvl > 0 && lvl % 10 == 0) onMilestone(p, key, lvl)
    }

    private fun onMilestone(p: org.bukkit.entity.Player, key: dev.willram.ramrpg.api.identity.SkillKey, lvl: Int) {
        val def = service(RpgServiceKeys.SKILL_REGISTRY).get(key) ?: return
        val reward = lvl * 100.0
        p.sendMessage(Component.translatable(
            "ramrpg.skill.milestone",
            def.displayName.color(NamedTextColor.YELLOW),
            Component.text(lvl).color(NamedTextColor.GOLD),
        ).color(NamedTextColor.LIGHT_PURPLE))
        p.playSound(p.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f)
        p.world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, p.location.add(0.0, 1.5, 0.0), 30, 0.5, 0.5, 0.5)
        val economy = service(RpgServiceKeys.ECONOMY)
        if (economy.enabled) {
            if (economy.deposit(p, reward)) {
                p.sendMessage(Component.text("+$reward coins", NamedTextColor.GREEN))
            }
        }
    }
}
