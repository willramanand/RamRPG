package dev.willram.ramrpg

import dev.willram.ramcore.terminable.TerminableConsumer
import dev.willram.ramcore.terminable.module.TerminableModule
import dev.willram.ramcore.testkit.TestServiceContext
import dev.willram.ramrpg.core.modules.BossModule
import dev.willram.ramrpg.core.modules.BuffModule
import dev.willram.ramrpg.core.modules.ContentModule
import dev.willram.ramrpg.core.modules.CraftingModule
import dev.willram.ramrpg.core.modules.DungeonModule
import dev.willram.ramrpg.core.modules.MobModule
import dev.willram.ramrpg.core.modules.PerkModule
import dev.willram.ramrpg.core.modules.RpgModules
import dev.willram.ramrpg.core.modules.ScheduleModule
import dev.willram.ramrpg.core.modules.SetModule
import dev.willram.ramrpg.core.modules.VendorModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-1.7b: [dev.willram.ramrpg.RamRPG.enable] binds exactly `RpgModules.all(this)` -- one
 * `bindModule` per element. This asserts that list is the declared 16-module seam set (six populated,
 * ten seeded empty), with no missing, extra or duplicate module, so "every declared module is bound
 * exactly once". Off-server: only module *construction* runs (constructors just store the context);
 * the empty seams' `setup` is exercised to prove they register nothing yet, while the populated seams'
 * `setup` (which touches Bukkit) is not invoked.
 */
class ModuleSeamTest {

    private val expectedPopulated = setOf(
        "LootModule", "QuestModule", "RewardModule", "CombatModule", "EconomyModule", "UiModule",
    )

    private val expectedEmpty = setOf(
        "ContentModule", "CraftingModule", "SetModule", "BuffModule", "PerkModule",
        "MobModule", "BossModule", "VendorModule", "DungeonModule", "ScheduleModule",
    )

    @Test
    fun `the module set is exactly the declared reserved list, each present once`() {
        val modules = RpgModules.all(TestServiceContext.withRegistry())
        val names = modules.map { it::class.simpleName }

        assertEquals(16, modules.size, "expected 6 populated + 10 seeded-empty module seams")
        assertEquals(names.size, names.toSet().size, "no module may be bound (listed) twice")
        assertEquals(expectedPopulated + expectedEmpty, names.toSet(), "module set must equal the reserved list")
    }

    @Test
    fun `the split is six populated and ten seeded-empty seams`() {
        val names = RpgModules.all(TestServiceContext.withRegistry()).map { it::class.simpleName }.toSet()
        assertEquals(expectedPopulated, names intersect expectedPopulated)
        assertEquals(expectedEmpty, names intersect expectedEmpty)
        assertEquals(6, expectedPopulated.size)
        assertEquals(10, expectedEmpty.size)
    }

    @Test
    fun `seeded-empty modules bind nothing`() {
        val ctx = TestServiceContext.withRegistry()
        val emptyModules: List<TerminableModule> = listOf(
            ContentModule(ctx), CraftingModule(ctx), SetModule(ctx), BuffModule(ctx), PerkModule(ctx),
            MobModule(ctx), BossModule(ctx), VendorModule(ctx), DungeonModule(ctx), ScheduleModule(ctx),
        )
        for (module in emptyModules) {
            val recorder = RecordingConsumer()
            module.setup(recorder)
            assertTrue(
                recorder.bound.isEmpty(),
                "${module::class.simpleName} is a seeded-empty seam and must register nothing yet",
            )
        }
    }

    /** A [TerminableConsumer] that records everything bound to it, for asserting empty seams are inert. */
    private class RecordingConsumer : TerminableConsumer {
        val bound = mutableListOf<AutoCloseable>()
        override fun <T : AutoCloseable> bind(terminable: T): T {
            bound.add(terminable)
            return terminable
        }
    }
}
