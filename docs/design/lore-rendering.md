# Lore rendering — wiring deferred lore into the packet renderer (wire-deferred-lore)

Several earlier WPs *computed* item lore but never wired it into `PacketItemRenderer`, so players
never SAW it. This inserted WP closes that gap. The mechanics were already correct (WP-2.1a/b/c,
WP-5.3); this makes them visible on the item a player is looking at.

## What is now surfaced (per viewer)

`PacketItemRendererImpl.renderUncached` builds a `LoreContext` for every rendered RPG item. It now
populates the two viewer-state fields that used to default to `null`, so the whole
`LoreTemplate.DEFAULT` renders in its designed order:

- **Requirements** (`LoreSection.Requirements`) — one line per `ItemDefinition.requirements` entry,
  **green when met / red when unmet FOR THAT VIEWER**, plus the shared inert banner when any line is
  unmet. Fed by `requirementState`, built through the SAME `requirementStateFor(viewer, services)`
  no-self-satisfaction path WP-2.1c's `StatProvider`s use — so a lore line agrees with whether the
  item is actually inert for that viewer. `skillNameLookup` is wired too, so a skill-level
  requirement reads "Combat 10", not "combat 10".
- **Set bonus** (`LoreSection.SetBonus`) — "Name (active/total)" (e.g. `Crit Set (2/4)`) with each
  threshold lit (green) when active and dimmed (dark grey) when not, for **that viewer's** equipped,
  non-inert set-member count. Fed by `setBonus` (`SetLoreInfo`), built by `setLoreInfoFor(...)`, which
  reuses `SetStatProvider`'s existing `equippedDefs` + `equippedSetCount` (inert-aware) count — the
  inert-exclusion rule is **not** re-implemented here.
- **Item level, durability, quality** — these already read only `def`/`instance` (not viewer state),
  so they rendered in the cached path before this WP. This WP keeps them and adds a test
  (`DeferredLoreWiredTest`) proving they appear in the full template alongside the newly-wired
  sections. Quality rides the rarity line (`LoreRender.rarity`) as a band word (Crude…Masterwork).

No new lang keys were added; all lines reuse existing keys (`ramrpg.item.requirement.*`,
`ramrpg.item.level`, `ramrpg.item.durability`, `ramrpg.item.quality.*`, `ramrpg.item.inert`,
`ramrpg.set.progress`, `ramrpg.set.threshold`).

## Per-viewer caching — approach B (extend the cache key), and why

`requirementState` (viewer skills/stats) and `setBonus` (viewer equipped count) are **per-viewer
state**, not properties of `(itemHash, locale)`. If they were baked into the existing cached snapshot
unchanged, a player who levelled a skill or swapped a set piece would keep seeing stale lore until some
unrelated property invalidated the cache.

Two sound options were on the table:

- **(A) compose these sections post-cache** per viewer (like `applyCooldownLore`), splicing them in at
  their correct `LoreTemplate` positions; or
- **(B) extend the cache key** with a per-viewer-state hash so the full template caches per
  viewer-state.

**Chosen: B.** `RenderCacheKey` gains a `viewerStateHash` field, computed by
`PacketItemRendererImpl.viewerStateHash(def, reqState, setInfo)`, which hashes only what changes the
rendered output: whether each requirement is met, and the active set-member count.

Rationale:

- Unlike **cooldown** — which ticks continuously, so it (correctly) stays a post-cache compose and is
  NOT part of the key — requirement/set state changes only on **discrete** skill-up / equipment-change
  events. Caching per viewer-state therefore refreshes naturally on exactly those events and is not a
  per-tick cost.
- B keeps the full template — **including correct section ORDER** — intact, avoiding A's fragile
  "splice a section in at the right line index" logic (positions shift with how many lines the other
  sections produced).
- The state fields are built *before* the cache lookup (a few skill-level reads plus one equipped-slot
  scan), which is far cheaper than the `ItemStack` clone + component build they gate, so folding them
  into the key is cheap.
- Cache growth is bounded: the hash derives only from THIS item's requirement met/unmet booleans and
  the active count of THIS item's set, so most items share the same state hash for most players.

Result: a player whose skills or equipped set pieces changed misses the cache and re-renders with fresh
lore — **without any unrelated cache invalidation**. Covered by `RenderCacheTest` (`a different viewer
state on an otherwise identical key misses the cache`).

## Wiring — module-injected hooks, no `RamRPG.kt` change (B5)

The `RENDERER` service is constructed in `RamRPG.load()` *before* modules set up, and `SetRegistry` is
created in `SetModule.setup()`. So the renderer cannot constructor-inject the skill/stat/set services it
now needs. Instead, `PacketItemRendererImpl` exposes settable hooks (mirroring
`EntityProfileRegistryImpl.levelBandService`/`mythicResolver`), and MODULES set them from their
`setup()`, resolving the renderer from the `ServiceContext`:

- **`UiModule.setup()`** sets `requirementStateHook` (building `ItemRequirementServices` from
  ctx-resolved skillRegistry/skillService/stats, then `requirementStateFor`) and `skillNameLookup`.
- **`SetModule.setup()`** sets `setLoreHook` (from its `SetRegistry` + ctx-resolved
  `ItemInstanceService`/`ItemDefinitionRegistry` + the same `ItemRequirementServices` used for inert
  checks), delegating to `setLoreInfoFor`.

`RamRPG.kt` is untouched. The two modules set different hooks on the same renderer instance, so their
setup order does not matter. While a hook is unset (e.g. before modules run, or in pure tests), the
renderer degrades to the prior fail-closed behaviour: requirement lore renders all-unmet and set lore
renders nothing.

## Cooldown lore stays deferred (blocked on an item→ability data model)

`applyCooldownLore` remains a post-cache per-viewer step, but is still **inert**: there is no
`ItemKey → AbilityKey` link in the data model today. Abilities fire via
`AbilityService.tryFire(trigger, item-context)`; no `ItemDefinition` field maps an item to an ability,
and `abilityKeyOf` defaults to `{ null }`. Inventing such a link is out of scope for this WP (rule 9).
A grep for any existing item→ability mapping found none. Cooldown lore therefore stays deferred until a
future WP models item-granted abilities and supplies a real `abilityKeyOf`.

## Tests & the design-coverage baseline

Pure, off-server tests (the renderer's own path needs a live server for `ItemStack`/`ItemMeta`, so
assertions are at the `LoreContext`/`LoreTemplate`/hook layer):

- `DeferredLoreWiredTest` — the full `LoreTemplate.DEFAULT`, populated as `renderUncached` now populates
  it, surfaces requirement (met green / unmet red), item level, durability, quality (rarity line), and
  set-bonus progress together.
- `RenderCacheTest` — extended: `viewerStateHash` is part of the key, and a changed viewer state misses
  the cache.

`docs/DESIGN.md` is regenerated at merge time, so `DesignSectionCoverageTest` is expected to be red on
this branch (this note is not yet stitched into `DESIGN.md`); that is the known, expected failure.
