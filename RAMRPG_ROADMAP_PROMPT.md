# RamRPG Roadmap — Agent Task Prompt

You are working on **RamRPG** (`willramanand/RamRPG`), a Kotlin RPG plugin for Paper/Folia (`api-version 1.21`, `folia-supported: true`) built on **RamCore** (`willramanand/RamCore`). It requires RamCore and ProtocolLib; Vault and MythicMobs are optional. Build is Maven (`pom.xml`), tests are JUnit 5 under `src/test/kotlin`.

Read these before writing code:

- `rework.md` — the completed architectural review; the api/core/builtin split it describes is now in place
- `src/main/kotlin/dev/willram/ramrpg/RamRPG.kt` — service wiring
- `api/` packages: `identity`, `stats`, `skills`, `items`, `enchants`, `effects`, `combat`, `abilities`, `entities`, `reforges`, `sockets`, `quests`
- RamCore's `docs/API.md` and `docs/MODULE_BOUNDARIES.md` (in the RamCore repo) — you must know what RamCore already provides before adding anything to RamRPG
- The companion file `RAMCORE_ROADMAP_PROMPT.md` — some RamRPG tasks depend on RamCore tasks; those dependencies are called out below

## Working rules

1. **RamCore first, always.** Before adding a type, service, or utility to RamRPG, check whether RamCore has it. If it does, use it. If RamCore has a weaker version, improve RamCore rather than fork the concept into RamRPG. RamRPG owns *RPG semantics* (stats, skills, items, combat, progression); RamCore owns *plumbing* (loot rolling, rewards, objectives, cooldowns, menus, scheduling, persistence, regions, parties, encounters, NPCs, displays).
2. **Effects are the currency.** Every new system (perks, sets, buffs, affixes, enchants) expresses its gameplay as `dev.willram.ramrpg.api.effects.Effect` bundles (`StatEffect`, `DamagePipelineEffect`, `TriggeredEffect`) and feeds `StatService` through a `StatProvider`. Do not add bespoke hooks into listeners.
3. **Content is data.** New content types must be loadable from HOCON from day one. Kotlin `builtin/` entries are the defaults, not the only path.
4. **Folia-safe.** All entity/player/world mutation goes through `core/platform/PlatformScheduler` (which delegates to RamCore `Schedulers`). No async Bukkit access.
5. **Schema discipline.** Any change to `ItemInstanceData` bumps `ItemSchema.CURRENT` and adds a migration; `PdcSchemaStabilityTest` must keep passing.
6. **Every task ships with:** unit tests (no live server), lore/UI rendering where the player would see it, a `lang/en_us.json` entry for every new string, and a short section in a new `docs/DESIGN.md` recording the numbers and formulas chosen.
7. **Do not break existing public API** in `api/` without a deprecation cycle.
8. Work one task at a time in the order given unless told otherwise. After each task, summarize what changed, what was tested, and open questions.

---

## Phase 0 — Design document (short, do it first)

### Task 0.1 — `docs/DESIGN.md`

Write down what the code currently implies so later systems don't multiply against unknowns:

- Stat list and what each does (from `builtin/identity/Builtins.kt` and `builtin/stats/BuiltinStats.kt`): agility, alchemy, attack_speed, combat, cooking, crit_chance, crit_damage, damage, defense, enchanting, excavation, farming, ferocity, fishing, foraging, fortune, health, health_regen, lifesteal, mining, sorcery, speed, strength, true_defense, wisdom, woodcutting.
- Damage pipeline order (`api/combat/Combat.kt` `DamagePriority`: WEAPON_BASE 100 → STRENGTH 200 → ENCHANT_OFFENSE 300 → EFFECT_OFFENSE 400 → CRIT_ROLL 500 → ELEMENTAL_BREAKDOWN 600 → ABILITY_MOD 700 → ARMOR_MITIGATION 1100 → TRUE_DEFENSE 1200 → ENCHANT_DEFENSE 1300 → SHIELDS 1400 → THORNS 1500 → LIFESTEAL 1600 → FEROCITY 1700 → INDICATOR 1900 → APPLY 2000) with the formula each builtin stage applies.
- XP curve (`SkillXpCurveTest`), upgrade cost curve (`UpgradeCostTest`), rarity rules (`RarityRulesTest`), tier multipliers (`builtin/entities/BuiltinEntities.kt` TIERS: uncommon 2×, rare 4×, epic 8×, legendary 16×).
- Decide and record: does durability exist? (`PlayerItemDamageEvent` is currently cancelled globally.) Recommended: yes, as an RPG stat with repair as a smithing sink — see Task 2.2.
- Target power curve: player level bands, expected stats per band, mob HP/damage per band. Rough is fine; it will be tuned.

---

## Phase 1 — Use RamCore correctly

Goal: RamRPG stops carrying private copies of things RamCore provides, so RamCore improvements reach RamRPG automatically. Each task: migrate, delete the duplicate, keep behavior identical, keep tests green.

### Task 1.1 — Loot

`api/entities/Entities.kt` defines its own `LootEntry` (weighted, independent chance, min/max count) and `EntityProfile.loot` / `lootPool` / `lootRolls`. RamCore has `dev.willram.ramcore.loot` (`LootTable`, `LootPool`, `LootEntry`, `LootCondition`, `LootFunction`, `LootContext`, `LootGenerator`, instanced loot).

- Replace `EntityProfile.loot/lootPool/lootRolls` with a RamCore `LootTable` reference (keep a Kotlin builder in RamRPG for the ergonomic `LootEntry(ik("x"), chance = 0.2)` style).
- Move drop resolution in `core/listeners/LootListener.kt` onto `LootGenerator`. Add a `LootFunction` that constructs RPG item instances via `ItemInstanceService` (so drops get rolled stats and a `rollSeed`).
- Use RamCore instanced loot (`InstancedLoot`, `LootClaimPolicy`) for boss drops so parties get per-player loot. Depends on Phase 5 for bosses; wire the hook now.
- If RamCore's loot API lacks something RamRPG needs (e.g. an "independent chance" entry), add it to RamCore, not RamRPG.

### Task 1.2 — Quests → RamCore objectives + rewards

`api/quests/Quests.kt`, `core/services/QuestServiceImpl.kt`, `core/listeners/QuestProgressListener.kt`, `QuestsGui.kt` and `PlayerRpgData.questProgress/questCompleted` duplicate RamCore's `objective` (`ObjectiveDefinition`, `ObjectiveTask`, `ObjectiveTracker`, `ObjectiveProgress`) and `reward` (`RewardEngine`, reward contexts, weighted/conditional rewards).

- Re-express `QuestDefinition` as a thin RPG wrapper over `ObjectiveDefinition` + a RamCore reward set; delete the RPG-side progress tracking.
- Add RamCore reward types RamRPG needs: skill XP, RPG item instance, buff (Task 4.1), perk point (Task 5.1). Register them from RamRPG via RamCore's reward extension point; if the extension point is missing, add it to RamCore.
- Rebuild `QuestsGui` on RamCore `menu` (`Gui`, `PaginatedMenu`, `MenuSession`) — it already imports `Gui`/`Item`; finish the migration.
- Migrate stored progress with a `DataMigration`.

### Task 1.3 — Cooldowns

`api/abilities/Abilities.kt` has `Cooldown(ticks, keyScope: PLAYER|ITEM|GLOBAL)`. RamCore has `dev.willram.ramcore.cooldown.CooldownTracker` and command cooldowns.

- Back `AbilityServiceImpl` cooldowns with `CooldownTracker`; keep the RPG `CooldownScope` as the key strategy (player UUID / item `instanceId` / global).
- Expose remaining cooldown for lore and action-bar rendering through the same tracker.

### Task 1.4 — Player data behind an interface (do not block on RamCore 1.1)

`core/storage/PlayerStore.kt` + `FilePlayerStore.kt` use RamCore `FileDataRepository` / `DataItem` (`PlayerRpgData` extends `DataItem`). RamCore roadmap task 1.1/1.2 will introduce `Store<K,V>` and `PlayerDataService`.

- Keep `PlayerStore` as the RPG-facing interface. Make the current file implementation the only concrete class and remove any direct `FileDataRepository` usage outside it.
- Add async load on `AsyncPlayerPreLoginEvent` and save-on-quit with dirty tracking now, using what RamCore currently offers.
- When RamCore ships `PlayerDataService`, the swap must touch only `FilePlayerStore.kt` and the wiring in `RamRPG.kt`.

### Task 1.5 — Content loading from HOCON (depends on RamCore task 1.6, but start now)

`core/config/ContentOverrides.kt` can override values but cannot *define* items, enchants, entities, gems, or reforges. Everything lives in `builtin/*.kt` (6 items, 2 abilities, 27 mob profiles, a handful of enchants/reforges/gems).

- Add `core/config/ContentLoader.kt` that reads `plugins/RamRPG/content/**/*.conf` into `ItemDefinition`, `RPGEnchantment`, `EntityProfile`, `Reforge`, `Gem`, `SkillDefinition`, `StatDefinition`, plus every new type introduced below (recipes, sets, perks, buffs, affixes, mob definitions).
- Effects need a serializable form: define a HOCON schema for `StatEffect`, `DamagePipelineEffect` (by stage key + params), and `TriggeredEffect` (trigger + action key + params), with an `EffectActionRegistry` so builtin actions (lifesteal, fortune drops, vein miner, etc.) can be referenced by id.
- Validation: collect every error with file + path and fail startup with the full list; expose `/rpg validate` and `/rpg reload` with a diff of what changed.
- Convert `builtin/items/BuiltinItems.kt` to a shipped `content/items/builtin.conf` as the proof; keep the Kotlin registration path for programmatic use.
- Prefer RamCore's `ContentLoader` (RamCore task 1.6) for the file plumbing, template `extends:` inheritance, and validation once it exists; until then keep RamRPG's loader small and structurally compatible.

### Task 1.6 — Menus, UI and rendering on RamCore

- `StatsGui`, `SkillsGui`, `QuestsGui`: finish moving to RamCore `menu`.
- `ActionBarUi`, `BossBarUi`, damage indicators (`builtin/stats/DamageIndicatorStage.kt`): move to RamCore `presentation` and `display` (`TextDisplaySpec`) instead of hand-built packets; `rework.md` item 15 flagged the hardcoded entity-metadata indices.
- `core/rendering/PacketItemRenderer.kt`: close the gaps from `rework.md` item 4 — bundle contents, dropped item entities, item frames — using RamCore `packet`/`protocol` factories. Keep `RenderCache` keyed by `(itemHash, locale)`.

### Task 1.7 — Deduplicate wiring

`RamRPG.kt` holds ~20 `lateinit` services and a `get()` singleton. Register services in RamCore's `ServiceRegistry` (`RamPlugin.services()`) and inject through constructors. Keep `RamRPG.get()` as a deprecated shim for one release.

---

## Phase 2 — Item model prerequisites

### Task 2.1 — Item level, requirements and quality

- Add to `ItemDefinition`: `itemLevel: Int`, `requirements: List<Requirement>` (skill level, stat threshold, perk owned), and `equipSlots`.
- Add to `ItemInstanceData`: `quality: Double` (0–1 or a tier enum) that scales `statRolls`; `craftedBy: UUID?`. Bump `ItemSchema.CURRENT`; write the migration.
- `EquipmentListener` enforces requirements (unmet → item inert plus red lore line, do not delete or drop the item).
- Lore: `LoreSection.Requirements`, `LoreSection.ItemLevel`, quality shown in the rarity line.

### Task 2.2 — Durability as an RPG system

Per the Phase 0 decision. If yes: `durability` and `max_durability` on the instance, drained by a `DamagePipelineEffect` at `APPLY` and by tool use; at 0 the item goes inert (never breaks). Repair is a smithing recipe (Task 3.3). If no: remove the global cancel and document it.

### Task 2.3 — Elemental damage

`DamageContext.components: MutableMap<DamageTypeKey, Double>` and the `ELEMENTAL_BREAKDOWN` stage exist with no types. Add builtin damage types (physical, fire, frost, lightning, arcane, poison, true), per-type resistance stats, a `Resistances` stage before `ARMOR_MITIGATION`, and `DamageTag` coverage. Weapons declare a component split; enchants/affixes/potions add components.

---

## Phase 3 — Stations: crafting, smithing, enchanting

One mechanism, three uses.

### Task 3.1 — `Station` and `Recipe` API

```
api/crafting/
  Station         key, displayName, block matcher (which vanilla block opens it), menu layout, permitted recipe kinds
  Recipe          key, station, inputs: List<Ingredient>, requirements, cost (money/xp/reagents), outcome
  Ingredient      ItemKey | material tag | any item with category X | instance predicate (e.g. "a sword with quality ≥ 0.5")
  RecipeOutcome   sealed: NewItem(def, initSpec) | UpgradeInput(slot, +upgradeLevel) | Repair(slot) | Enchant(slot, enchant, level) | Transmute(...)
  QualityRoll     skill-scaled roll → quality/bonus stats; critical-craft chance
  RecipeRegistry  ContentRegistry-backed; HOCON-loadable
  CraftingService open(player, station) → RamCore MenuSession; craft(player, recipe, inputs) → Result
```

- Station GUIs are RamCore `menu` sessions with input slots, a preview slot rendered through `PacketItemRenderer`, a recipe book page (`PaginatedMenu`) filtered by what the player can currently make.
- Skill XP grant on craft via the existing `XpSource` mechanism; `NonCombatXpListener` should not need editing.
- Tests: ingredient matching, quality roll determinism with a seed, requirement gating, outcome application.

### Task 3.2 — Crafting content

- Material tiers (e.g. iron → steel → mithril → adamant → …) as `ItemDefinition`s with `ItemCategory.MISC` and a `materialTier` tag; recipes chain tiers.
- Weapon and armor recipes for each tier and each vanilla category in `ItemCategory`; outputs use `statRolls` so two crafted swords differ.
- Reagents dropped by mobs (Phase 6) and gathered by skills so crafting consumes the economy.

### Task 3.3 — Smithing

- Upgrade recipes: `RecipeOutcome.UpgradeInput` consuming tier materials and money; cost from the existing upgrade cost curve; failure chance above a threshold that consumes materials but not the item (config).
- Repair recipes (if Task 2.2 says yes).
- Reforge station: move `ReforgeStatProvider`/`ReforgeRegistry` behind a `RecipeOutcome.Reforge` so reforging is a recipe with a reagent cost rather than a special path.
- Socket cutting: adding a socket slot to an item as a smithing recipe; gem insertion/removal as recipes. (`SocketData`, `GemRegistry`, `SocketStatProvider` already exist.)

### Task 3.4 — Enchanting

`core/listeners/EnchantingListener.kt` and `EnchantmentRegistryImpl` exist. Replace the vanilla enchanting flow:

- Enchanting station: shows the enchant pool for the input item's categories, filtered by Enchanting skill level; cost in XP levels plus reagents; max level scales with skill.
- `RPGEnchantment` gains `maxLevel`, `applicableCategories`, `conflictsWith`, `rarity/weight`, and `skillRequirement`.
- Enchanted books as `ItemDefinition`s (category `ENCHANTED_BOOK`) with an apply recipe.
- Enchant extraction/transfer recipe (destroys the source item).
- Convert `builtin/enchants/BuiltinEnchants.kt` to HOCON and grow the set: at least 3 per weapon category, 3 per armor slot, 2 per tool, each expressed as `Effect`s only.

---

## Phase 4 — Buffs and potions

### Task 4.1 — `Buff` system

- `api/buffs/`: `BuffDefinition` (key, duration, stacking rule: refresh / stack-intensity / stack-duration / ignore, effects: `List<Effect>`, dispellable, display icon/color), `BuffInstance` (remaining ticks, stacks, source), `BuffService` (apply/remove/query), `BuffStatProvider`.
- Persist active buffs in `PlayerRpgData` so relog does not clear them (config per buff).
- UI: buff bar in `ActionBarUi` or a boss-bar row; use RamCore `presentation` for apply/expire feedback.
- Mob buffs too: `BuffService` targets `LivingEntity`, stored in RamCore `Metadata` for non-players.

### Task 4.2 — Potions and brewing

- Potion `ItemDefinition`s with an on-consume `TriggeredEffect` (`EffectTrigger.OnInteract(RIGHT)` or a new `OnConsume`) that applies a buff; splash/lingering variants apply to an area (reuse RamCore `selector`).
- Brewing station via Task 3.1; recipes gated by the Alchemy skill; quality affects duration/potency.
- Ingredients: herbs from Farming/Foraging, reagents from mobs, water/bottle base items.
- Ship ~12 potions across offense, defense, utility, and resistance (Task 2.3 damage types).

---

## Phase 5 — Perk trees, builds, armor sets

### Task 5.1 — Perk trees

- `api/perks/`: `PerkTree` (key, displayName, nodes), `PerkNode` (key, cost, prerequisites: all-of / any-of, maxRank, effects per rank, position for GUI), `PerkService` (unlock, refund, respec, points available), `PerkStatProvider`.
- Points: N per skill level from a config curve; one tree per combat-ish skill plus a general tree, or one big tree — record the choice in `docs/DESIGN.md`.
- Node effects: `StatEffect`, `DamagePipelineEffect`, `TriggeredEffect`, and a new `AbilityUnlockEffect` that grants an `Ability` from `AbilityRegistry`.
- GUI: grid menu with node icons, tooltips showing rank effects, greyed prerequisites; RamCore `menu`.
- Respec: consumable item or money cost; refunds all points.
- Persist unlocked nodes in `PlayerRpgData`.

### Task 5.2 — Builds and archetypes (optional, keep thin)

A `Class`/archetype is a starting perk tree, a category whitelist/penalty on `equipSlots`, and a stat multiplier set. Implement only if trees alone do not produce distinct builds. Do not build a separate class system.

### Task 5.3 — Armor sets

- `api/sets/`: `SetDefinition` (key, displayName, members: `Set<ItemKey>`, thresholds: `Map<Int, List<Effect>>`), `SetRegistry`, `SetStatProvider` counting equipped members on the existing equipment-dirty trigger.
- `LoreSection.SetBonus` rendering "Name (2/4)" with active/inactive threshold lines.
- Ship 3–4 sets per material tier that push a build direction (crit, tank, caster, ranged).

---

## Phase 6 — Mobs and difficulty

### Task 6.1 — Affixes

- `AffixDefinition` (key, displayName tag, weight, effects: `List<Effect>`, visual: glow color / particle via RamCore `presentation`), `AffixRegistry`.
- `EntitySpawnListener` rolls 0–N affixes for tiered spawns; store on the entity via RamCore `Metadata`/PDC; nameplate shows tags (RamCore `display`).
- Ship 10+: Vampiric, Frozen, Shielded, Splitting, Fiery, Berserk, Warded (elemental resist), Thorned, Swift, Regenerating.
- Loot and XP scale with affix count.

### Task 6.2 — Region level scaling

- Use RamCore `region` rules to tag areas with a level band and tier weights. `EntityProfileRegistry.resolve` picks the band from the spawn location; stats scale from base profile × band multiplier; drops pull from band-appropriate loot tables.
- Fallback: distance-from-spawn band when no region matches.

### Task 6.3 — `MobDefinition` for custom mobs

- Bundles: base `EntityType` (or Mythic type), `EntityProfile`, equipment (RPG item instances), `Ability` list (`AbilityTrigger.PassiveTick` / `BossSignal`), AI goals via RamCore `ai`/`brain`, nameplate/display, loot table.
- Spawn via `MobService.spawn(def, location, level)`; spawner rules (region + weight + cap) as content.
- HOCON-loadable; convert `builtin/entities/BuiltinEntities.kt` specs to `content/mobs/vanilla.conf`.

### Task 6.4 — Bosses via RamCore encounters

- Boss = `MobDefinition` + RamCore `EncounterDefinition` (phases, `EncounterAbility`, signals). RamRPG abilities subscribe through `AbilityTrigger.BossSignal`.
- Boss bar, phase announcements, and arena boundaries via RamCore `presentation` + `region`.
- Party participation and instanced loot via RamCore `party` + `InstancedLoot` (Task 1.1 hook).
- Ship 2 bosses end-to-end as the reference implementation.

---

## Phase 7 — Economy and world hooks (after the above)

- Vendors: NPC merchants using RamCore `npc` + `trade`, buying/selling RPG items through `EconomyService`.
- Dungeons: consume RamCore instanced worlds when available (RamCore task 3.2).
- Daily/weekly content via RamCore real-time scheduling (RamCore task 3.8).

---

## Suggested order

1. 0.1 → 1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.6 → 1.7
2. 2.1 → 2.2 → 2.3
3. 5.3 (armor sets — quick win that proves providers + lore + HOCON end to end)
4. 3.1 → 3.2 → 3.3 → 3.4
5. 4.1 → 4.2
6. 5.1 → (5.2 only if needed)
7. 6.1 → 6.2 → 6.3 → 6.4
8. Phase 7

## Cross-repo dependencies

| RamRPG task | Needs from RamCore |
|---|---|
| 1.1 | loot API gaps, if any, added upstream |
| 1.2 | reward extension point for custom reward types |
| 1.4 | swap-in when RamCore 1.1/1.2 ship |
| 1.5 | RamCore 1.6 `ContentLoader` (adopt when available) |
| 1.6 | `presentation`, `display`, `packet` as they stand |
| 6.4 | `encounter`, `party`, `InstancedLoot` as they stand |
| Phase 7 | RamCore 3.2 (instanced worlds), 3.8 (real-time scheduling) |

## Definition of done

- No RPG-side duplicates of loot, rewards, objectives, cooldowns, or menu plumbing remain.
- A new item, enchant, recipe, set, perk, buff, affix, or mob can be added with a `.conf` file and `/rpg reload`, with validation errors pointing at file and path.
- A player can: craft a tiered weapon, upgrade and reforge it at a smithing station, enchant it, brew a potion that buffs them, spend perk points into a build, equip a 4-piece set, and kill an affixed elite and a phased boss in a level-scaled region — with every stat visible in lore and the stats GUI.
