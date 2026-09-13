# RamRPG — Manual Smoke Test

Everything testable off-server is covered by unit tests (`FakeScheduler`/`FakeClock`/`ProxyFakes`/
`@TempDir`, no live server). This checklist is the **live-server** validation those cannot provide, and
it is the milestone-tag gate: at every `v2.x` tag, walk the scenarios below on **Paper** and **Folia**
(26.1) with RamCore installed (merge-checklist item 11, DoD item 3).

**This file is append-only.** Every later work package whose feature appears in DoD item 3 adds its own
scenario row here as part of its commit — the table grows one row per shipped player-facing system, so
by v3.0.0 it walks the full eleven-step player journey.

## Prerequisites

1. Build artifacts (JDK 25 toolchain; `JAVA_HOME` at a JDK 25 or let Gradle provision it):
   ```
   ./gradlew build
   # RamRPG plugin:  build/libs/RamRPG-<version>.jar
   ```
2. A matching RamCore build (`RamCore-2.0.0.jar` from `../RamCore`, `./gradlew :ramcore-paper:shadowJar`)
   and ProtocolLib on each server. Vault and MythicMobs are optional.
3. Two test servers on 26.1: one **Paper**, one **Folia**.
4. On each: drop `RamCore-<version>.jar`, `ProtocolLib`, and `RamRPG-<version>.jar` into `plugins/`.
   RamRPG declares RamCore and ProtocolLib as required `load: BEFORE` dependencies.

## Scenarios

Run each in-game as an op. `[P]` = Paper expectation, `[F]` = Folia expectation.

| # | DoD scenario | Command / trigger | Expect (P) | Expect (F) | Result |
| - | --- | --- | --- | --- | --- |
| 1 | Player data persists across join/quit (F5) | join, gain XP, quit, rejoin | Level/XP/skills restored from `PlayerDataService` store; no data loss; migration ledger applied on read | same; store I/O and the join handler run without a region-thread error | ☐ |
| 2 | Stats GUI renders every stat | `/skills` or `/rpg stats` | GUI opens; all 26 stats shown with values from `StatService`; closing does not drop items | same; GUI open/click handled on the player thread | ☐ |
| 3 | Packet lore renders on an RPG item | hold/inspect an RPG item | Lore shows rarity, stats, and any cooldown line through `PacketItemRenderer`; other viewers see per-locale lore; vanilla item unchanged | same; packet send anchored to the viewer's context | ☐ |
| 4 | Ability cast + cooldown | cast a bound ability | Ability fires; cooldown starts and is shown; recast before expiry is refused; insufficient resource is refused | same; cast/effect run on the caster/target region thread with no thread error | ☐ |
| 5 | Mob stats scale by spawn location | spawn/kill a mob in a level band | Mob HP/damage scale with the resolved band; loot/XP scale too | same; band resolved once at spawn, cached on the entity | ☐ |
| 6 | Loot drops with rolled stats | kill an RPG mob | Drops resolve from the RamCore `LootTable`; item carries rolled stats and a stable seed | same; drop grant runs on the killer/entity context | ☐ |
| 7 | Skills tree GUI | `/skills` tree view | Paginated skill tree opens through the RamCore menu; navigation works; no legacy `menu.Gui` | same; menu handled on the player thread | ☐ |
| 8 | Requirement-gated equipment goes inert below level (WP-2.1c) | equip an item whose `requirements` you don't meet | Item equips but contributes no stats (equipment/enchant/reforge/socket) and its lore shows the unmet requirement(s) in red plus an inert banner; meeting the requirement later (e.g. leveling the skill) and re-equipping restores full stats | same; the equipment-change stat refresh runs on the player's region thread | ☐ |
| 9 | Durability drains, goes inert at zero, never breaks (WP-2.2) | use an RPG weapon until it hits 0 durability | Weapon's durability lore counts down on each hit; at 0 it goes inert (greys out / red durability line + inert banner, contributes no stats) but stays in your hand — it never breaks or disappears, and vanilla's own durability/break behaviour never applies to it | same; the drain runs in the pipeline on the attacker's/victim's own region thread | ☐ |

## Cross-cutting checks

- **Content reload:** `/rpg validate` prints `file:path: message` per error and mutates nothing;
  `/rpg reload` applies a diff, re-registers content, and refreshes lore/stats without orphaning
  live objects. Both are permission-guarded.
- **No thread errors:** on Folia, watch the console for `IllegalStateException`/region-ownership
  errors during any scenario — there should be none.
- **Shutdown:** stop the server cleanly; confirm no leaked display entities, boss bars, or tasks and
  no async-access warnings on disable.

## Recording

Fill the Result column per server. Any failure → open an issue with the scenario #, server type, and
console excerpt; keep the affected system unreleased until it passes on both Paper and Folia.
