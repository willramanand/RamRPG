/**
 * WP-1.5d: loader-backed shim over the packaged `content/items/builtin.conf` resource (authored
 * alongside this file at `src/main/resources/content/items/builtin.conf`; see
 * docs/design/1.5d-builtin-content.md for the full parity guarantee and first-run extraction story).
 *
 * [registerAll] stays the programmatic entry point (rule 7 -- no api break, `RamRPG.registerBuiltins()`
 * keeps calling it unchanged): it loads the packaged HOCON via [RpgContentLoader.loadPackaged] and turns
 * each [dev.willram.ramrpg.core.config.specs.ItemSpec] into an [ItemDefinition] through the EXACT SAME
 * [ContentRegistrarRpg.buildItem] mapping the live `content/` pipeline uses, so this path and the
 * operator-editable HOCON path are structurally guaranteed to agree -- see `BuiltinItemsParityTest`,
 * which asserts this against a frozen snapshot of the old hand-written Kotlin registry.
 *
 * The load is a small, bounded classpath read + single-file HOCON parse (65 entries) done via a
 * throwaway temp directory (RamCore's [dev.willram.ramcore.content.ContentLoader] only reads real
 * filesystem directories). It runs synchronously from `RamRPG.registerBuiltins()`, the exact same
 * enable()-time bootstrap point the old in-memory Kotlin construction ran from -- see the WP's Folia
 * rule 4 carve-out ("at load()/enable() bootstrap where the existing content load already runs"). This
 * does NOT touch or require an operator's data folder; copying the packaged resource into `content/` so
 * an operator can edit it is [RpgContentLoader.extractPackagedContent]'s job, wired into the real
 * `content/` pipeline ([dev.willram.ramrpg.core.modules.ContentModule], outside this WP's file scope).
 */
package dev.willram.ramrpg.builtin.items

import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.core.config.ContentRegistrarRpg
import dev.willram.ramrpg.core.config.RpgContentLoader
import org.bukkit.Material

object BuiltinItems {
    private const val OWNER = "ramrpg-builtin"

    fun registerAll(reg: ItemDefinitionRegistry) {
        val result = RpgContentLoader.loadPackaged()
        check(result.successful()) {
            "BuiltinItems: packaged content/items/builtin.conf failed to load: " +
                result.errors().joinToString("; ") { it.describe() }
        }
        for (spec in result.items) {
            // Curated, packaged content: an unresolvable material here is a packaging bug, not an
            // authoring mistake to aggregate and report (contrast ContentRegistrarRpg.registerAll,
            // which DOES aggregate -- that path takes arbitrary operator-authored content).
            val material = Material.valueOf(spec.material)
            reg.register(OWNER, ContentRegistrarRpg.buildItem(spec, material))
        }
    }
}
