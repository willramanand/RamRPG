package dev.willram.ramrpg

import dev.willram.ramcore.RamPlugin
import dev.willram.ramrpg.api.abilities.AbilityRegistry
import dev.willram.ramrpg.api.abilities.AbilityService
import dev.willram.ramrpg.api.combat.DamagePipeline
import dev.willram.ramrpg.api.combat.DamagePriority
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.StatDirtyReason
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.builtin.abilities.BuiltinAbilities
import dev.willram.ramrpg.builtin.enchants.BuiltinEnchants
import dev.willram.ramrpg.builtin.entities.BuiltinEntities
import dev.willram.ramrpg.builtin.items.BuiltinItems
import dev.willram.ramrpg.builtin.reforges.BuiltinReforges
import dev.willram.ramrpg.builtin.skills.BuiltinSkills
import dev.willram.ramrpg.builtin.sockets.BuiltinGems
import dev.willram.ramrpg.builtin.stats.ApplyStage
import dev.willram.ramrpg.builtin.stats.ArmorMitigationStage
import dev.willram.ramrpg.builtin.stats.BuiltinStats
import dev.willram.ramrpg.builtin.stats.CritRollStage
import dev.willram.ramrpg.builtin.stats.DamageIndicatorStage
import dev.willram.ramrpg.builtin.stats.EnchantDamageStage
import dev.willram.ramrpg.builtin.stats.EnchantPostHitStage
import dev.willram.ramrpg.builtin.stats.FerocityStage
import dev.willram.ramrpg.builtin.stats.LifestealStage
import dev.willram.ramrpg.builtin.stats.StrengthStage
import dev.willram.ramrpg.builtin.stats.TrueDefenseStage
import dev.willram.ramrpg.builtin.stats.WeaponBaseStage
import dev.willram.ramrpg.core.config.ContentOverrideLoader
import dev.willram.ramrpg.core.config.Translations
import dev.willram.ramrpg.core.listeners.AbilityListener
import dev.willram.ramrpg.core.listeners.ActionBarUi
import dev.willram.ramrpg.core.listeners.BossBarUi
import dev.willram.ramrpg.core.listeners.CombatListener
import dev.willram.ramrpg.core.listeners.EconomyService
import dev.willram.ramrpg.core.listeners.EnchantingListener
import dev.willram.ramrpg.core.listeners.EntitySpawnListener
import dev.willram.ramrpg.core.listeners.EquipmentListener
import dev.willram.ramrpg.core.listeners.FortuneListener
import dev.willram.ramrpg.core.listeners.LootListener
import dev.willram.ramrpg.core.listeners.ManaRegen
import dev.willram.ramrpg.core.listeners.MythicIntegration
import dev.willram.ramrpg.core.listeners.NonCombatXpListener
import dev.willram.ramrpg.core.listeners.PlayerStoreListener
import dev.willram.ramrpg.core.listeners.SkillsCommand
import dev.willram.ramrpg.core.listeners.XpListener
import dev.willram.ramrpg.core.platform.PlatformScheduler
import dev.willram.ramrpg.core.platform.RamCorePlatformScheduler
import dev.willram.ramrpg.core.rendering.PacketItemRenderer
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
import dev.willram.ramrpg.core.services.SkillRegistryImpl
import dev.willram.ramrpg.core.services.SkillServiceImpl
import dev.willram.ramrpg.core.services.SkillStatProvider
import dev.willram.ramrpg.core.services.SocketStatProvider
import dev.willram.ramrpg.core.services.StatServiceImpl
import dev.willram.ramrpg.core.storage.FilePlayerStore
import dev.willram.ramrpg.core.storage.PlayerStore
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import java.io.File
import java.time.Duration

class RamRPG : RamPlugin() {

    lateinit var platform: PlatformScheduler
    lateinit var playerStore: PlayerStore
    lateinit var stats: StatService
    lateinit var skillRegistry: SkillRegistry
    lateinit var skillService: SkillService
    lateinit var itemDefs: ItemDefinitionRegistry
    lateinit var itemInstances: ItemInstanceService
    lateinit var enchantments: EnchantmentRegistry
    lateinit var entityProfiles: EntityProfileRegistry
    lateinit var abilities: AbilityRegistry
    lateinit var abilityService: AbilityService
    lateinit var damagePipeline: DamagePipeline
    lateinit var renderer: PacketItemRenderer
    lateinit var reforges: ReforgeRegistry
    lateinit var gems: GemRegistry

    private var skillsCommand: SkillsCommand? = null
    private lateinit var bossBarUi: BossBarUi
    private lateinit var actionBarUi: ActionBarUi
    private lateinit var manaRegen: ManaRegen
    private lateinit var equipmentListener: EquipmentListener
    lateinit var economy: EconomyService
    lateinit var questRegistry: dev.willram.ramrpg.api.quests.QuestRegistry
    lateinit var quests: dev.willram.ramrpg.core.services.QuestService
    var mythicMobsEnabled: Boolean = false

    companion object {
        private lateinit var i: RamRPG
        fun get(): RamRPG = i
    }

    init { i = this }

    override fun load() {
        mythicMobsEnabled = server.pluginManager.getPlugin("MythicMobs") != null
    }

    override fun enable() {
        Translations.load(this)
        platform = RamCorePlatformScheduler()
        val storeDir = File(dataFolder, "playerdata")
        if (!storeDir.exists()) storeDir.mkdirs()
        val fileStore = FilePlayerStore(storeDir.toPath())
        playerStore = fileStore

        stats = StatServiceImpl()
        skillRegistry = SkillRegistryImpl()
        skillService = SkillServiceImpl(
            skillRegistry, playerStore,
            onLevelUp = ::onSkillLevelUp,
            onXpGain = { p, k, amt ->
                if (::bossBarUi.isInitialized) bossBarUi.onXpGain(p, k, amt)
                if (::quests.isInitialized) quests.onSkillXp(p, k, amt.toInt())
            },
        )

        val itemDefsImpl = ItemDefinitionRegistryImpl()
        itemDefs = itemDefsImpl
        itemInstances = ItemInstanceServiceImpl(itemDefsImpl)
        enchantments = EnchantmentRegistryImpl()

        val entityProfilesImpl = EntityProfileRegistryImpl()
        entityProfilesImpl.mythicResolver = MythicIntegration.resolver()
        entityProfiles = entityProfilesImpl

        abilities = AbilityRegistryImpl()
        abilityService = AbilityServiceImpl(abilities, playerStore)
        damagePipeline = DamagePipelineImpl()
        reforges = ReforgeRegistryImpl()
        gems = GemRegistryImpl()
        renderer = PacketItemRendererImpl(itemDefs, itemInstances, stats, enchantments, reforges, gems)

        registerBuiltins()
        applyContentOverrides()
        registerStatProviders()
        registerDamageStages()
        registerListeners(fileStore)

        skillsCommand = SkillsCommand(skillRegistry, skillService, stats, enchantments, itemInstances, itemDefs, reforges, gems, playerStore)

        runCatching { dev.willram.ramrpg.core.config.RamRpgMetrics.register(this) }
        log("<yellow>RamRPG <green>enabled <gray>(rewrite scaffold)")
    }

    override fun disable() {
        if (::manaRegen.isInitialized) manaRegen.shutdown()
        if (::actionBarUi.isInitialized) actionBarUi.shutdown()
        if (::bossBarUi.isInitialized) bossBarUi.shutdown()
        if (::playerStore.isInitialized) {
            val store = playerStore as? FilePlayerStore
            store?.saveAll()
            store?.close()
        }
    }

    @Suppress("UnstableApiUsage")
    override fun registerCommands(commands: Commands) {
        skillsCommand?.register(commands)
    }

    fun reloadContent() {
        itemDefs.unregisterOwner("ramrpg-override")
        skillRegistry.unregisterOwner("ramrpg-override")
        entityProfiles.unregisterOwner("ramrpg-override")
        enchantments.unregisterOwner("ramrpg-override")
        applyContentOverrides()
        renderer.invalidate()
        for (p in server.onlinePlayers) {
            stats.markDirty(p, dev.willram.ramrpg.api.stats.StatDirtyReason.WORLD_CHANGED)
        }
    }

    private fun registerBuiltins() {
        BuiltinStats.registerAll(stats)
        BuiltinSkills.registerAll(skillRegistry)
        BuiltinItems.registerAll(itemDefs)
        BuiltinEnchants.registerAll(enchantments)
        BuiltinEntities.registerAll(entityProfiles)
        BuiltinAbilities.registerAll(abilities)
        BuiltinReforges.registerAll(reforges)
        BuiltinGems.registerAll(gems)
    }

    private fun applyContentOverrides() {
        val contentDir = File(dataFolder, "content")
        if (!contentDir.exists()) contentDir.mkdirs()
        ContentOverrideLoader(contentDir.toPath())
            .apply(stats, itemDefs, skillRegistry, entityProfiles, enchantments)
    }

    private fun registerStatProviders() {
        val owner = "ramrpg-builtin"
        stats.registerProvider(SkillStatProvider(skillRegistry, skillService), owner)
        stats.registerProvider(EquipmentStatProvider(itemInstances, itemDefs), owner)
        stats.registerProvider(EnchantmentStatProvider(itemInstances, enchantments), owner)
        stats.registerProvider(ReforgeStatProvider(itemInstances, itemDefs, reforges), owner)
        stats.registerProvider(SocketStatProvider(itemInstances, gems), owner)
    }

    private fun registerDamageStages() {
        damagePipeline.register(WeaponBaseStage(stats))
        damagePipeline.register(StrengthStage(stats))
        damagePipeline.register(EnchantDamageStage(itemInstances, enchantments, DamagePriority.ENCHANT_OFFENSE, true))
        damagePipeline.register(CritRollStage(stats))
        damagePipeline.register(ArmorMitigationStage(stats))
        damagePipeline.register(TrueDefenseStage(stats))
        damagePipeline.register(EnchantDamageStage(itemInstances, enchantments, DamagePriority.ENCHANT_DEFENSE, false))
        damagePipeline.register(LifestealStage(stats))
        damagePipeline.register(EnchantPostHitStage(itemInstances, enchantments))
        damagePipeline.register(FerocityStage(stats))
        damagePipeline.register(DamageIndicatorStage())
        damagePipeline.register(ApplyStage())
    }

    private fun registerListeners(fileStore: FilePlayerStore) {
        PlayerStoreListener(fileStore).register()
        equipmentListener = EquipmentListener(stats).also { it.register() }
        CombatListener(damagePipeline).register()
        economy = EconomyService()
        questRegistry = dev.willram.ramrpg.core.services.QuestRegistryImpl()
        quests = dev.willram.ramrpg.core.services.QuestService(questRegistry, skillService, economy, playerStore)
        dev.willram.ramrpg.builtin.quests.BuiltinQuests.registerAll(questRegistry)
        XpListener(entityProfiles, skillService, economy).register()
        dev.willram.ramrpg.core.listeners.QuestProgressListener(quests, entityProfiles).register()
        manaRegen = ManaRegen(stats, playerStore, platform).also { it.register() }
        PacketRenderListener(renderer).register()
        AbilityListener(abilityService).register()
        actionBarUi = ActionBarUi(stats, playerStore, platform).also { it.register() }
        bossBarUi = BossBarUi(skillService, skillRegistry, playerStore).also { it.register() }
        EntitySpawnListener(entityProfiles).register()
        FortuneListener(stats).register()
        NonCombatXpListener(skillService).register()
        LootListener(entityProfiles, itemInstances, itemDefs).register()
        EnchantingListener(enchantments, itemInstances, itemDefs).register()
    }

    private fun onSkillLevelUp(p: org.bukkit.entity.Player, key: dev.willram.ramrpg.api.identity.SkillKey, lvl: Int) {
        val def = skillRegistry.get(key)
        val name = def?.displayName ?: Component.text(key.id.value())
        val title = Title.title(
            Component.text("Level Up!", NamedTextColor.GOLD),
            Component.text("")
                .append(name.color(NamedTextColor.YELLOW))
                .append(Component.text(" Lv $lvl", NamedTextColor.GRAY)),
            Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500)),
        )
        p.showTitle(title)
        p.sendMessage(Component.text("")
            .append(name.color(NamedTextColor.YELLOW))
            .append(Component.text(" → Lv $lvl", NamedTextColor.GOLD)))
        p.playSound(p.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f)
        stats.markDirty(p, StatDirtyReason.SKILL_LEVEL_CHANGED)
        if (::equipmentListener.isInitialized) equipmentListener.applyAttributes(p)
        // Milestones every 10 levels
        if (lvl > 0 && lvl % 10 == 0) onMilestone(p, key, lvl)
    }

    private fun onMilestone(p: org.bukkit.entity.Player, key: dev.willram.ramrpg.api.identity.SkillKey, lvl: Int) {
        val def = skillRegistry.get(key) ?: return
        val reward = lvl * 100.0
        p.sendMessage(Component.text("")
            .append(Component.text("Milestone! ", NamedTextColor.LIGHT_PURPLE))
            .append(def.displayName.color(NamedTextColor.YELLOW))
            .append(Component.text(" Lv $lvl", NamedTextColor.GOLD)))
        p.playSound(p.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f)
        p.world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, p.location.add(0.0, 1.5, 0.0), 30, 0.5, 0.5, 0.5)
        if (::economy.isInitialized && economy.enabled) {
            if (economy.deposit(p, reward)) {
                p.sendMessage(Component.text("+$reward coins", NamedTextColor.GREEN))
            }
        }
    }

    fun refreshAttributes(p: org.bukkit.entity.Player) {
        if (::equipmentListener.isInitialized) equipmentListener.applyAttributes(p)
    }
}
