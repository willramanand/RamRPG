/**
 * WP-1.7a: one [ServiceKey] per RPG subsystem service, registered into RamCore's
 * [dev.willram.ramcore.service.ServiceRegistry] (`RamPlugin.services()`) from
 * [dev.willram.ramrpg.RamRPG.load]. This is pure DI *prep*: every key here resolves to exactly the
 * same instance [dev.willram.ramrpg.RamRPG] already exposes through its `lateinit var` fields and its
 * [dev.willram.ramrpg.RamRPG.get] singleton. Nothing consumes these keys via constructor injection
 * yet -- that migration is WP-1.7b's job.
 *
 * Scope: the ~18 real subsystem services (registries + services), not the private per-player UI
 * holders (`BossBarUi`, `ActionBarUi`, `ManaRegen`, `EquipmentListener`) which stay plugin-internal.
 *
 * Deliberately NOT keyed here: `RewardActionFactories` (`RamRPG.rewardFactories`). RamCore's
 * `SimpleServiceRegistry` only accepts `register(...)` calls made from inside
 * [dev.willram.ramrpg.RamRPG.load] (it refuses registration once `loadAll()` runs, which
 * `RamPlugin#onLoad` calls immediately after `load()` returns -- i.e. before any plugin, Vault
 * included, has enabled). Building `RewardActionFactories` requires evaluating
 * `EconomyService.ramCoreEconomy`, which lazily probes Vault; forcing that probe during `load()`
 * would permanently freeze Vault detection to "absent" on any server that actually has Vault
 * installed. `rewardFactories` is still constructed in `enable()`, unchanged, just without a key.
 */
package dev.willram.ramrpg.core.services

import dev.willram.ramcore.service.ServiceKey
import dev.willram.ramrpg.api.abilities.AbilityRegistry
import dev.willram.ramrpg.api.abilities.AbilityService
import dev.willram.ramrpg.api.combat.DamagePipeline
import dev.willram.ramrpg.api.crafting.CraftingService
import dev.willram.ramrpg.api.crafting.RecipeRegistry
import dev.willram.ramrpg.api.crafting.StationRegistry
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.quests.QuestRegistry
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.listeners.EconomyService
import dev.willram.ramrpg.core.platform.PlatformScheduler
import dev.willram.ramrpg.core.rendering.PacketItemRenderer
import dev.willram.ramrpg.core.storage.PlayerStore

object RpgServiceKeys {
    val PLATFORM: ServiceKey<PlatformScheduler> = ServiceKey.of("rpg-platform", PlatformScheduler::class.java)
    val PLAYER_STORE: ServiceKey<PlayerStore> = ServiceKey.of("rpg-player-store", PlayerStore::class.java)
    val STATS: ServiceKey<StatService> = ServiceKey.of("rpg-stats", StatService::class.java)
    val SKILL_REGISTRY: ServiceKey<SkillRegistry> = ServiceKey.of("rpg-skill-registry", SkillRegistry::class.java)
    val SKILL_SERVICE: ServiceKey<SkillService> = ServiceKey.of("rpg-skill-service", SkillService::class.java)
    val ITEM_DEFINITIONS: ServiceKey<ItemDefinitionRegistry> =
        ServiceKey.of("rpg-item-definitions", ItemDefinitionRegistry::class.java)
    val ITEM_INSTANCES: ServiceKey<ItemInstanceService> =
        ServiceKey.of("rpg-item-instances", ItemInstanceService::class.java)
    val ENCHANTMENTS: ServiceKey<EnchantmentRegistry> = ServiceKey.of("rpg-enchantments", EnchantmentRegistry::class.java)
    val ENTITY_PROFILES: ServiceKey<EntityProfileRegistry> =
        ServiceKey.of("rpg-entity-profiles", EntityProfileRegistry::class.java)
    val ABILITIES: ServiceKey<AbilityRegistry> = ServiceKey.of("rpg-abilities", AbilityRegistry::class.java)
    val ABILITY_SERVICE: ServiceKey<AbilityService> = ServiceKey.of("rpg-ability-service", AbilityService::class.java)
    val DAMAGE_PIPELINE: ServiceKey<DamagePipeline> = ServiceKey.of("rpg-damage-pipeline", DamagePipeline::class.java)
    val RENDERER: ServiceKey<PacketItemRenderer> = ServiceKey.of("rpg-renderer", PacketItemRenderer::class.java)
    val REFORGES: ServiceKey<ReforgeRegistry> = ServiceKey.of("rpg-reforges", ReforgeRegistry::class.java)
    val GEMS: ServiceKey<GemRegistry> = ServiceKey.of("rpg-gems", GemRegistry::class.java)
    val ECONOMY: ServiceKey<EconomyService> = ServiceKey.of("rpg-economy", EconomyService::class.java)
    val QUEST_REGISTRY: ServiceKey<QuestRegistry> = ServiceKey.of("rpg-quest-registry", QuestRegistry::class.java)
    val QUESTS: ServiceKey<QuestService> = ServiceKey.of("rpg-quests", QuestService::class.java)

    // WP-3.1b: the crafting subsystem keys (RecipeRegistry / StationRegistry / CraftingService).
    //
    // These are DECLARED here (typed, house-style ServiceKey.of ids) but are deliberately NOT part of
    // [all]. Unlike the 18 above -- which [dev.willram.ramrpg.RamRPG.load] constructs and registers into
    // the RamCore ServiceRegistry, and which [RpgServiceGraph] + ServiceWiringTest/ServiceRegistrationOrderTest
    // enumerate as exactly that load()-time set -- the crafting trio is constructed in
    // [dev.willram.ramrpg.core.modules.CraftingModule.setup] (during enable(), i.e. AFTER load()).
    // RamCore's SimpleServiceRegistry refuses register(...) once loadAll() has run (RamPlugin#onLoad runs
    // it right after load() returns; see ServiceWiringTest's "registration after loadAll is rejected"),
    // so a module CANNOT register them into the ServiceRegistry, and this WP may not touch RamRPG.kt (B5).
    // Adding them to [all] would therefore (a) break those wiring tests' exact-18 assertions and (b) claim
    // a load()-time registration that does not exist. Instead CraftingModule wires them locally (the
    // SetRegistry precedent). To expose them via ServiceContext for downstream WPs (3.1c/3.3a/c/d), the
    // orchestrator promotes their construction into load()/registerServices at merge -- the same
    // merge-time RamRPG.kt seam WP-2.1c's providers use -- at which point they join [all], [RpgServiceGraph]
    // and the wiring tests. See the WP-3.1b report's content-pipeline gap.
    val RECIPE_REGISTRY: ServiceKey<RecipeRegistry> = ServiceKey.of("rpg-recipe-registry", RecipeRegistry::class.java)
    val STATION_REGISTRY: ServiceKey<StationRegistry> = ServiceKey.of("rpg-station-registry", StationRegistry::class.java)
    val CRAFTING_SERVICE: ServiceKey<CraftingService> = ServiceKey.of("rpg-crafting-service", CraftingService::class.java)

    /** Every declared key, for wiring diagnostics and tests. */
    fun all(): Set<ServiceKey<*>> = setOf(
        PLATFORM, PLAYER_STORE, STATS, SKILL_REGISTRY, SKILL_SERVICE,
        ITEM_DEFINITIONS, ITEM_INSTANCES, ENCHANTMENTS, ENTITY_PROFILES,
        ABILITIES, ABILITY_SERVICE, DAMAGE_PIPELINE, RENDERER, REFORGES, GEMS,
        ECONOMY, QUEST_REGISTRY, QUESTS,
        RECIPE_REGISTRY, STATION_REGISTRY, CRAFTING_SERVICE,
    )
}
