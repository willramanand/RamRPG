# RamRPG design index

**Generated index — do not hand-edit in a worktree.** The orchestrator regenerates this from
`docs/design/*.md` after every merge (execution-plan F.3 step 5a). Each WP that chooses numbers or
formulas writes its own `docs/design/<wp-id>-<slug>.md`; this file just links them. `DesignSectionCoverageTest`
asserts this index and `docs/design/` stay in one-to-one sync.

Read this before implementing any WP: it is the single source of truth for numbers, formulas, the
damage pipeline, and the dataVersion ledger.

## Foundation

- [F5 — PlayerRpgData dataVersion ledger](design/F5-dataversion-ledger.md)

## Phase 1

- [1.1a — Loot tables and drop rates](design/1.1a-loot-tables.md)
- [1.1b — Boss loot instancing and claim policy](design/1.1b-boss-loot-instancing.md)
- [1.2a — Quest model and objective mapping](design/1.2a-quest-objectives.md)
- [1.2b — Reward types and parameters](design/1.2b-reward-types.md)
- [1.3 — Ability cooldown scopes and persistence](design/1.3-cooldown-scopes.md)
- [1.5a — Content schema: file layout and type map](design/1.5a-content-schema.md)
- [1.5b — Effect schema, builtin action/condition/matcher ids](design/1.5b-effect-schema.md)
- [1.5c — Reload semantics and what is not hot-swappable](design/1.5c-reload-semantics.md)
- [1.5d — Builtin items as content (parity + first-run extraction)](design/1.5d-builtin-content.md)
- [1.6a — HUD composition order and slot budget](design/1.6a-hud-composition.md)

## Phase 2

- [2.1a — Item level bands and requirement gates](design/2.1a-item-level-requirements.md)
- [2.1b — Quality tiers and durability model](design/2.1b-quality-durability.md)
- [2.1c — Inert item rules](design/2.1c-inert-items.md)
- [2.2 — Durability drain rates and repair costs](design/2.2-durability.md)
- [Lore rendering — surfacing requirement/quality/durability/set lore per-viewer](design/lore-rendering.md)
- [2.3a — Damage types, split rules and resistance formula](design/2.3a-damage-types.md)

## Phase 3

- [3.0 — Command surface and the /skills deprecation map](design/3.0-command-surface.md)
- [3.1a — Station and recipe model; quality roll formula; critical-craft chance](design/3.1a-crafting-model.md)
- [3.1c — Crafting XP rates](design/3.1c-crafting-xp.md)
- [3.2a — Material tier ladder](design/3.2a-material-tiers.md)
- [3.3a — Upgrade cost curve and failure thresholds](design/3.3a-upgrade-costs.md)
- [3.3c — Reforge costs](design/3.3c-reforge-costs.md)
- [3.3d — Socket slot caps and gem costs](design/3.3d-socket-costs.md)

## Phase 5

- [5.3 — Set bonus thresholds and shipped sets](design/5.3-armor-sets.md)

## Phase 6

- [6.2 — Level bands, multipliers and the distance fallback curve](design/6.2-level-bands.md)
- [1.7b — Service graph, module seams and construction order](design/1.7b-service-graph.md)

## Phase 0 baseline

- [0.1 — Stats and the damage pipeline](design/0.1-stats-and-pipeline.md)
- [0.1 — Curves, rarity rules, and tiers](design/0.1-curves-and-tiers.md)
- [0.1 — Durability (D2 = YES)](design/0.1-durability.md)
- [0.1 — Perk topology (D3)](design/0.1-perk-topology.md)
- [0.1 — Target power curve](design/0.1-power-curve.md)
- [0.1 — Economy placeholders (G4 deferred)](design/0.1-economy-placeholders.md)
