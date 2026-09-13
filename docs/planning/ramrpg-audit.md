# RamRPG Roadmap Status Audit (2026-09-12)

Build: PASS (Maven 3.9.15, JDK 25, Kotlin 2.3.20). Tests: 26/26 pass across 11 classes. LOC: main 5146 (core 3100, builtin 969, api 783, utils 13), test 475.
pom deps: paper-api 26.1.2 (provided), kotlin-stdlib-jdk8, **dev.willram:RamCore:2.0.0 (compile scope — WRONG: should be ramcore-api provided/compileOnly; RamCore ships as its own runtime plugin)**, ProtocolLib 5.3.0-SNAPSHOT (provided), VaultAPI 1.7 (provided), Mythic-Dist 5.6.1 (provided), junit-jupiter 5.11.4 (test), bstats-bukkit 3.0.2 (compile).
Local m2 has ramcore, ramcore-api, ramcore-kotlin, ramcore-nms, ramcore-protocol, ramcore-test all at 2.0.0.

## Overall status table

| Task | Status | Evidence |
|---|---|---|
| 0.1 Design doc | NOT STARTED | No `docs/` dir. |
| 1.1 Loot via RamCore | NOT STARTED | No `ramcore.loot` usage; `api/entities/Entities.kt:16-39` private `LootEntry`/`EntityProfile.loot/lootPool/lootRolls`; `core/listeners/LootListener.kt:37-68` rolls itself; `LootListener.kt:29` creates drops with no seed. |
| 1.2 Quests via RamCore | NOT STARTED (GUI half done) | No `ramcore.objective`/`reward`; `QuestServiceImpl.kt:57-75` (class named `QuestService`) hand-tracks `PlayerRpgData.questProgress/questCompleted`. `QuestsGui.kt` already on RamCore `Gui`/`Item`. |
| 1.3 Cooldowns | NOT STARTED | `AbilityServiceImpl.kt:52-53,78-83` private `ConcurrentHashMap<CdKey,Long>`; no CooldownTracker; no lore/actionbar cooldown hook. |
| 1.4 PlayerStore interface + async | DONE (in uncommitted diff) | `PlayerStore.kt:35-50` interface; `FilePlayerStore` sole prod impl (RamCore `FileDataRepository`/`Repositories.jsonByUuid`); `PlayerStoreListener.kt` async `AsyncPlayerPreLoginEvent` load, async save on quit, `DataItem.markDirty()`. |
| 1.5 HOCON content loading | NOT STARTED | `ContentOverrides.kt` is JSON patch-only (`registry.get(key) ?: continue`), reads `dataFolder/content/{stats,items,skills,entities,enchants}/*.json` via `Repositories.jsonByString`. No ContentLoader, no Effect schema, no EffectActionRegistry, no `/rpg validate|reload` diff (only `/skills reload`). No `.conf` shipped. |
| 1.6 Menus/UI/rendering | PARTIAL | Stats/Skills/Quests GUIs on RamCore menu. `ActionBarUi`/`BossBarUi`/`DamageIndicatorStage` hand-rolled, no `ramcore.presentation`/`display`. `PacketItemRenderer` covers bundle+shulker; item frames + dropped item entities unrendered. |
| 1.7 Wiring | NOT STARTED | `RamRPG.kt:89-113` ~17 `lateinit var` + companion `get()`; `SkillsCommand.kt:255,275,283,413` reach through singleton. |
| 2.1 Item level/req/quality | NOT STARTED | No `itemLevel`/`requirements`/`equipSlots`/`quality`/`craftedBy` in `api/items/Items.kt`. `EquipmentListener` checks nothing. |
| 2.2 Durability | UNDECIDED | `DurabilityListener.kt` (untracked, 13 lines) globally cancels `PlayerItemDamageEvent`. |
| 2.3 Elemental | NOT STARTED | `DamageContext.components`/`DamageTypeKey`/`ELEMENTAL_BREAKDOWN` scaffolding only. |
| 3.1–3.4 Stations | NOT STARTED | No `api/crafting`. Upgrade/reforge/socket are `/skills` subcommands in `SkillsCommand.kt`. `EnchantingListener.kt` drives vanilla table/anvil. `RPGEnchantment` lacks `conflictsWith`/`skillRequirement`. |
| 4.1–4.2 Buffs/potions | NOT STARTED | No `api/buffs`; no `OnConsume` trigger. |
| 5.1–5.3 Perks/sets | NOT STARTED | No `api/perks`, `api/sets`. |
| 6.1–6.4 Mobs | NOT STARTED | `BuiltinEntities.kt` 27 specs × 4 tiers = 135 profiles in Kotlin; `EntitySpawnListener` flat HP/DEF/DMG + boss glow; `XpListener` 5× boss bonus. No affixes/region/MobDefinition/encounter. |
| Phase 7 | NOT STARTED | `EconomyService.kt` thin Vault deposit/balance wrapper only. |

## Service wiring (RamRPG.kt)
All eager in `enable()`, `lateinit var`: platform (RamCorePlatformScheduler), playerStore (FilePlayerStore), stats (StatServiceImpl), skillRegistry, skillService(skillRegistry, playerStore, onLevelUp, onXpGain), itemDefs, itemInstances(itemDefsImpl), enchantments, entityProfiles (+ MythicIntegration.resolver()), abilities, abilityService(abilities, playerStore, skillService, SORCERY), damagePipeline, renderer(itemDefs, itemInstances, stats, enchantments, reforges, gems), reforges, gems, economy, questRegistry, quests(questRegistry, skillService, economy, playerStore); private lateinit bossBarUi/actionBarUi/manaRegen/equipmentListener; skillsCommand nullable. Companion singleton `i` set in `init`.

## api/ surface
- abilities: AbilityTrigger(sealed), ResourceCost, CooldownScope, Cooldown, Requirement, AbilityContext, AbilityResult, Ability, AbilityRegistry, AbilityService
- combat: DamageTag, DamageContext, DamageStage, DamagePipeline, DamagePriority
- effects: ScalingContext, ScalingFormula/Scaling, InteractType, BlockMatcher(s), EffectTrigger(sealed: OnEquip, OnHit, OnHurt, OnKill, OnInteract, OnBlockBreak, Tick, Custom), EffectContext, EffectAction, Condition, Effect(sealed: StatEffect/DamagePipelineEffect/TriggeredEffect)
- enchants: EnchantmentRarity, EnchantingContext, RPGEnchantment, EnchantmentRegistry
- entities: LootEntry, EntityProfile, EntityProfileRegistry
- identity: value-class keys StatKey, SkillKey, ItemKey, EnchantmentKey, AbilityKey, EffectKey, EntityProfileKey, DamageTypeKey, XpSourceKey; RamRpgNamespace
- items: Rarity, ItemCategory, ReforgeKey, SocketData, ItemIdentity, ItemInstanceData, ItemInstanceInit, LoreContext, LoreSection(sealed)/LoreTemplate, StatRoll, ItemDefinition, ItemDefinitionRegistry, ItemInstanceService, ItemSchema; RarityRules
- quests: QuestKey, QuestGoal(sealed), QuestReward(sealed), QuestDefinition, QuestRegistry
- reforges: ReforgeIds, ReforgeDefinition, ReforgeRegistry
- skills: XpCurve/XpCurves, SkillReward(sealed), SkillDefinition, XpContext, XpSource, SkillRegistry, SkillService
- sockets: GemKey, GemDefinition, GemRegistry
- stats: StatFormat, StatDefinition, ModifierOperation, SourceType, ModifierSource, StatModifier, StatSnapshot, StatDirtyReason, StatContext, StatProvider, StatService

## Storage
PlayerRpgData (extends RamCore DataItem): skillLevels, skillXp (keyed by SkillKey string), currentMana, maxManaCache, lastActiveSkillId, questProgress, questCompleted, lastDailyReset, disabledAbilities (new). ItemSchema.CURRENT = 1. `ItemSchemaMigrator` interface exists (`ItemInstanceServiceImpl.kt:145-152`) but only NOOP impl; migration path never exercised. PlayerStore: get/require/put/remove/all; FilePlayerStore adds loadAsync/saveAsync/saveAll/close.

## Effects model
StatEffect → StatService via providers (SkillStatProvider, EquipmentStatProvider, EnchantmentStatProvider, ReforgeStatProvider, SocketStatProvider). DamagePipelineEffect → consumed by EnchantDamageStage/EnchantPostHitStage scanning equipped items. **TriggeredEffect defined but consumed nowhere** (no dispatcher). LoreSection.EffectsHint just prints key.

## Damage stages (registration order)
WeaponBase 100, Strength 200, EnchantDamage(attacker) 300(+99), CritRoll 500, ArmorMitigation 1100, TrueDefense 1200, EnchantDamage(defender) 1300(+99), Lifesteal 1600, EnchantPostHit 1601, Ferocity 1700, DamageIndicator 1900, Apply 2000 (no-op; CombatListener writes finalDamage). Unimplemented priority slots: EFFECT_OFFENSE 400, ELEMENTAL_BREAKDOWN 600, ABILITY_MOD 700, SHIELDS 1400, THORNS 1500. Dead `IndicatorStage` class in DamageStages.kt:119-123. Pipeline re-runs up to 8 ferocity chains.

## Tests (all pure JUnit5, no server)
ContentOverrideLoaderTest, DamagePipelineTest (sort only), EnchantmentEffectTest, ItemDtoTest, PdcSchemaStabilityTest (forward-compat only, no real migration), RarityRulesTest, RenderCacheTest, SkillXpCurveTest, StatRollTest, StatServiceTest (reimplements aggregation, doesn't call StatServiceImpl), UpgradeCostTest.

## Uncommitted work (17 modified + 2 untracked + roadmap doc; +220/-24)
1. Paper API nullability fixes (equipment returns air not null) in ReforgeStatProvider, SocketStatProvider, StatProviders, EnchantDamageStages.
2. PlayerStoreListener: sync PlayerLoginEvent → async AsyncPlayerPreLoginEvent.
3. EnchantingListener anvil rename API fix.
4. NonCombatXpListener nullcheck removal.
5. Ability feature slice: Ability.unlockSkill/unlockLevel, AbilityService.isDisabled/setDisabled, PlayerRpgData.disabledAbilities, mana spend grants Sorcery XP, `/skills ability list|toggle`; VeinMiner/Quickshot skill gates; VeinMiner grants mining XP.
6. PacketRenderListener: merchant trade rendering + inbound SET_CREATIVE_SLOT canonicalizer.
7. DurabilityListener (global cancel), InventoryRefreshListener (updateInventory 1 tick after click/drag — 1.21 client prediction workaround).

## Resources
paper-plugin.yml: version 1.0.0-SNAPSHOT (pom says 2.0.0 — mismatch), api-version 1.21, folia-supported true, deps RamCore + ProtocolLib required, Vault + MythicMobs optional. lang/en_us.json: 14 keys, all `ramrpg.stat.*`; everything else hardcoded Component.text.
