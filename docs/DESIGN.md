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

- [1.3 — Ability cooldown scopes and persistence](design/1.3-cooldown-scopes.md)

## Phase 0 baseline

- [0.1 — Stats and the damage pipeline](design/0.1-stats-and-pipeline.md)
- [0.1 — Curves, rarity rules, and tiers](design/0.1-curves-and-tiers.md)
- [0.1 — Durability (D2 = YES)](design/0.1-durability.md)
- [0.1 — Perk topology (D3)](design/0.1-perk-topology.md)
- [0.1 — Target power curve](design/0.1-power-curve.md)
- [0.1 — Economy placeholders (G4 deferred)](design/0.1-economy-placeholders.md)
