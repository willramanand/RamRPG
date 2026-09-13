/**
 * WP-1.7b: the single source of truth for the RamRPG module seams. [dev.willram.ramrpg.RamRPG.enable]
 * binds exactly this list (one `bindModule` per element), and [ModuleSeamTest] asserts the set is the
 * declared 16 -- six seams populated now, ten seeded empty for one named future WP each -- with no
 * missing, extra, or duplicate module. New subsystems get a seam here (and only here) as their WP
 * lands; the rule is "no seam without a WP that uses it".
 *
 * Every module takes the plugin's [ServiceContext] and resolves its collaborators from it, so no
 * module reaches for the deprecated `RamRPG.get()` singleton.
 */
package dev.willram.ramrpg.core.modules

import dev.willram.ramcore.service.ServiceContext
import dev.willram.ramcore.terminable.module.TerminableModule

object RpgModules {

    /** All subsystem modules, in bind order. Non-empty first, then the seeded-empty seams. */
    fun all(ctx: ServiceContext): List<TerminableModule> = listOf(
        // Six seams populated by WP-1.7b (migrating existing registrations).
        LootModule(ctx),
        QuestModule(ctx),
        RewardModule(ctx),
        CombatModule(ctx),
        EconomyModule(ctx),
        UiModule(ctx),
        // Ten seams seeded empty, each reserved for one named later WP.
        ContentModule(ctx),   // WP-1.5a
        CraftingModule(ctx),  // WP-3.1b
        SetModule(ctx),       // WP-5.3
        BuffModule(ctx),      // WP-4.1a
        PerkModule(ctx),      // WP-5.1a
        MobModule(ctx),       // WP-6.2
        BossModule(ctx),      // WP-6.4a
        VendorModule(ctx),    // WP-7.1
        DungeonModule(ctx),   // WP-7.2
        ScheduleModule(ctx),  // WP-7.3
    )
}
