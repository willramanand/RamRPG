/**
 * Superseded by [dev.willram.ramrpg.core.commands.RpgCommand] as of WP-3.0; `/skills` is now an alias
 * registered by [dev.willram.ramrpg.core.commands.RpgCommand] (see its KDoc for the alias tree and
 * [dev.willram.ramrpg.core.commands.CommandAliasMap] for the full old->new path mapping). This class is
 * retained only so `RamRPG.kt`'s bootstrap construction and `register()` call stay valid without editing
 * `RamRPG.kt` during the one-release deprecation window; a later cleanup WP removes it.
 */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramrpg.api.abilities.AbilityRegistry
import dev.willram.ramrpg.api.abilities.AbilityService
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.quests.QuestRegistry
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.services.QuestService
import dev.willram.ramrpg.core.storage.PlayerStore
import io.papermc.paper.command.brigadier.Commands

@Suppress("UnstableApiUsage", "unused")
class SkillsCommand(
    private val skills: SkillRegistry,
    private val skillService: SkillService,
    private val stats: StatService,
    private val enchants: EnchantmentRegistry,
    private val items: ItemInstanceService,
    private val itemDefs: ItemDefinitionRegistry,
    private val reforges: ReforgeRegistry,
    private val gems: GemRegistry,
    private val playerStore: PlayerStore,
    private val abilities: AbilityRegistry,
    private val abilityService: AbilityService,
    private val quests: QuestService,
    private val questRegistry: QuestRegistry,
    private val reloadContent: () -> Unit,
) {

    /**
     * No-op as of WP-3.0: [dev.willram.ramrpg.core.commands.RpgCommand] registers the `/skills` alias
     * directly through its own `register(Commands)`, called from the SAME `ContentModule.setup()`
     * lifecycle hook. `RamRPG.kt` still calls this method (it is not editable during this WP), so it
     * must stay a valid, harmless no-op rather than being deleted.
     */
    fun register(commands: Commands) {
        // Intentionally empty -- see class KDoc.
    }
}
