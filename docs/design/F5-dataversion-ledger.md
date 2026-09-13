# F5 — PlayerRpgData dataVersion ledger

`PlayerRpgData` is persisted through RamCore's `PlayerDataService` over a migration-capable
`Stores.file(...)` store (`RamRPG.load()`). The store keeps a `StoreMigrations<PlayerRpgData>` chain,
seeded empty here (`StoreMigrations.start()`, current version **v1**). Every later work package that
adds a persisted field bumps this version by **exactly one** and appends a matching `StoreMigrations`
step that upgrades an old record on load. **One owner per bump — no exceptions.**

| dataVersion | Fields | Owner WP | Migration rule |
| --- | --- | --- | --- |
| **v1** | `skillLevels`, `skillXp`, `currentMana`, `maxManaCache`, `lastActiveSkillId`, `questProgress`, `questCompleted`, `lastDailyReset`, `disabledAbilities` | WP-F5 (today) | baseline; no migration |
| ~~v2~~ | quest progress **relocated** to RamCore's ObjectiveProgressStore (removed from the profile) | WP-1.2a | no migration — in-house / fresh servers; fields simply removed |
| **v3** | `activeBuffs` | WP-4.1b | absent buffs default to empty, never null |
| **v4** | `perkRanks`, `perkPointsSpent` | WP-5.1a | absent ranks default to empty, never null |

## Rules for the owner of a bump

1. Add the field(s) to `PlayerRpgData` **and** extend `PlayerRpgData.snapshot()` to copy them (the
   snapshot is what the async writer serialises; a field left out of `snapshot()` is silently dropped
   on every save).
2. Add one `StoreMigrations` step `.to(<version>) { record -> ... }` in `RamRPG.load()`'s chain that
   upgrades a record from the previous version. Migrations run on load and the upgraded record is
   written back.
3. Migration must be **idempotent** and must **never drop** existing data. Absent collections default
   to empty, never null.
4. Ship a test that loads a checked-in old-version fixture and asserts the upgrade
   (`QuestProgressMigrationTest`, `BuffPersistenceRoundTripTest`, `PerkPersistenceMigrationTest`).

This is the player-profile ledger only. Item instances (`ItemInstanceData`) have a separate schema
version, `ItemSchema.CURRENT`, bumped once in Phase 2 by WP-2.1b — do not conflate the two.
