# RamCore Capability Audit (for RamRPG)

Audited: RamCore `C:\repos\RamCore` (git HEAD `a83a1a1`, version `2.0.0`) against RamRPG's roadmap
`C:\repos\RamRPG\RAMRPG_ROADMAP_PROMPT.md`. Verified against source under `ramcore-api` (and
`ramcore-protocol`/`ramcore-kotlin`/`ramcore-test` where relevant), cross-checked with
`docs/API.md`, `docs/MODULE_BOUNDARIES.md`, `docs/ROADMAP_EXECUTION_PLAN.md`, `RELEASE_READINESS.md`,
`todo.md`, and the ADRs.

## 0. Critical finding — RamRPG is not building against current RamCore

RamRPG's `pom.xml` (`C:\repos\RamRPG\pom.xml:117-120`) depends on:

```xml
<groupId>dev.willram</groupId>
<artifactId>RamCore</artifactId>
<version>2.0.0</version>
```

This resolves locally to `C:\Users\willr\.m2\repository\dev\willram\ramcore\2.0.0\RamCore-2.0.0.jar`,
**built/installed 2026-05-09** — the old single-module Maven shaded jar, from *before* RamCore's
entire 2.x roadmap execution. RamCore's Gradle multi-module split and almost everything in the
capability matrix below (persistence `store`, `playerdata`, locale, `input`, Vault/PlaceholderAPI
bridges, `ContentLoader`/specs, `stat`, `ability`, `dialogue`, resource packs, hot-reload,
`RealTimeScheduler`, `worldinstance`, ability targeting/telegraphs) landed **2026-09-10 to 09-11**
(`docs/ROADMAP_EXECUTION_PLAN.md` outcomes) — four months later. RamCore's own build now publishes
`dev.willram:ramcore-api:2.0.0`, `ramcore-kotlin`, `ramcore-nms`, `ramcore-protocol`, `ramcore-test`
(all present in `~/.m2/repository/dev/willram/ramcore-*`, built 2026-09-10/11) via
`./gradlew publishToMavenLocal`, or `com.github.willramanand.RamCore:ramcore-api:<tag>` via JitPack —
**there is no `dev.willram:RamCore` artifact in the current build at all.**

RamRPG's own source (`api/entities/Entities.kt`, `api/quests/Quests.kt`,
`core/services/QuestServiceImpl.kt`) still contains the RPG-side duplicates the roadmap prompt
describes, confirming Phase 1 of `RAMRPG_ROADMAP_PROMPT.md` has not started. RamRPG's last commit
(`942e0b3`, "2.0 modifications") and last build (`target/RamRPG-2.0.0.jar`, 2026-05-10) predate all of
RamCore's Phase A-D work.

**Everything below describes what RamCore *has on disk today* and is ready to consume — none of it
is yet reachable from RamRPG until the `pom.xml` dependency is repointed at `ramcore-api` (plus
`ramcore-kotlin` for the DSL, `ramcore-test` for tests) at `2.0.0` from `mavenLocal`/JitPack.**

## 1. Capability matrix

Legend: **E**=EXISTS (fully), **P**=PARTIAL, **M**=MISSING. Stability tags per `docs/MODULE_BOUNDARIES.md`.

| Area | Status | Package / key classes (file) | Stability |
| --- | --- | --- | --- |
| Loot | **E** | `dev.willram.ramcore.loot` - `LootTable`, `LootPool`, `LootEntry`, `LootCondition`, `LootFunction`, `LootContext`, `LootGenerator`, `InstancedLoot`, `LootClaimPolicy`, `LootInstanceStore`/`PersistentLootInstanceStore` (`ramcore-api/.../loot/*.java`) | Stable |
| Objective | **E** | `dev.willram.ramcore.objective` - `ObjectiveDefinition`, `ObjectiveTask`, `ObjectiveTracker`, `ObjectiveProgress`, `ObjectiveProgressStore` (`ramcore-api/.../objective/*.java`) | Stable |
| Reward | **E** | `dev.willram.ramcore.reward` - `RewardEngine`, `RewardAction` (functional, extension point), `RewardActionFactory`/`RewardActionFactories` (config-driven registry), `RewardActions` built-ins (`money`, `command`, `message`, `permission-node-check`; **`item` intentionally left to consumers** - exactly RamRPG's "RPG item instance" need) | Stable (bridges experimental) |
| Cooldown | **E** | `dev.willram.ramcore.cooldown` - `CooldownTracker<K>`, `Cooldown`, `CooldownMap`, `CooldownStore`, `CooldownExpiryListener` | Stable |
| Menu | **E** | `dev.willram.ramcore.menu` - `Gui`, `Item`, `PaginatedMenu<T>`, `MenuSession` (now supports `suspend(InputRequest)`/reopen for text input), `MenuView`, `MenuState` | Stable |
| Presentation | **E** | `dev.willram.ramcore.presentation` - `PresentationEffect`, `PresentationEffects` (titles/actionbar/bossbar/sound/particle/etc.), `PresentationContext` (audiences + `TaskContext` anchor) | Stable |
| Display | **E** | `dev.willram.ramcore.display` - `TextDisplaySpec`, `ItemDisplaySpec`, `BlockDisplaySpec`, `Hologram`/`HologramSpec`, `DisplaySpawner`, `DisplayHandle` | Stable |
| Packet/Protocol | **E** | `dev.willram.ramcore.packet` (module `ramcore-protocol`) - `ProtocolVisualPacketFactory`, `PacketVisualSession`/`State`/`Operation`, `PacketVisualTransport` (+ `InMemoryPacketVisualTransport`, `ProtocolLibPacketVisualTransport`), `PacketFakeEntity`, `PacketEntityView`, `Packets`; `dev.willram.ramcore.scoreboard` packet-backed scoreboards | Experimental / adapter-backed |
| ContentRegistry/ContentLoader | **P** | `dev.willram.ramcore.content` - `ContentRegistry<T>`, `ContentLoader` (YAML+HOCON, `extends:` deep-merge inheritance, every-error-at-once `ValidationError`+`SourceRef`), `SpecLoader`/`ContentDeserializer`, `ContentRegistrar`. Specs exist only for `ItemSpec`, `RegionSpec`, `RewardPlanSpec`, `AbilitySpec`, `DialogueSpec`, `StatSpec` (`content/spec/*.java`) - **no loot-table, NPC, or display config-form specs yet** (documented deferral) | Experimental |
| ServiceRegistry / `RamPlugin.services()` | **E** | `dev.willram.ramcore.service.ServiceRegistry`/`SimpleServiceRegistry`, `Service`, `ServiceKey`; `RamPlugin.services()` (`ramcore-api/.../RamPlugin.java:112-115`) | Stable |
| Persistence | **E** | `dev.willram.ramcore.data.FileDataRepository`/`DataItem`/`DataMigration` (now `@Deprecated(since="2.1")`, still functional) plus the new `dev.willram.ramcore.store` - `Store<K,V>`, `CachedStore`, `InMemoryStore`, `FileStore`, `store.sql.SqlStore` (experimental, driver runtime-resolved), `StoreMigrations`; `dev.willram.ramcore.playerdata.PlayerDataService` (async preload on login, join/quit lifecycle, autosave, dirty tracking) | Stable (`SqlStore`/`PlayerDataService` experimental) |
| Schedulers (Folia) | **E** | `dev.willram.ramcore.scheduler.Schedulers`, `TaskContext`, `Promise` (Folia-anchored continuation overloads added in the 2.3 audit); test seam `SchedulerBackend`/`FakeScheduler` | Stable, Folia-safe by design |
| Region | **E** | `dev.willram.ramcore.region` - `RuleRegion`, `RegionRuleEngine` (+ `regionsAt`/`region(id)`), `RegionShape`/`RegionShapes`, `RegionRule`, `RegionTracker` (+ `RegionEnterEvent`/`RegionExitEvent`) | Stable |
| Party | **E** | `dev.willram.ramcore.party` - `PartyManager`, `PartyGroup`, `PartyInvite`, `PartyMembershipRule(s)`, `PartyContributionTracker`, `PartyStore`, `PartyTeleport` | Stable |
| Encounter | **E** | `dev.willram.ramcore.encounter` - `EncounterDefinition` (phases, arena `RegionShape`, `RewardPlan`), `EncounterAbility`, `EncounterInstance`, `EncounterRegistry`, `EncounterListener`, `Encounters` facade | Advanced platform |
| NPC | **E** | `dev.willram.ramcore.npc` - `NpcSpec<T>`, `Npcs.spawn/spec/registry`, `NpcClickHandler`/`NpcClickContext`, `NpcRegistry`, `NpcHandle` | Stable |
| Trade | **E** | `dev.willram.ramcore.trade` - `Trades` (offer/profile/apply), `TradeOffer`, `TradeProfile`, `TradeRestockBehavior` | Stable (`TRADE_RESTOCK_INTERNALS` unsupported per NMS matrix) |
| AI / Brain | **E** (capability-gated) | `dev.willram.ramcore.ai` - `MobAi`, `MobGoalBackend` (+Paper/InMemory impls), `RamMobGoal(Builder)`, `CommonMobGoals`, `MobGoalDecorators`, `MobAiDiagnostics`; `dev.willram.ramcore.brain` - `MobBrains`, `MobBrainController`, `BrainMemorySnapshot`. Sensors/Activities `UNSUPPORTED` (`docs/NMS_COMPATIBILITY.md`) | Paper-experimental / NMS-backed for the gated parts |
| Selector | **E** | `dev.willram.ramcore.selector` - `Selectors` facade, `EntitySelector<T>`, `PlayerSelector`, `SelectorSort` | Stable |
| Metadata | **E** | `dev.willram.ramcore.metadata` - `Metadata` facade, `MetadataKey`, `MetadataMap`, `MetadataRegistry` + typed `metadata.type.{Player,Entity,Block,World}MetadataRegistry` | Stable |
| RamExceptions | **E** | `dev.willram.ramcore.exception.RamExceptions`, `RamPreconditions` (`checkArgument`/`checkState`/`misuse`), `ApiMisuseException`, `ValidationException`/`ValidationError` base, `exception.types.{EntityRetiredException,EventHandlerException,PromiseChainException,SchedulerTaskException}` | Stable |
| Real-time scheduling | **E** | `dev.willram.ramcore.schedule` - `RealTimeScheduler`, `Job`, `JobState`, `MissedRunPolicy{SKIP,CATCH_UP}`, `CronExpression`/`Schedule` (in-house cron, no dependency) | Experimental |
| Instanced worlds | **E** (Paper-only) | `dev.willram.ramcore.worldinstance` - `WorldInstanceService`, `WorldInstance`, `WorldBackend` (`PaperWorldBackend` gates `supportsInstances()` false on Folia with an actionable refusal), `InstanceMarker`, `WorldInstances` (copy/sweep) | Paper-experimental; refuses on Folia by design |
| Vault economy | **E** | `dev.willram.ramcore.economy` - `Economy`, `EconomyResult`, `InMemoryEconomy`, `VaultEconomy` (package-private, obtained only via `Economies.detect(IntegrationRegistry)` so `net.milkbowl` never loads when Vault is absent), wired into `RewardActions.money(Economy, amount)` | Stable; `VaultEconomy` experimental |

## 2. Detail per area

### Loot
- Files: `ramcore-api/src/main/java/dev/willram/ramcore/loot/{LootTable,LootPool,LootEntry,LootCondition,LootFunction,LootContext,LootGenerator,InstancedLoot,LootClaimPolicy,LootInstance,LootInstanceStore,InMemoryLootInstanceStore,PersistentLootInstanceStore,LootReward,LootPayloadCodec}.java`
- Shape: `LootTable.builder(ContentId)` -> guaranteed/weighted entries + `LootPool`s + `rolls`/`bonusRolls`. `LootEntry(id, weight, Function<LootContext,LootReward>, Predicate<LootContext>)` - record with `guaranteed(...)`/`weighted(...)` factories and `.when(condition)`. `LootGenerator.generate(table, context, random) -> LootGenerationResult` is pure/side-effect-free. `InstancedLoot` facade: `generator()`, `inMemoryStore()`, `persistentStore(Store<UUID,LootInstanceSnapshot>, LootPayloadCodec)`, `table(id)`, `pool(id)`.
- Gap (documented, not a bug): `LootReward.payload` is `Object`; `PersistentLootInstanceStore` can't persist it without a consumer-supplied `LootPayloadCodec` - exactly what RamRPG's Task 1.1 "constructs RPG item instances via `ItemInstanceService`" would need to supply.
- Also missing (per Task 1.5 outcome): no `LootTableSpec` for `ContentLoader`/HOCON yet - RamRPG can build loot tables in Kotlin code today but not load them from `.conf` via RamCore's loader.
- RamRPG dependency: Task 1.1 (replace `EntityProfile.loot`), Task 6.4 (`InstancedLoot`+party for boss drops).

### Objective
- Files: `ramcore-api/src/main/java/dev/willram/ramcore/objective/{ObjectiveDefinition,ObjectiveTask,ObjectiveTracker,ObjectiveProgress,ObjectiveProgressStore,ObjectiveAction,ObjectiveEvent,ObjectiveSubject,Objectives}.java`
- Shape: `ObjectiveDefinition.builder(ContentId)` with `List<ObjectiveTask>`, `chained`/`hidden` flags. `ObjectiveTask.of(id, ObjectiveAction, target, required)`. `ObjectiveTracker` (in-memory, or `create(ObjectiveProgressStore)` for persistence + `load()`); tracks progress keyed by `ObjectiveSubject` x objective `ContentId`, fires `ObjectiveProgressListener`s, exposes `ObjectiveProgress.tasks(definition) -> List<ObjectiveTaskProgress>`.
- RamRPG dependency: Task 1.2 (`QuestDefinition` as a thin wrapper over `ObjectiveDefinition`).

### Reward
- Files: `ramcore-api/src/main/java/dev/willram/ramcore/reward/{RewardEngine,RewardAction,RewardActionFactory,RewardActionFactories,RewardActions,RewardContext,RewardPlan,RewardEntry,RewardOutcome,RewardReport,Rewards,RewardSubjects}.java`
- Shape: `RewardPlan.builder()` (guaranteed/weighted `RewardEntry` + `rolls`). `RewardEngine.validate/preview/execute(plan, context, random) -> RewardReport`. Extension point (exactly what RamRPG Task 1.2 needs for custom reward types): `RewardActionFactory { String type(); RewardAction create(ConfigurationNode params); }`, registered into `RewardActionFactories` (`.standard(Economy)` ships `money`/`command`/`message`/`permission-node-check`; `item` is explicitly left to consumers per the class javadoc - RamRPG registers `skill_xp`, `rpg_item`, `buff`, `perk_point` here).
- Known limitation: `RewardActions` needs a live player/economy/server context for most actions - validated but callers must run them on the right scheduler context (documented, not a bug).
- RamRPG dependency: Task 1.2 (reward types + extension point - the literal cross-repo dependency row in `RAMRPG_ROADMAP_PROMPT.md`), Task 1.5 (`RewardActionFactory` keyed by `type:` used from HOCON).

### Cooldown
- Files: `ramcore-api/src/main/java/dev/willram/ramcore/cooldown/{CooldownTracker,Cooldown,CooldownMap,CooldownStore,CooldownSnapshot,CooldownExpiryListener,ComposedCooldownMap,ActionThrottle,Cooldowns}.java`
- Shape: `CooldownTracker.create(Cooldown base)` or `create(base, CooldownStore<K>)` for persistence; `load()` restores unexpired cooldowns; `CooldownExpiryListener<K>` callbacks. RamRPG's `Cooldown(ticks, keyScope: PLAYER|ITEM|GLOBAL)` maps directly onto `CooldownTracker<K>` with `K` = whatever key strategy RamRPG picks (matches Task 1.3's plan exactly).
- Note: RamCore's own `AbilityCaster` (3.3) deliberately does not use `CooldownTracker` for per-ability cooldowns (uses an injectable `Clock` instead, per an execution-plan deviation) - worth knowing if RamRPG wants ability cooldowns to look like RamCore's own; not a blocker.
- RamRPG dependency: Task 1.3.

### Menu
- Files: `ramcore-api/src/main/java/dev/willram/ramcore/menu/{Gui,Item,PaginatedMenu,MenuSession,MenuView,MenuState,MenuButton,MenuAction,MenuClickContext,MenuRenderer,MenuLifecycleHandler,Menus,PaginatedButtonFactory}.java`
- Shape: `Gui` (abstract, `TerminableConsumer`, slot map, redraw lifecycle) and the newer `MenuSession`/`MenuView` (declarative, `suspend(InputRequest)` for player text input added in Task 1.4 - reopens the session after chat/anvil/sign capture). `PaginatedMenu<T>.builder(...)` with `PaginatedButtonFactory<T>`, previous/next buttons, `PAGE_STATE_KEY`.
- RamRPG dependency: Task 1.2 (`QuestsGui`), Task 1.6 (`StatsGui`/`SkillsGui`), Task 3.1 (station GUIs), Task 5.1/5.3 (perk/set GUIs).

### Presentation / Display
- Presentation: `presentation/{PresentationEffect,PresentationEffects,PresentationContext}.java` - Adventure-native (titles, action bar, boss bar, sound, particle) with `PresentationContext.of(TaskContext, Audience...)` anchoring effects to a scheduler; all effects `Terminable`.
- Display: `display/{TextDisplaySpec,ItemDisplaySpec,BlockDisplaySpec,Hologram,HologramSpec,HologramLine,Holograms,DisplaySpawner,DisplayHandle,DisplayOptions}.java` - `TextDisplaySpec.text(ComponentLike)` builder with line width, background color, opacity, alignment, shadow/see-through flags; spawned via `DisplaySpawner`, returned as a `DisplayHandle` (per-player visibility + lifecycle cleanup).
- RamRPG dependency: Task 1.6 (replace `ActionBarUi`/`BossBarUi`/`DamageIndicatorStage`'s hand-built packets with `presentation`+`display`).

### Packet / Protocol
- Module: `ramcore-protocol` (separate Gradle module, ProtocolLib `compileOnly`), files under `dev/willram/ramcore/{packet,protocol,scoreboard,event,integration}`.
- Shape: `ProtocolVisualPacketFactory.createPackets(PacketVisualOperation) -> List<PacketContainer>` (`@FunctionalInterface`); `PacketVisualTransport`/`InMemoryPacketVisualTransport`/`ProtocolLibPacketVisualTransport` separate "what to show" (`PacketVisualState`/`PacketVisualSession`) from "how it's sent" so logic is unit-testable without ProtocolLib; `PacketFakeEntity`, `PacketEntityView` for illusion entities. Per NMS matrix: `PACKET_VISUAL_STATE` = `SUPPORTED` always; `PACKET_PROTOCOLLIB_TRANSPORT`/`PACKETS` only `PARTIAL` when ProtocolLib is actually present, else `UNSUPPORTED`.
- Gap flagged in `RAMRPG_ROADMAP_PROMPT.md` itself (Task 1.6, rework.md item 4): bundle contents, dropped item entities, and item frames are not yet covered by RamCore's packet item-rendering path - RamRPG's `PacketItemRenderer` gaps are not something RamCore currently closes; this is real remaining work on the RamCore side if RamRPG wants to delete `PacketItemRenderer`.
- RamRPG dependency: Task 1.6.

### ContentRegistry / ContentLoader
- Files: `ramcore-api/src/main/java/dev/willram/ramcore/content/{ContentRegistry,SimpleContentRegistry,ContentEntry,ContentKey,ContentId,ContentLoader,ContentDefinition,ContentLoadResult,SourceRef,SpecLoader,SpecLoadResult,ContentDeserializer,ContentRegistrar,ContentValidationException}.java` + `content/spec/{ItemSpec,RegionSpec,RewardPlanSpec,RewardEntrySpec,AbilitySpec,DialogueSpec,StatSpec}.java`
- Shape: `ContentRegistry<T>.create(Class<T>)` - namespaced (`ContentId`), owner-tracked (`register(owner, key, value)`), `unregisterOwner(owner)`, `close()`. `ContentLoader.load(Path root)` reads `content/<type>/*.yml|.yaml|.conf`, resolves `extends:` by deep-merge (child scalar wins, list replaces, maps merge), collects every error with `SourceRef(file, path)` rather than failing on the first (exactly RamRPG's Task 1.5 validation requirement). `SpecLoader.create().deserializer(type, ContentDeserializer<T>).load(dir) -> SpecLoadResult`.
- Gap: no `LootTableSpec`, no NPC/display/mob config-form specs - documented as deferred in the 1.6 outcome notes. RamRPG's Task 1.5 wants item/enchant/entity/gem/reforge/skill/stat content types; only the item/region/reward/ability/dialogue/stat shapes exist today, and `ItemSpec` is generic (material/name/lore), not RPG-schema-aware - RamRPG will still need its own RPG-shaped spec types layered on `ContentLoader`'s raw `ContentDefinition`s, per the roadmap's own "keep RamRPG's loader small and structurally compatible" guidance.
- RamRPG dependency: Task 1.5 (explicitly listed as "depends on RamCore task 1.6" in the cross-repo table - 1.6 is DONE, but only the plumbing; RamRPG still authors its own specs).

### ServiceRegistry / RamPlugin
- Files: `ramcore-api/src/main/java/dev/willram/ramcore/RamPlugin.java`, `service/{ServiceRegistry,SimpleServiceRegistry,Service,ServiceKey,ServiceContext,ServiceRegistration,ServiceDiagnostic}.java`
- How a plugin extends `RamPlugin` (verified, `RamPlugin.java:26-124`): it's abstract with `enable()`, `disable()`, `load()`, `registerCommands(Commands)`; provides `bind(Terminable)`, `bindModule(TerminableModule)`, `services() -> ServiceRegistry`. `onLoad()` creates the `ServiceRegistry` and calls your `load()` then `serviceRegistry.loadAll()`; `onEnable()` registers Brigadier commands via the lifecycle event, calls `serviceRegistry.enableAll()` then your `enable()`; `onDisable()` calls your `disable()`, closes the registry, closes the terminable registry, and shuts down `Schedulers` - but only shuts down the shared static executors if `ownsSharedExecutors()` (i.e. this plugin instance *is* RamCore itself) - the Phase-A fix for the "one consumer's disable kills everyone's executor" bug (finding A3 in the execution plan).
- RamRPG dependency: Task 1.7 (register services in RamCore's `ServiceRegistry` instead of ~20 `lateinit` fields + `RamRPG.get()` singleton).

### Persistence
- Legacy (still works, now `@Deprecated(since="2.1")`): `data/{FileDataRepository,DataItem,DataMigration,DataRepository,Repositories,NamespacedKeys,GsonDataSerializer,DataKeyCodec}.java` - this is what RamRPG's `PlayerStore`/`FilePlayerStore` currently uses per Task 1.4.
- New (Task 1.1, done): `store/{Store,CachedStore,SimpleCachedStore,InMemoryStore,FileStore,ForwardingStore,AbstractAsyncStore,StoreCodec,StoredRecord,StoreMigration,StoreMigrations,StoreFiles,Stores,StoreException}.java` + `store/sql/{SqlStore,SqlDialect,SqlStoreConfig,ConnectionProvider,HikariConnectionProvider(s)}.java`. `Store<K,V>`: `load/save/delete/loadAll/keys` all return `Promise`; wrap with `Stores.cached(store)` for dirty tracking. `SqlStore` is experimental and its JDBC drivers/HikariCP are not yet runtime-resolved - the planned `RamCoreLoader` (Paper `PluginLoader`) from ADR-0003 "is not written yet" (explicit open item in the 1.1 outcome notes), so SQL storage today only works if the consumer plugin ships its own driver on the classpath.
- New (Task 1.2, done): `playerdata/{PlayerDataService,PlayerDataKey,PlayerDataOptions,JoinPolicy}.java` - exactly the `PlayerDataService` RamRPG's Task 1.4 says "when RamCore ships it, the swap must touch only `FilePlayerStore.kt`". It has shipped. `install(RamPlugin, PlayerDataOptions)` must be called from `RamPlugin.load()`; `register(PlayerDataKey<T>, Store<UUID,T>)`; reads are synchronous on the player's thread post-join; `JoinPolicy{KICK,DEFER}` (no `BLOCK`, deliberately, for Folia safety).
- RamRPG dependency: Task 1.4 (swap-in point - ready now, but blocked entirely by section 0's stale-jar problem).

### Schedulers (Folia)
- File: `ramcore-api/src/main/java/dev/willram/ramcore/scheduler/Schedulers.java` (~1,100+ lines) + `TaskContext`, `promise/Promise.java` (~1,450 lines), `scheduler/builder/TaskBuilder.java`, test seam `SchedulerBackend`/`SchedulerBackends` and `ramcore-test`'s `FakeScheduler`.
- Global/async/region/entity/player scheduling contexts; `Promise` continuations gained `TaskContext`-anchored overloads (`thenApply(TaskContext, fn)`, etc.) in the 2.3 audit specifically to fix "sync on Folia means the global region thread, not the entity's region" (finding A1). Also fixed: entity-retired promises now complete exceptionally with `EntityRetiredException` (A2) instead of hanging; cancellation races and exception-swallowing bugs found and fixed (A6/A7); `RamPlugin`'s shared-executor-shutdown bug (A3) fixed via `ownsSharedExecutors()`.
- RamRPG dependency: rule 4 of the roadmap ("Folia-safe. All entity/player/world mutation goes through `core/platform/PlatformScheduler`... delegates to RamCore `Schedulers`") - foundational, touches every task.

### Region / Party / Encounter / NPC / Trade
- Region: `region/{RuleRegion,RegionRuleEngine,RegionShape,RegionShapes,RegionRule,RegionAction,RegionDecision,RegionQuery,RegionTracker,RegionEnterEvent,RegionExitEvent}.java`. `RegionRuleEngine.regionsAt(Position)`/`region(ContentId)` and the `RegionTracker` listener (enter/exit events) were added in Task 1.5 - previously region was "a pure rule engine" with no lookup, now has both.
- Party: `party/{PartyManager,PartyGroup,PartyId,PartyInvite,PartyMembershipRule(s),PartyOptions,PartyRole,PartyContributionTracker,PartyStore,PartySnapshot,PartyTeleport,Parties,PartyChatContext,PartyChatHandler}.java`. `PartyManager.create()` (in-memory) or with a `PartyStore` for persistence; one-party-per-player indexing; damage `PartyContributionTracker` for bosses.
- Encounter: `encounter/{EncounterDefinition,EncounterPhase,EncounterAbility,EncounterAbilityAction,EncounterInstance,EncounterRegistry,EncounterListener,EncounterSignal,EncounterState,EncounterUpdate,Encounters}.java`. `EncounterDefinition.builder(id, maxHealth)` with health-percent-ordered `EncounterPhase`s, an arena `RegionShape`, and a `RewardPlan`.
- NPC: `npc/{NpcSpec,Npcs,NpcHandle,NpcRegistry,NpcSpawner,NpcClickHandler,NpcClickContext,NpcClickType}.java`. `Npcs.spawn(Location, NpcSpec<T>) -> Promise<NpcHandle<T>>`; click handlers, nameplate visibility, AI toggle.
- Trade: `trade/{Trades,TradeOffer,TradeProfile,TradeRestockBehavior,TradeSetMode}.java`. `Trades.applyNow(Merchant, TradeProfile)` (sync) or `Trades.apply(AbstractVillager, TradeProfile)` (hops to the villager's scheduler via `Schedulers.run`).
- RamRPG dependency: Task 6.2 (region level scaling), Task 1.1/6.4 (party + instanced loot for bosses), Task 6.4 (boss = `MobDefinition` + `EncounterDefinition`), Phase 7 (NPC vendors + `trade`).

### AI / Brain / Selector
- AI: `ai/{MobAi,MobGoalBackend,PaperMobGoalBackend,InMemoryMobGoalBackend,RamMobGoal,RamMobGoalBuilder,CommonMobGoals,MobGoalDecorators,MobGoalSnapshot,MobGoalConflict,MobAiController,MobAiDiagnostics,MobGoalDebugState(Status),MobGoalRegistration}.java`. `MobAi.controller(mob)`/`controller(mob, backend)`; goal add/remove/replace/pause/restore with snapshots; decorators (cooldown, timeout, chance, predicate, distance/LOS/health gates).
- Brain: `brain/{MobBrains,MobBrainBackend,PaperMobBrainBackend,InMemoryMobBrainBackend,MobBrainController,MobBrainDiagnostics,BrainMemorySnapshot,BrainMemoryValue}.java`. Read/write for selected `MemoryKey`s via Paper; sensors/activities remain `UNSUPPORTED` (need a versioned NMS adapter - none written yet, per `docs/NMS_COMPATIBILITY.md`).
- Selector: `selector/{Selectors,EntitySelector,PlayerSelector,SelectorSort}.java`.
- RamRPG dependency: Task 6.3 (`MobDefinition` "AI goals via RamCore `ai`/`brain`").

### Metadata / RamExceptions
- Metadata: `metadata/{Metadata,MetadataKey(Impl),MetadataMap(Impl),MetadataRegistry,AbstractMetadataRegistry,StandardMetadataRegistries,Empty,SoftValue,WeakValue,TransientValue,ExpiringValue,ExpireAfterAccessValue}.java` + `metadata/type/{Player,Entity,Block,World}MetadataRegistry.java`.
- RamExceptions: `exception/{RamExceptions,RamPreconditions,ApiMisuseException,InternalException,ValidationException,ValidationError}.java` + `exception/events/RamExceptionEvent.java` + `exception/types/{EntityRetiredException,EventHandlerException,PromiseChainException,SchedulerTaskException}.java`. `RamPreconditions.checkArgument/checkState(condition, problem, fix)` and `.misuse(problem, fix)` - every RamCore public constructor in the areas above (`LootEntry`, `ObjectiveDefinition`, `EncounterDefinition`, etc.) already uses this pattern, so RamRPG gets consistent fail-fast errors for free when it builds on these types.
- RamRPG dependency: Task 6.1 (affix metadata on entities via PDC/`Metadata`).

### Real-time scheduling / Instanced worlds
- Real-time: `schedule/{RealTimeScheduler,Job,JobState,MissedRunPolicy,CronExpression,Schedule}.java`. In-house 5-field cron parser (no dependency); `RealTimeScheduler` ticks once/second async, runs due jobs on their declared `TaskContext`; `MissedRunPolicy{SKIP, CATCH_UP(max)}` for restarts.
- Instanced worlds: `worldinstance/{WorldInstanceService,WorldInstance,WorldBackend,WorldInstanceOptions,InstanceMarker,WorldInstances}.java`. Paper-only by design - `WorldBackend.supportsInstances()` returns `false` under a regionised (Folia) server and `create()` refuses with an actionable message; verified live on both Paper 26.1 (creates + startup-sweeps) and Folia 26.1.2 (refuses as expected) per `RELEASE_READINESS.md`.
- RamRPG dependency: Phase 7 ("RamCore 3.2 instanced worlds, 3.8 real-time scheduling" - both DONE, but Folia dungeons on RamRPG, which declares `folia-supported: true`, will hit the same Paper-only gate RamCore documents; RamRPG needs its own fallback story for Folia servers if dungeons matter there).

### Vault economy
- Files: `economy/{Economy,EconomyResult,InMemoryEconomy,VaultEconomy,Economies}.java`; wired into `reward/RewardActions.money(Economy, amount)`.
- `Economies.detect(IntegrationRegistry)` never touches `net.milkbowl.*` classes unless the registry reports Vault present - safe when Vault is absent (as RamRPG's `pom.xml` already marks VaultAPI `provided`/optional).
- Gap: `RELEASE_READINESS.md` scenario 5 ("reward paid through the Vault bridge") is only exercised as a documented dialogue-action hook in the example plugin and is explicitly marked "not tested" on both the Paper and Folia smoke logs - the Vault path has automated unit coverage (`InMemoryEconomy`, money-through-`RewardEngine`) but no live-server verification yet.

## 3. RamCore roadmap tasks RamRPG is waiting on - status

Every task in `RAMCORE_ROADMAP_PROMPT.md` Phases 1-3 and the execution plan's Phase A-D is marked
DONE as of 2026-09-11 per `docs/ROADMAP_EXECUTION_PLAN.md` outcome notes (also cross-confirmed by
`todo.md`, all items checked). Task 3.9 (ability targeting/telegraphs, a post-Phase-D addition) is
also DONE (2026-09-11).

| RamCore task | RamRPG cross-repo row | Status | Caveat |
| --- | --- | --- | --- |
| 1.1 Pluggable persistence (`Store`) | Task 1.4 swap-in | DONE | `SqlStore` experimental; `RamCoreLoader` (runtime Hikari/JDBC resolution) not written - SQL needs a consumer-shipped driver |
| 1.2 Player data lifecycle (`PlayerDataService`) | Task 1.4 swap-in | DONE | Experimental; install must happen from `RamPlugin.load()` |
| 1.5 Vault/PAPI bridges (reward extension point) | Task 1.2 reward types | DONE | `item` reward type still consumer-supplied (as designed); live Vault-reward smoke test not run |
| 1.6 Config-backed content (`ContentLoader`) | Task 1.5 content loading | DONE (plumbing only) | No loot/NPC/display/mob spec types yet - RamRPG must author its own RPG specs on top |
| 1.6/3.1 Presentation/display/packet | Task 1.6 UI migration | DONE | `PacketItemRenderer` gaps (bundles, dropped items, item frames) flagged in RamRPG's own roadmap are not closed by RamCore yet |
| 2.2 Multi-module Gradle split | (infra) | DONE | RamRPG's `pom.xml` still points at the pre-split artifact - see section 0 |
| 3.2 Instanced worlds | Phase 7 | DONE | Paper-only; refuses on Folia |
| 3.3/3.4 Ability + stat system | (overlaps RamRPG's own ability/stat systems) | DONE | Experimental; RamRPG should evaluate reusing `dev.willram.ramcore.ability`/`stat` instead of, or underneath, its own `api/abilities`/`api/stats` |
| 3.6 Session recorder / diagnostics timeline | (ops) | DONE | Per-subsystem instrumentation hooks (reward/loot/objective/cooldown/region/menu/ability) deferred - `record(...)` exists but nothing calls it yet |
| 3.8 Real-time/cron scheduling | Phase 7 | DONE | - |
| 3.9 Ability targeting/telegraphs | (follow-up) | DONE | - |

Nothing on RamRPG's cross-repo dependency table is blocked by missing RamCore work any more. The
sole blocker is operational: RamRPG must repoint its build (section 0) before any of Phase 1 can
start consuming these APIs.

## 4. Gradle setup

- Root: `C:\repos\RamCore\settings.gradle.kts` - modules `ramcore-api`, `ramcore-nms`, `ramcore-protocol`, `ramcore-kotlin`, `ramcore-test`, `ramcore-paper`, `examples:sample-plugin`, `examples:sample-plugin-kotlin`. `foojay-resolver-convention` plugin so CI/JitPack can provision JDK 25 without a local install.
- Root `build.gradle.kts`: `group = "dev.willram"`, `version = "2.0.0"` for all modules; Java toolchain 25 (`options.release.set(25)`); JUnit 5.11.4 (`junit-jupiter` + `junit-platform-launcher` explicitly for Gradle 9); every module except `ramcore-paper` applies `maven-publish` (publishes to `mavenLocal()`/JitPack as `dev.willram:<module>:2.0.0`).
- `ramcore-api/build.gradle.kts`: `api` deps on Configurate (core/hocon/yaml), Typesafe config, flow-math, `shadow-bukkit`; `compileOnlyApi` JetBrains annotations; `compileOnly` paper-api, VaultAPI, PlaceholderAPI, HikariCP (all optional/runtime-resolved per ADR-0003).
- `ramcore-nms`/`ramcore-protocol`/`ramcore-test`: each `api(project(":ramcore-api"))` + `compileOnly` paper-api (+ ProtocolLib for `-protocol`).
- `ramcore-kotlin/build.gradle.kts`: Kotlin JVM plugin, `jvmToolchain(25)`/`JvmTarget.JVM_25`; shades `kotlinx-coroutines-core-jvm:1.8.0` as `implementation`.
- `ramcore-paper/build.gradle.kts`: the shaded runtime plugin - `com.gradleup.shadow:8.3.9` + ASM 9.8 (forced, for Java 25 class files); `implementation project(...)` on all four library modules; `compileOnly` for paper-api/ProtocolLib/VaultAPI/PlaceholderAPI/HikariCP/Lettuce (all runtime-resolved or server-provided); shades+relocates Configurate, Typesafe config, flow-math, snakeyaml, kotlinx-coroutines, bStats under `dev.willram.ramcore.libs.*`; output `ramcore-paper/build/libs/RamCore-2.0.0.jar`; `jar` task disabled in favor of `shadowJar`.
- JDK requirement: Java 25 (toolchain), Paper API `26.1.2.build.60-stable`+. `gradle.properties` auto-downloads the toolchain and also points at a local `jdk-25.0.1.8-hotspot` install. Gradle wrapper is 9.1.0 (`gradle/wrapper/gradle-wrapper.properties`); `jitpack.yml` builds the library modules with `openjdk21` + foojay-provisioned Java 25 toolchain via `./gradlew :ramcore-api:publishToMavenLocal ...` (note: does not publish `ramcore-paper`, which is correct - it's the runtime jar, not a library dependency).
- Publishing coordinates: `dev.willram:ramcore-api:2.0.0`, `dev.willram:ramcore-nms:2.0.0`, `dev.willram:ramcore-protocol:2.0.0`, `dev.willram:ramcore-kotlin:2.0.0`, `dev.willram:ramcore-test:2.0.0` all present in the local Maven cache (`~/.m2/repository/dev/willram/ramcore-*/2.0.0/`, built 2026-09-10/11). Via JitPack: `com.github.willramanand.RamCore:ramcore-api:<tag>` (README). There is no `dev.willram:RamCore` (capital, single-module) coordinate produced by the current build - that is the stale artifact from section 0.

## 5. `ramcore-test` module - fakes available for off-server unit testing

`C:\repos\RamCore\ramcore-test\src\main\java\dev\willram\ramcore\testkit\`:

| Class | Purpose |
| --- | --- |
| `FakeScheduler` | Deterministic, tick-stepped `SchedulerBackend`. `tick()`/`tick(n)`, `runAsync()` (drains async queue inline), `pendingCount()`, `retireEntity(UUID)`; models global/async/per-region/per-entity queues; repeating tasks reschedule until cancelled. Install via `SchedulerBackends.install(...)`. |
| `FakeClock` | Manually advanced `java.time.Clock` (`FakeClock.epoch()`, advance methods) for `CooldownTracker`, `RealTimeScheduler`, etc. |
| `ProxyFakes` | `java.lang.reflect.Proxy`-backed fakes of Bukkit interfaces without a server; `stub(Class)` (neutral defaults) and `recording(Class)` (captures call names). |
| `TestServiceContext` | `ServiceContext` implementation for tests; `withRegistry()` attaches a real `ServiceRegistry`; carries an event log. |
| `FakeItemStack` | An `ItemStack` constructible/cloneable off-server (opaque pass-through only - no material/meta). |

Gap vs. the roadmap's own text: `docs/ROADMAP_EXECUTION_PLAN.md`'s task 2.2 plan called for
`ramcore-test` to also carry `CommandTestHarness` and `MenuClickContexts.fake(...)`; the outcome note
admits the deviation ("`ramcore-test` holds the fakes only") and neither class exists anywhere in the
repo (confirmed by search - zero matches). `StoreContractTest` also stayed in the main test suite
rather than being published. So RamRPG can unit-test schedulers, clocks, services, and Bukkit-interface
stubs off-server, but cannot yet get a ready-made command-dispatch or menu-click test harness from
`ramcore-test` - it would have to build its own on top of `ProxyFakes`/`FakeScheduler`.

## 6. How a plugin extends `RamPlugin`

```java
public abstract class RamPlugin extends JavaPlugin implements ServiceContext {
    public abstract void load();               // called from onLoad(), before serviceRegistry.loadAll()
    public abstract void enable();              // called from onEnable(), after commands + serviceRegistry.enableAll()
    public abstract void disable();             // called from onDisable(), before serviceRegistry.close()
    public abstract void registerCommands(Commands commands); // Brigadier, via LifecycleEvents.COMMANDS

    // inherited, ready to use:
    <T extends AutoCloseable> T bind(T terminable);
    <T extends TerminableModule> T bindModule(T module);
    ServiceRegistry services();
    void registerListener(Listener listener);
    void log(String message);                  // MiniMessage-formatted console log
}
```
(`C:\repos\RamCore\ramcore-api\src\main\java\dev\willram\ramcore\RamPlugin.java`)

Register a `PlayerDataService`, custom services, etc. from `load()` (the service registry refuses
registrations after `load()`, per the 1.2 outcome note). Bind anything with lifecycle (trackers,
sessions, listeners-as-`TerminableModule`) via `bind`/`bindModule` so `onDisable()` cleans it up
automatically. Only the plugin instance that *is* RamCore itself shuts down the shared static
scheduler executors (`ownsSharedExecutors()`), so a consumer plugin's disable/reload never kills
scheduling for the rest of the server.

## 7. Sources consulted

- `C:\repos\RamRPG\RAMRPG_ROADMAP_PROMPT.md`
- `C:\repos\RamRPG\pom.xml`
- `C:\repos\RamCore\README.md`, `RAMCORE_ROADMAP_PROMPT.md`, `todo.md`, `RELEASE_READINESS.md`
- `C:\repos\RamCore\docs\API.md`, `MODULE_BOUNDARIES.md`, `ROADMAP_EXECUTION_PLAN.md`, `NMS_COMPATIBILITY.md`
- `C:\repos\RamCore\docs\decisions\ADR-0001..0003`
- `C:\repos\RamCore\ramcore-api\src\main\java\dev\willram\ramcore\**` (loot, objective, reward, cooldown, menu, presentation, display, content, service, data, store, playerdata, scheduler, region, party, encounter, npc, trade, ai, brain, selector, metadata, exception, schedule, worldinstance, economy)
- `C:\repos\RamCore\ramcore-protocol\src\main\java\dev\willram\ramcore\{packet,protocol,scoreboard}\**`
- `C:\repos\RamCore\ramcore-test\src\main\java\dev\willram\ramcore\testkit\**`
- Gradle files: root + all module `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `jitpack.yml`, `gradle/wrapper/gradle-wrapper.properties`
- Local Maven cache (`~/.m2/repository/dev/willram/**`) and both repos' `git log` for corroborating timestamps
