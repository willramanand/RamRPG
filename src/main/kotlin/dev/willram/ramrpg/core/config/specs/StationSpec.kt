/**
 * WP-3.1a: PURE spec for a `stations/` content entry. Deliberately Bukkit-free -- like [ItemSpec] it
 * holds RAW strings for anything Bukkit ([blockMaterials]) and RAW menu sizing ([rows]), and leaves the
 * server-side resolution to WP-3.1b's registrar (out of this WP's file scope):
 *
 *  - [blockMaterials] (raw material names) -> a [dev.willram.ramrpg.api.effects.BlockMatcher] via
 *    [dev.willram.ramrpg.api.effects.BlockMatchers].ofMaterials(...), with the "unknown material" error
 *    raised there (exactly the [ItemSpec] `material` seam).
 *  - [rows] + [displayName] -> a RamCore `dev.willram.ramcore.menu.MenuView` via `Menus.menu(name, rows)`.
 *
 * So there is intentionally NO `toDefinition()` here (a live
 * [dev.willram.ramrpg.api.crafting.Station] needs a server to build its menu/matcher) -- mirroring
 * [ItemSpec], which also has none, rather than [GemSpec]/[ReforgeSpec], which are fully pure. The one
 * fully-pure field, [permittedKinds], IS parsed to its typed enum here.
 *
 * No [EffectSpec.Registries] parameter: a station references no open-ended action/condition/matcher ids
 * (unlike items/enchants), so nothing needs registry resolution at parse time.
 *
 * HOCON shape (namespaced ids MUST be quoted -- ':' is a HOCON separator):
 * ```
 * id = "ramrpg:smithing"
 * name = "<gold>Smithing Table"
 * blocks = ["SMITHING_TABLE"]                 # raw material names; registrar -> BlockMatcher
 * rows = 5                                     # chest rows; registrar -> MenuView
 * permitted-kinds = ["UPGRADE_INPUT", "REPAIR", "REFORGE", "INSERT_GEM", "ADD_SOCKET", "REMOVE_GEM"]
 * ```
 */
package dev.willram.ramrpg.core.config.specs

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.crafting.RecipeOutcomeKind
import dev.willram.ramrpg.api.crafting.StationKey
import dev.willram.ramrpg.core.config.RpgContentSpec
import dev.willram.ramrpg.core.config.SpecNodes
import net.kyori.adventure.text.Component
import org.spongepowered.configurate.ConfigurationNode

data class StationSpec(
    val key: StationKey,
    val displayName: Component,
    /** Raw material names; the registrar resolves these to a BlockMatcher (kept out of this pure spec). */
    val blockMaterials: List<String>,
    /** Chest rows the registrar builds a RamCore MenuView from; defaults to [DEFAULT_ROWS]. */
    val rows: Int,
    /** The [RecipeOutcomeKind]s this station accepts (fully pure -- parsed to the typed enum here). */
    val permittedKinds: Set<RecipeOutcomeKind>,
) : RpgContentSpec {
    override val id: ContentId get() = key.id

    companion object {
        private const val DEFAULT_ROWS = 3

        fun deserialize(node: ConfigurationNode): StationSpec {
            val id = SpecNodes.requireId(node)
            return StationSpec(
                key = StationKey(id),
                displayName = SpecNodes.componentOr(node, "name", Component.text(id.value())),
                blockMaterials = SpecNodes.stringList(node, "blocks").map { it.uppercase() },
                rows = SpecNodes.intOr(node, "rows", DEFAULT_ROWS),
                permittedKinds = SpecNodes.enumList<RecipeOutcomeKind>(node, "permitted-kinds", "recipe outcome kind").toSet(),
            )
        }
    }
}
