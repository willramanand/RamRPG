/**
 * WP-1.5a: registers the pure specs from an [RpgContentLoadResult] into the live RPG registries.
 *
 * This is the ONE place in the content pipeline that touches Bukkit: an [ItemSpec] holds its material
 * as a String (to keep the spec off-server-testable), so the String -> [Material] resolution -- and the
 * "unknown material" error it can raise -- happens here, on the server. Every other spec builds a pure
 * domain object via its own `toX()` and is registered directly.
 *
 * Registration is NOT thread-safe against the Bukkit main thread on its own; callers load off-thread
 * and then invoke [registerAll] on the right context (see [dev.willram.ramrpg.core.modules.ContentModule]).
 *
 * Errors AGGREGATE: [registerAll] never throws on a bad material -- it collects a [ValidationError] and
 * moves on, so one typo does not sink the rest of a content pack. (Load-time errors are already carried
 * by the [RpgContentLoadResult]; these are the registration-time ones the loader cannot detect without
 * Bukkit.)
 */
package dev.willram.ramrpg.core.config

import dev.willram.ramcore.exception.ValidationError
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.config.specs.ItemSpec
import org.bukkit.Material

class ContentRegistrarRpg(
    private val stats: StatService,
    private val items: ItemDefinitionRegistry,
    private val skills: SkillRegistry,
    private val enchants: EnchantmentRegistry,
    private val entities: EntityProfileRegistry,
    private val reforges: ReforgeRegistry,
    private val gems: GemRegistry,
) {

    /**
     * Registers every loaded spec under [owner], returning any registration-time errors (currently
     * only unknown materials). Note [StatService.registerDefinition] takes no owner, so HOCON stat
     * definitions cannot be scoped/unregistered by owner -- matching the existing StatService contract.
     */
    fun registerAll(result: RpgContentLoadResult, owner: String): List<ValidationError> {
        val errors = ArrayList<ValidationError>()

        result.stats.forEach { stats.registerDefinition(it.toDefinition()) }
        result.skills.forEach { skills.register(owner, it.toDefinition()) }
        result.enchants.forEach { enchants.register(owner, it.toEnchantment()) }
        result.entities.forEach { entities.register(owner, it.toProfile()) }
        result.reforges.forEach { reforges.register(owner, it.toDefinition()) }
        result.gems.forEach { gems.register(owner, it.toDefinition()) }

        result.items.forEach { spec ->
            val material = runCatching { Material.valueOf(spec.material) }.getOrNull()
            if (material == null) {
                // Cite the file + path this item came from (the loader kept its SourceRef) so this
                // registration-time error keeps the "every error carries file and path" promise;
                // fall back to the id only if the source is somehow unknown.
                val src = result.sourceOf(spec.id)
                errors += if (src != null) {
                    ValidationError.at(src.file(), src.path().ifEmpty { spec.id.toString() }, "unknown material '${spec.material}'")
                } else {
                    ValidationError.at(spec.id.toString(), spec.material, "unknown material '${spec.material}'")
                }
            } else {
                items.register(owner, buildItem(spec, material))
            }
        }
        return errors
    }

    /** Turns a pure [ItemSpec] into an [ItemDefinition] once its [Material] has resolved. */
    private fun buildItem(spec: ItemSpec, material: Material): ItemDefinition {
        val baseStats = spec.baseStats.map { (statKey, amount) ->
            StatModifier(statKey, amount, ModifierOperation.ADD, ModifierSource(SourceType.ITEM, spec.id))
        }
        return ItemDefinition(
            key = spec.key,
            displayName = spec.displayName,
            material = material,
            rarity = spec.rarity,
            categories = spec.categories,
            baseStats = baseStats,
            // effects = emptyList() (WP-1.5b seam) and loreTemplate = DEFAULT are the constructor defaults.
            maxStack = spec.maxStack,
            customModelData = spec.customModelData,
            allowVanillaWrapper = spec.allowVanillaWrapper,
            description = spec.description,
            statRolls = spec.statRolls,
        )
    }
}
