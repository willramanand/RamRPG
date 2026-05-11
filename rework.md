# RamRPG Architectural Review

## 1. Prioritized Issue List

### P0 — Thread-safety + truth-source bugs

1. **`PlayerRepository.kt:113-141` — async stat recalc + Bukkit access on async thread.** Two `Schedulers.async().runRepeating(... 1L, 1L)` loops call `plugin.stats.allocateStats(data)` and then `applyModifiers(player, null)` every tick async. `allocateStats` reads `player.equipment.*` and `Items.retrieve(item)` (touches `ItemMeta`/PDC); `applyModifiers` mutates `Attribute.MAX_HEALTH`, `walkSpeed`, `healthScale`, calls `player.health = ...`. **All Bukkit. All async. Race + Folia incompatible.** Mana regen loop writes `data.currentMana` async while sync `EntityRegainHealthEvent` reads it. Fix: dirty-flag scheduler on sync (or `Schedulers.forEntity`) — see §4.
2. **Stat recalc per tick.** Even synced, `allocateStats` runs every tick for every online player. Iterates all `Stat.entries`, all equipment slots, all enchant maps. Heavy. Replace with dirty flag + `StatService.snapshot`.
3. **`Items.retrieve` falls back to `Material.name`** (`Items.kt:100-103`) — vanilla items get treated as RPG items, lore overwrite via packet path mutates non-RPG vanilla stacks. Should require explicit identity tag (PDC item-key) only.
4. **`ItemListeners.applyModifications` mutates `meta.itemFlags`/attribute modifiers and writes lore on every WINDOW_ITEMS / SET_SLOT.** Real risks:
   - Creative-mode middle-click clones the *modified* packet stack? In ProtocolLib SET_SLOT path, server item is cloned via `clone()` then mutated — but `meta.itemFlags.addAll(...)` works on a fresh meta only if `setItemMeta` was called after; check ordering. Currently meta is mutated then `newItem.itemMeta = meta`. Ok, but `meta.addAttributeModifier(...)` for "hide_attributes_modifier" with operation `MULTIPLY_SCALAR_1` and amount `0.0` ≠ no-op — `MULTIPLY_SCALAR_1` of 0 multiplies by 1 (vanilla quirk: 0 = +0%). Mostly cosmetic but confusing.
   - Anvil/Smithing/Grindstone: client preview reads from server inventory, not the rendered stack. Output items use server PDC. So far ok, but item-name rendering relies on `meta.displayName() == null` check — rename in anvil persists the rendered name into server item if not careful. Verify rename flow.
   - Item frames, shulker preview tooltips, dropped item entities: WINDOW_ITEMS doesn't cover them. Use `SET_ENTITY_DATA` for dropped items + bundle/shulker contents come through different paths (SET_CONTAINER_CONTENT or `DataComponentPatch`). Currently un-rendered for those.
   - `META_ITEM` stack inside bundles — packet contains nested stacks; only top-level read. Bundle rendering missing.
5. **`PlayerItemDamageEvent` cancelled globally** — items never break. Intentional? Unrelated to review goals but a gameplay-truth concern: code elsewhere assumes durability. Document.

### P1 — Extensibility blockers

6. **`Items` enum** with hardcoded balance values — every rebalance requires recompile + migration. Also tiny pool: only vanilla-shape items, no custom items. Replace with `ItemDefinition` registry, HOCON-loaded.
7. **`Stat` enum + `Stat.entries` iteration everywhere** (`StatRepository`, `StatListeners`, `applyModifiers`, `PlayerData.statPoints: MutableMap<Stat,…>`). Adding a new stat = touching ~6 files. Migrate to `StatKey` registry.
8. **`Skill` enum + `Skill.entries`** in `PlayerData.skillsLvl`/`skillsXp` (EnumMap) — same problem.
9. **`SourceRegistry.kt:14-19`** — display-name reflection: `Class.forName("…source.${skill.lowercase()}.${displayName}Source")`. Brittle, breaks on rename, crashes on plugin reload, blocks third-party sources. Hard P1.
10. **`StatRepository.handleItemStatModifiers` 60-line if-cascade** for which enchantment provides which stat. Each enchant should declare its own stat contribution via `Effect`s; service iterates contributions.
11. **`StatListeners.register` is one giant 250-line lambda combining damage, defense, true-defense, regen, fortune, ore-conversion** — no pipeline, no composition, can't add elemental damage / shields / new modifier classes without editing this method.
12. **`Enchantments.kt`** is a static field-per-enchant + manual `putIfAbsent` block 50× duplicated. New enchant = edit Enchantments.kt + create class + add to `register()`. Should be auto-registered via API.
13. **`CustomEnchantment.handleStats(lvl): Int`** returns `Int` for all stats — Defense/health regen/crit-damage all integer-quantized. And stat target is implicit (caller decides via if-tree). No `Effect` model.
14. **`EntityStats` enum** + `EntityStats.valueOf(entity.type.name)` — adding a custom entity = recompile. Replace with registry keyed by `EntityType` + namespaced custom keys for MM mobs. Same for `CombatSource` and the other 11 source enums.
15. **Indicator code in `Indicators.kt`** — packet construction is hardcoded for entity ID 15 / 23 indices — these are MC-version-sensitive. RamCore has a `display/TextDisplaySpec` and `packet/ProtocolVisualPacketFactory` — use them.

### P2 — UX / cleanup

16. `RamRPG.get()` singleton + `lateinit` services. Shift to a service container and inject.
17. `enums/CriticalType.kt` — fine as enum but should be `CritTier` registry-backed.
18. No tests at all. Suggested test list at end.
19. Package layout flat-by-feature. Suggest api/core/builtin split per spec.
20. Many `e.printStackTrace()` swallowing exceptions silently; route through `RamExceptions`.
21. `ItemListeners` writes lore via MiniMessage every packet — costly. Cache rendered lore per `(itemHash, viewerLocale)`.
22. `ManaAbility.kt` is **fully commented out** (452 lines). Either delete or re-implement under new `Ability` API.

---

## 2. Target Architecture

```
ramrpg/
├─ api/                        # public, depend-only surface
│   ├─ identity/               ItemKey, StatKey, SkillKey, EnchantmentKey, AbilityKey, EffectKey
│   ├─ stats/                  StatDefinition, StatModifier, StatSnapshot, StatProvider, StatService
│   ├─ skills/                 SkillDefinition, XpCurve, XpSource, SkillReward
│   ├─ items/                  ItemDefinition, ItemIdentity, ItemInstanceData, ItemRenderer,
│   │                          ItemDefinitionRegistry, ItemInstanceService, LoreTemplate
│   ├─ enchants/               RPGEnchantment, EnchantmentRegistry
│   ├─ effects/                Effect (sealed), EffectTrigger, EffectAction, ScalingFormula, Condition
│   ├─ combat/                 DamageContext, DamageStage, DamagePipeline, DamageTag
│   ├─ abilities/              Ability, AbilityTrigger, ResourceCost, Cooldown, AbilityResult
│   ├─ entities/               EntityProfile, EntityProfileRegistry
│   └─ registry/               Registry<K,V> facade over RamCore ContentRegistry
│
├─ core/                       # internal services
│   ├─ services/               StatServiceImpl, AbilityServiceImpl, EnchantmentServiceImpl,
│   │                          ItemInstanceServiceImpl, DamagePipelineImpl
│   ├─ storage/                PlayerStore, ItemSchemaMigrator
│   ├─ listeners/              CombatListener (thin), EquipmentListener (thin), XpListener
│   ├─ platform/               RamRpgScheduler (delegates to RamCore Schedulers)
│   ├─ rendering/              PacketItemRenderer, RenderCache, IndicatorRenderer
│   └─ config/                 ConfigLoaders (HOCON), RegistryConfigBinders
│
└─ builtin/                    # ships out of the box; could be split into sub-plugins later
    ├─ stats/                  builtin StatDefinitions (damage, str, …)
    ├─ skills/                 builtin SkillDefinitions + XpSources
    ├─ items/                  builtin ItemDefinitions (vanilla wrappers + custom)
    ├─ enchants/               builtin RPGEnchantments mapped onto Effects
    ├─ abilities/              vein miner, quickshot, …
    ├─ effects/                lifesteal, fire aspect, fortune-extra-drops, …
    └─ entities/               EntityProfiles for vanilla mobs + tier variants
```

**Final design rule:** Effects ⇒ Contexts ⇒ Pipelines ⇒ Registries ⇒ Services.

---

## 3. Concrete API (Kotlin)

### Identity

```kotlin
package dev.willram.ramrpg.api.identity

@JvmInline value class StatKey(val id: ContentId) {
    companion object { fun of(ns: String, v: String) = StatKey(ContentId.of(ns, v)) }
}
// same shape: SkillKey, ItemKey, EnchantmentKey, AbilityKey, EffectKey, EntityProfileKey, DamageTypeKey
```

Reuse `dev.willram.ramcore.content.ContentId` to share namespacing.

### Stats

```kotlin
data class StatDefinition(
    val key: StatKey,
    val displayName: Component,
    val symbol: String? = null,
    val color: TextColor = NamedTextColor.WHITE,
    val defaultBase: Double = 0.0,
    val perLevel: Double = 0.0,
    val min: Double? = null,
    val max: Double? = null,
    val format: StatFormat = StatFormat.WHOLE,
)

enum class ModifierOperation { ADD, MULTIPLY_BASE, MULTIPLY_TOTAL }

data class ModifierSource(val type: SourceType, val ref: ContentId)
enum class SourceType { BASE, SKILL, ITEM, ENCHANT, REFORGE, SOCKET, BUFF, REGION, BOSS, PERM }

data class StatModifier(
    val stat: StatKey,
    val amount: Double,
    val operation: ModifierOperation,
    val source: ModifierSource,
)

data class StatSnapshot(private val values: Map<StatKey, Double>) {
    fun get(stat: StatKey): Double = values[stat] ?: 0.0
    operator fun get(k: StatKey) = get(k)
}

interface StatProvider {
    fun provideStats(ctx: StatContext, output: MutableList<StatModifier>)
}

interface StatService {
    fun register(provider: StatProvider, owner: String)
    fun markDirty(player: Player, reason: StatDirtyReason)
    fun snapshot(player: Player): StatSnapshot
    fun recalculateNow(player: Player): StatSnapshot
}

enum class StatDirtyReason {
    EQUIPMENT_CHANGED, SKILL_LEVEL_CHANGED, ENCHANT_CHANGED,
    EFFECT_ADDED, EFFECT_REMOVED, WORLD_CHANGED, INSTANCE_DATA_CHANGED, JOIN
}
```

Aggregation order in `StatServiceImpl.compute`:

```
result[k] = clamp((Σ ADD) * (1 + Σ MULTIPLY_BASE)) * Π(1 + MULTIPLY_TOTAL)
```

### Items

```kotlin
data class ItemKey(val id: ContentId)

data class ItemIdentity(
    val key: ItemKey,
    val instanceId: UUID? = null,
    val schemaVersion: Int = CURRENT_SCHEMA,
)

data class SocketData(val key: ContentId, val gem: ContentId? = null)
data class ReforgeKey(val id: ContentId)

data class ItemInstanceData(
    val identity: ItemIdentity,
    val upgradeLevel: Int = 0,
    val reforge: ReforgeKey? = null,
    val sockets: List<SocketData> = emptyList(),
    val customRolls: Map<StatKey, Double> = emptyMap(),
    val enchantments: Map<EnchantmentKey, Int> = emptyMap(),
    val owner: UUID? = null,
)

data class ItemDefinition(
    val key: ItemKey,
    val displayName: Component,
    val material: Material,
    val rarity: Rarity,
    val categories: Set<ItemCategory>,
    val baseStats: List<StatModifier>,
    val effects: List<Effect>,
    val loreTemplate: LoreTemplate,
    val maxStack: Int? = null,
    val customModelData: Int? = null,
)

interface ItemDefinitionRegistry {
    fun get(key: ItemKey): ItemDefinition?
    fun register(owner: String, def: ItemDefinition)
    fun all(): Collection<ItemDefinition>
}

interface ItemInstanceService {
    fun identify(item: ItemStack): ItemInstanceData?
    fun create(def: ItemDefinition, init: ItemInstanceInit = ItemInstanceInit()): ItemStack
    fun read(item: ItemStack): ItemInstanceData?
    fun write(item: ItemStack, data: ItemInstanceData): ItemStack
}

interface ItemRenderer {
    fun render(viewer: Player, item: ItemStack, ctx: ItemRenderContext): ItemStack
}
```

Persisted PDC layout (JSON-encoded under namespaced key `ramrpg:item`):

```json
{ "v":1, "k":"ramrpg:inferno_blade", "iid":"…uuid…",
  "u":3, "r":"ramrpg:fierce",
  "s":[{"k":"ramrpg:slot_1","g":"ramrpg:ruby_3"}],
  "rolls":{"ramrpg:strength":12.0},
  "ench":{"ramrpg:sharpness":5,"ramrpg:lifesteal":3},
  "owner":"…uuid…"}
```

Identity + instance only. No lore. No final stats. `v` lets `ItemSchemaMigrator` transform old shapes lazily on read.

### Enchantments

```kotlin
interface RPGEnchantment {
    val key: EnchantmentKey
    val displayName: Component
    val maxLevel: Int
    val targets: Set<ItemCategory>
    val rarity: EnchantmentRarity
    fun description(level: Int): List<Component>
    fun effects(level: Int): List<Effect>
    fun conflicts(other: EnchantmentKey): Boolean = false
    fun bookshelfPower(level: Int): Int = 0
    fun xpCost(level: Int, ctx: EnchantingContext): Int = level * 10
}
```

Old `handleStats(Int):Int` / `handleEffects()` go away. Stat contributions become `StatEffect`. Damage hooks become `DamagePipelineEffect`. Fortune chance becomes `TriggeredEffect(BlockBreak, Stat≥roll, GiveExtraDrop)`.

### Effects

```kotlin
sealed interface Effect { val key: EffectKey }

data class StatEffect(
    override val key: EffectKey,
    val stat: StatKey,
    val amount: ScalingFormula,
    val operation: ModifierOperation,
) : Effect

data class DamagePipelineEffect(
    override val key: EffectKey,
    val stage: DamageStage,
) : Effect

data class TriggeredEffect(
    override val key: EffectKey,
    val trigger: EffectTrigger,
    val conditions: List<Condition> = emptyList(),
    val action: EffectAction,
) : Effect

fun interface ScalingFormula { fun eval(ctx: ScalingContext): Double }
object Scaling {
    fun flat(v: Double) = ScalingFormula { v }
    fun linear(perLevel: Double) = ScalingFormula { it.level * perLevel }
    fun expr(s: String) = ExpressionScaling(s)
}

sealed interface EffectTrigger {
    object OnEquip : EffectTrigger
    object OnHit : EffectTrigger
    object OnHurt : EffectTrigger
    object OnKill : EffectTrigger
    data class OnInteract(val type: InteractType) : EffectTrigger
    data class OnBlockBreak(val matcher: BlockMatcher) : EffectTrigger
    object Tick : EffectTrigger
    data class Custom(val key: ContentId) : EffectTrigger
}

fun interface EffectAction { fun execute(ctx: EffectContext) }
fun interface Condition { fun test(ctx: EffectContext): Boolean }
```

### Combat pipeline

```kotlin
data class DamageContext(
    val attacker: LivingEntity?,
    val victim: LivingEntity,
    val cause: EntityDamageEvent.DamageCause,
    var weapon: ItemStack? = null,
    var baseDamage: Double,
    var finalDamage: Double = baseDamage,
    val tags: MutableSet<DamageTag> = mutableSetOf(),
    val components: MutableMap<DamageTypeKey, Double> = mutableMapOf(),
    val metadata: MutableMap<String, Any> = mutableMapOf(),
)

interface DamageStage {
    val key: ContentId
    val priority: Int
    fun apply(ctx: DamageContext)
}

interface DamagePipeline {
    fun register(stage: DamageStage)
    fun unregister(key: ContentId)
    fun process(ctx: DamageContext): DamageContext
}
```

Default builtin stages (registered with stable priorities so users can splice between):

```
 100  WeaponBaseStage
 200  StrengthStage
 300  EnchantOffenseStage          (sharpness, power, ...)
 400  EffectOffenseStage           (TriggeredEffects with OnHit + offense)
 500  CritRollStage
 600  ElementalBreakdownStage
 700  AbilityModifierStage
1000  --- attack/defense boundary ---
1100  ArmorMitigationStage
1200  TrueDefenseStage             (fire/lava → trueDefense)
1300  EnchantDefenseStage          (protection, blast prot, ...)
1400  ShieldsStage
1500  ThornsStage
1600  LifestealStage
1700  FerocityStage                (re-enqueues secondary DamageContext)
1900  IndicatorStage               (emit display only)
2000  ApplyStage                   (writes back into Bukkit event)
```

Listener becomes ~10 lines.

### Skills + XP sources

```kotlin
data class SkillDefinition(
    val key: SkillKey,
    val displayName: Component,
    val description: Component,
    val maxLevel: Int,
    val xpCurve: XpCurve,
    val rewards: List<SkillReward>,
    val barColor: BossBar.Color,
)

fun interface XpCurve { fun xpToReach(level: Int): Double }
object XpCurves { fun polynomial(base: Double, mul: Double) = XpCurve { lvl -> base + mul * lvl } }

sealed interface SkillReward
data class StatPerLevelReward(val stat: StatKey, val amountPerLevel: Double): SkillReward
data class UnlockAbilityReward(val ability: AbilityKey, val atLevel: Int): SkillReward

data class XpSourceKey(val id: ContentId)

interface XpSource {
    val key: XpSourceKey
    val skill: SkillKey
    fun xp(ctx: XpContext): Double
}

interface SkillService {
    fun addXp(p: Player, src: XpSource, ctx: XpContext)
    fun level(p: Player, skill: SkillKey): Int
    fun xp(p: Player, skill: SkillKey): Double
}
```

`CombatSource` enum is replaced by `XpSource` instances keyed by `EntityProfileKey` and registered through HOCON loader at boot. No reflection, no display-name lookup.

### Abilities

```kotlin
interface Ability {
    val key: AbilityKey
    val triggers: List<AbilityTrigger>
    val requirements: List<Requirement>
    val costs: List<ResourceCost>
    val cooldown: Cooldown
    fun execute(ctx: AbilityContext): AbilityResult
}

sealed interface AbilityTrigger {
    object RightClick : AbilityTrigger
    object LeftClick : AbilityTrigger
    object SneakRightClick : AbilityTrigger
    object EntityHit : AbilityTrigger
    object Killed : AbilityTrigger
    object Damaged : AbilityTrigger
    data class BlockBreak(val matcher: BlockMatcher) : AbilityTrigger
    data class PassiveTick(val periodTicks: Int) : AbilityTrigger
    data class BossSignal(val signal: ContentId) : AbilityTrigger
}

sealed interface ResourceCost { data class Mana(val amount: Double): ResourceCost }
data class Cooldown(val ticks: Long, val keyScope: CooldownScope = CooldownScope.PLAYER)
sealed interface AbilityResult { object Success: AbilityResult; data class Fail(val reason: Component): AbilityResult }
```

Use RamCore `cooldown.CooldownMap` for backing.

---

## 4. Threading + Folia

Wrap RamCore schedulers in a thin abstraction:

```kotlin
interface PlatformScheduler {
    fun runGlobal(task: Runnable)
    fun runAsync(task: Runnable)
    fun runForEntity(entity: Entity, task: Runnable)
    fun runAtLocation(loc: Location, task: Runnable)
    fun repeatGlobal(period: Long, task: Runnable): Cancellable
    fun repeatForEntity(e: Entity, period: Long, task: Runnable): Cancellable
}

class RamCorePlatformScheduler : PlatformScheduler {
    override fun runForEntity(e: Entity, t: Runnable) = Schedulers.forEntity(e).run(t).also{}
    override fun runAtLocation(l: Location, t: Runnable) = Schedulers.forRegion(l).run(t).also{}
    /* … */
}
```

Mandatory rules:
- DB / file I/O / GSON: `runAsync`.
- Touching player inventory / Attribute / health / location: `runForEntity(player)`.
- Touching world blocks / entities at a point: `runAtLocation(loc)`.
- After async completes, callbacks must `runForEntity` before touching Bukkit.

Concrete fixes:
- Replace `PlayerRepository`’s two async-1-tick loops with: dirty-flag stat recompute (sync per entity) + mana regen on `Schedulers.forEntity(player)` 1L/1L.
- `actionBar.startUpdateActionBar()` should also use entity scheduler.
- Auto-save remains async, but reads of `PlayerData` must be snapshot first on entity thread.

---

## 5. Packet rendering

Replace `ItemListeners.applyModifications` with `PacketItemRenderer` service that:

1. Subscribes via RamCore `Protocol` to: `WINDOW_ITEMS`, `SET_SLOT`, `SET_CONTAINER_CONTENT`, `SET_EQUIPMENT`, `SET_ENTITY_DATA` (item entities + item frames), bundle component patches, merchant offers, creative inventory.
2. For each `ItemStack`:
   - `ItemInstanceService.identify(item)` → `ItemInstanceData?`. If null and item is a "vanilla wrapper" `ItemDefinition`, fall back to material lookup *only when explicitly opted in* (whitelist on `ItemDefinition`); otherwise leave untouched.
   - `LoreRenderer.render(definition, instance, viewer)` → cached `Component` list.
   - `ItemRenderer.render` clones, sets meta, returns. **Never** mutates the original.
3. Cache key: `(itemKey, schemaVersion, instanceHash, viewerLocale, definitionRev)`. Invalidate on definition reload.
4. Bundle handling: walk `DataComponentPatch` for `bundle_contents` and re-render each child.
5. Anvil/Smithing/Grindstone: skip rendering for the *output* slot only when source items are RamRPG items, so server item PDC remains canonical (rename via anvil should set `customRolls` flag for "renamed" if you want, never persist generated lore).
6. Creative pick-block (middle-click) round-trips client→server `CreativeSetSlot` — server already trusts unfiltered NBT only from ops; ensure incoming creative item is **canonicalized**: read PDC identity, drop any client-injected lore, re-serialize via `ItemInstanceService.write`.
7. Add render hook for *named* containers (shulker preview tooltip). Vanilla shulker preview reads server-side `BlockEntityTag` — that path doesn't go through SET_SLOT. Either re-render at container open, or rebuild `BlockEntityTag.Items` server-side at place/break.

LoreTemplate sketch:

```kotlin
class LoreTemplate(private val sections: List<LoreSection>) {
    fun render(ctx: LoreContext): List<Component> = sections.flatMap { it.render(ctx) }
}

sealed interface LoreSection {
    object Stats : LoreSection
    object Description : LoreSection
    object Enchantments : LoreSection
    object EffectsHint : LoreSection
    object Rarity : LoreSection
    data class Static(val lines: List<Component>) : LoreSection
    data class Conditional(val cond: (LoreContext)->Boolean, val inner: LoreSection) : LoreSection
}
```

---

## 6. Migration plan (incremental, behaviour-preserving)

**Phase 0 — fix the P0 thread-safety bug now.** No new APIs needed.
- Move both `Schedulers.async().runRepeating(... 1L)` loops in `PlayerRepository` to sync (or `Schedulers.forEntity(player)`). Mana regen retains async fairness via `bucket.asCycle().next()` but mutation of `currentMana` and `applyModifiers` runs on entity thread.
- Remove `Schedulers.sync().run { Events.call(event) }` inside async lambda — entire mana logic should be sync.

**Phase 1 — adapter layer for keys.** Introduce `StatKey`/`SkillKey`/`ItemKey`. Provide bridges:
```kotlin
fun Stat.key() = StatKey.of("ramrpg", name.lowercase())
fun StatKey.legacy(): Stat? = Stat.entries.firstOrNull { it.key() == this }
```
All new code uses keys; old enums kept until phase 4.

**Phase 2 — registries + services.** Build `StatService`, `ItemDefinitionRegistry`, `EnchantmentRegistry`, `SkillService` backed by RamCore `ContentRegistry`. `StatRepository` becomes a `ConfigStatProvider` that publishes baseline `StatModifier`s. Old enum-driven calls delegate to `StatService.snapshot(player).get(stat.key())`.

**Phase 3 — damage pipeline.** Create `DamagePipeline` with current logic split into the listed stages 1:1 (refactor, no balance change). Delete `StatListeners.register()` body. Listener class shrinks to ~30 lines.

**Phase 4 — items + renderer.** Migrate `Items` enum to JSON/HOCON `ItemDefinition`s in `builtin/items/*.conf`. Write `ItemSchemaMigrator` that on read transforms old PDC keys (`ramrpg-item-type` string + bare `ramrpg-enchants` map) into new `ramrpg:item` JSON. Old PDC removed lazily on next write.

**Phase 5 — enchants → effects.** Rewrite each `enchants/impl/*` to declare `effects(level): List<Effect>` instead of `handleStats`. Damage stages and `StatService` consume them via providers wired from `ItemInstanceData.enchantments`. Delete `StatRepository.handleItemStatModifiers`.

**Phase 6 — sources + entity profiles.** Replace `EntityStats` and 11 `*Source` enums with a single HOCON file `entities.conf` keyed by `ramrpg:zombie`, plus tier overlays `ramrpg:elite_zombie`. `MythicMobs` integration adds `mythic:<mobtype>` keys at boot.

**Phase 7 — abilities.** Re-enable `ManaAbility.kt` content under new `Ability` interface. Delete commented file.

**Phase 8 — rendering hardening.** Cache + bundle/shulker support. Add fuzz tests.

---

## 7. Patches I'd land immediately (low risk)

A. **Thread-safety hotfix** — `PlayerRepository.kt`: move per-tick async loops to sync; drop async stat recalc entirely (use existing dirty-flag listeners).

B. **Drop name-based item fallback** in `Items.retrieve`: require explicit PDC identity tag.

C. **Replace `SourceRegistry` reflection** with explicit registration; no `Class.forName`.

D. **Quote `EntityStats.retrieve`/`CombatSource.retrieve` swallow** — log unknown-type once per type instead of silent ZOMBIE fallback.

E. **Delete `enchants/impl/tool/SmeltingTouch.kt`** dangling stub — already commented out in registry.

---

## 8. Risky changes — manual test before merge

- Packet rendering changes: test in vanilla anvil rename, smithing template, grindstone repair, creative pick-block, /give, /loot, shulker preview tooltip, bundles, item frames, dropped items pickup, hopper transfer, /clear (PDC preserved), Adventure-mode CanPlaceOn/CanDestroy, donkey chests, Ender chests sync.
- Schedule migration to `forEntity`: verify no `IllegalStateException: not on main` *and* no Folia "wrong region" exceptions.
- Stat dirty-flag rollout: confirm health regen, attack speed, walk speed all update on equipment change *and* on world change (current sync points).
- Enchantment effect migration: regression-test damage numbers vs old hardcoded `handleStats` constants. Snapshot expected values per level into a test.
- Item schema migrator: round-trip old PDC → new PDC → old form must yield identical gameplay state.

---

## 9. Tests to add

```
StatServiceTest
  - ADD then MULTIPLY_BASE then MULTIPLY_TOTAL aggregation
  - clamp min/max
  - dirty flag: snapshot returns cached until markDirty
DamagePipelineTest
  - stage ordering by priority
  - ferocity re-enqueue does not infinite-loop
  - true damage tag bypasses ArmorMitigationStage
ItemInstanceServiceTest
  - write→read roundtrip, schema upgrade v0→v1
  - identify returns null for unmarked vanilla item
PacketRendererTest
  - render does not mutate input ItemStack (deep equals on input pre/post)
  - bundle children rendered
SkillXpCurveTest
  - polynomial curve matches reference values
  - level-up boundary precision
EnchantmentEffectTest
  - SHARPNESS lvl N produces StatEffect with correct ScalingFormula(level)
  - LIFESTEAL OnHit triggers regen action
  - conflicting enchant pair rejected
SchedulerThreadingTest (integration via Folia harness)
  - ApplyModifiers always reaches entity scheduler
SerializationTest
  - PDC JSON shape stable; unknown fields ignored; missing v defaults to 1
```

---

## 10. Summary

Big wins available immediately: kill async stat/attribute mutation loop, drop name-fallback item identification, replace reflection-based source registry. Medium effort: extract DamagePipeline + StatService — frees from 250-line listener and per-tick recalc. Long arc: items move to definition+instance model, enchants become effects, lore becomes packet-rendered template. RamCore already supplies registries, schedulers, packet transports — lean on those instead of inventing parallel ones in RamRPG.
