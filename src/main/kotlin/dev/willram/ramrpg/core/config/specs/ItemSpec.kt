/**
 * WP-1.5a: PURE spec for an `items/` content entry. Deliberately holds `material` as a raw String:
 * [dev.willram.ramrpg.api.items.ItemDefinition] carries an `org.bukkit.Material`, so keeping the spec
 * Bukkit-free means the String -> Material resolution (and its "unknown material" error) happens in
 * [dev.willram.ramrpg.core.config.ContentRegistrarRpg] on the server, NOT here. `base-stats` are held
 * as raw amounts; the registrar attaches the `ModifierSource` (this item) when it builds the
 * StatModifier list.
 *
 * SEAM (WP-1.5b, now filled): items carry an `effects = [...]` bundle, parsed by [EffectSpec] into
 * concrete [Effect]s and held on [effects]. Deserialization takes an optional [EffectSpec.Registries]:
 * absent (the legacy `RpgContentLoader.load(root)` path) -> no effects; present (the ContentModule path
 * with builtins registered) -> the bundle resolves. Surfacing these onto the live
 * [dev.willram.ramrpg.api.items.ItemDefinition.effects] belongs to [ContentRegistrarRpg] (out of this
 * WP's file scope); the spec captures them so that later wiring is a one-line registrar change.
 *
 * WP-2.1a (now filled): items may also carry `item-level`, `requirements = [...]` and `equip-slots`.
 * Same seam shape as effects above: this spec parses and holds them (see [itemLevel], [requirements],
 * [equipSlots]), but wiring them onto the live [dev.willram.ramrpg.api.items.ItemDefinition] fields of
 * the same name is [ContentRegistrarRpg]'s job (out of THIS WP's file scope -- WP-2.1a owns only the
 * `ItemDefinition` fields existing, the pure parsing here, and the Kotlin builtins; WP-1.5d/ContentRegistrarRpg
 * wiring is separate). [equipSlots] is `null` when the HOCON omits `equip-slots`, meaning "no override" --
 * the eventual registrar call (or a direct `ItemDefinition(...)` construction) should omit the
 * constructor argument so `ItemDefinition`'s own category-based default applies, rather than passing an
 * empty set.
 *
 * HOCON shape (namespaced ids/keys MUST be quoted -- ':' is a HOCON separator):
 * ```
 * id = "ramrpg:test_sword"
 * name = "<red>Test Sword"
 * material = "DIAMOND_SWORD"
 * rarity = "RARE"                       # COMMON..MYTHIC; defaults COMMON
 * categories = ["SWORD"]
 * base-stats { "ramrpg:damage" = 50.0, "ramrpg:strength" = 20.0 }
 * description = ["<gray>A test blade."]
 * stat-rolls = [ { stat = "ramrpg:crit_chance", min = 0.0, max = 10.0 } ]
 * max-stack = 1
 * custom-model-data = 1001
 * allow-vanilla-wrapper = false
 * item-level = 15
 * requirements = [
 *   { type = skill_level, skill = "ramrpg:combat", level = 10 }
 *   { type = stat_threshold, stat = "ramrpg:strength", min = 20.0 }
 *   { type = perk_owned, perk = "ramrpg:dragon_slayer" }   # STUB until WP-5.1a -- see ItemRequirement.PerkOwned
 * ]
 * equip-slots = ["HAND"]                # optional; omit to use the category -> slot default
 * damage-split { "ramrpg:physical" = 0.7, "ramrpg:fire" = 0.3 }   # optional; must sum to 1.0 (+/- epsilon)
 * ```
 *
 * WP-2.3b (now filled): weapons may also carry a `damage-split { <damageTypeId> = <fraction>, ... } ` map
 * -- see [damageSplit]. Same seam shape as `effects`/`requirements` above: parsed and VALIDATED here
 * (a present-but-wrong-sum split is a load-time [ContentDeserializeException], not a silently-accepted
 * definition), but copying it onto the live [dev.willram.ramrpg.api.items.ItemDefinition.damageSplit]
 * field is [ContentRegistrarRpg]'s job.
 */
package dev.willram.ramrpg.core.config.specs

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.effects.Effect
import dev.willram.ramrpg.api.identity.DamageTypeKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemRequirement
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.StatRoll
import dev.willram.ramrpg.core.config.RpgContentSpec
import dev.willram.ramrpg.core.config.SpecNodes
import net.kyori.adventure.text.Component
import org.bukkit.inventory.EquipmentSlot
import org.spongepowered.configurate.ConfigurationNode

data class ItemSpec(
    val key: ItemKey,
    val displayName: Component,
    /** Raw material name; resolved to org.bukkit.Material by the registrar (kept out of this pure spec). */
    val material: String,
    val rarity: Rarity,
    val categories: Set<ItemCategory>,
    /** stat id -> ADD amount; the registrar wraps these in StatModifiers sourced to this item. */
    val baseStats: Map<StatKey, Double>,
    val description: List<Component>,
    val statRolls: List<StatRoll>,
    val maxStack: Int?,
    val customModelData: Int?,
    val allowVanillaWrapper: Boolean,
    /** WP-1.5b: the item's parsed effect bundle (empty when loaded without effect registries). */
    val effects: List<Effect> = emptyList(),
    /** WP-2.1a: planning/lore level; defaults to 1 (starter tier) when `item-level` is absent. */
    val itemLevel: Int = 1,
    /** WP-2.1a: parsed `requirements = [...]` gates; empty when absent. */
    val requirements: List<ItemRequirement> = emptyList(),
    /** WP-2.1a: explicit `equip-slots` override, or null when absent (defer to the category default). */
    val equipSlots: Set<EquipmentSlot>? = null,
    /**
     * WP-2.3b: parsed `damage-split = { <damageTypeId> = <fraction>, ... }`; empty when absent (the
     * `ElementalBreakdownStage` all-physical default). [deserialize] validates a non-empty map sums to
     * `1.0` within [DAMAGE_SPLIT_EPSILON] before this spec is ever constructed.
     */
    val damageSplit: Map<DamageTypeKey, Double> = emptyMap(),
) : RpgContentSpec {
    override val id: ContentId get() = key.id

    companion object {
        fun deserialize(node: ConfigurationNode, effects: EffectSpec.Registries? = null): ItemSpec {
            val id = SpecNodes.requireId(node)
            return ItemSpec(
                key = ItemKey(id),
                displayName = SpecNodes.componentOr(node, "name", Component.text(id.value())),
                material = SpecNodes.requiredString(node, "material").uppercase(),
                rarity = SpecNodes.enumOr(node, "rarity", Rarity.COMMON, "rarity"),
                categories = SpecNodes.enumList<ItemCategory>(node, "categories", "item category").toSet(),
                baseStats = SpecNodes.statAmounts(node, "base-stats"),
                description = SpecNodes.componentList(node, "description"),
                statRolls = statRolls(node),
                maxStack = SpecNodes.intOrNull(node, "max-stack"),
                customModelData = SpecNodes.intOrNull(node, "custom-model-data"),
                allowVanillaWrapper = SpecNodes.boolOr(node, "allow-vanilla-wrapper", false),
                effects = EffectSpec.bundle(node, "effects", id, effects),
                itemLevel = SpecNodes.intOr(node, "item-level", 1),
                requirements = requirements(node),
                equipSlots = equipSlots(node),
                damageSplit = damageSplit(node, id),
            )
        }

        private fun statRolls(node: ConfigurationNode): List<StatRoll> =
            node.node("stat-rolls").childrenList().map { roll ->
                val min = SpecNodes.doubleOr(roll, "min", 0.0)
                val max = SpecNodes.doubleOr(roll, "max", min)
                if (min > max) throw ContentDeserializeException("stat-roll min $min is greater than max $max")
                StatRoll(StatKey(SpecNodes.requireIdAt(roll, "stat")), min, max)
            }

        /**
         * Parses `requirements = [...]`. Each entry's `type` is a closed grammar owned by
         * [ItemRequirement] (mirrors [EffectSpec.one]'s `type` dispatch): `skill_level`, `stat_threshold`
         * or `perk_owned`. An unrecognised type throws [ContentDeserializeException] naming it, so
         * [dev.willram.ramcore.content.SpecLoader] tags it with the offending file + path.
         */
        private fun requirements(node: ConfigurationNode): List<ItemRequirement> =
            node.node("requirements").childrenList().map { req ->
                when (val type = SpecNodes.requiredString(req, "type", "item requirement type").lowercase()) {
                    "skill_level" -> ItemRequirement.SkillLevel(
                        skill = SkillKey(SpecNodes.requireIdAt(req, "skill")),
                        level = SpecNodes.intOr(req, "level", 1),
                    )
                    "stat_threshold" -> ItemRequirement.StatThreshold(
                        stat = StatKey(SpecNodes.requireIdAt(req, "stat")),
                        min = SpecNodes.doubleOr(req, "min", 0.0),
                    )
                    // STUB until WP-5.1a: PerkKey/PerkService do not exist yet, so this carries the raw
                    // ContentId a pack author names -- see ItemRequirement.PerkOwned's KDoc.
                    "perk_owned" -> ItemRequirement.PerkOwned(perk = SpecNodes.requireIdAt(req, "perk"))
                    else -> throw ContentDeserializeException(
                        "unknown item requirement type '$type'; expected skill_level, stat_threshold or perk_owned",
                    )
                }
            }

        /** `equip-slots` is absent by default (null = "use the ItemDefinition category default"). */
        private fun equipSlots(node: ConfigurationNode): Set<EquipmentSlot>? {
            if (node.node("equip-slots").virtual()) return null
            return SpecNodes.enumList<EquipmentSlot>(node, "equip-slots", "equipment slot").toSet()
        }

        /** Tolerance a `damage-split`'s fractions must sum to `1.0` within -- floating-point HOCON authoring slop. */
        private const val DAMAGE_SPLIT_EPSILON = 0.0001

        /**
         * Parses `damage-split { "<damageTypeId>" = <fraction>, ... }` -> [DamageTypeKey] -> fraction.
         * Absent -> empty map (the [ElementalBreakdownStage][dev.willram.ramrpg.builtin.stats.ElementalBreakdownStage]
         * all-physical default). Present -> every fraction is read, then the map MUST sum to `1.0` within
         * [DAMAGE_SPLIT_EPSILON] or this throws [ContentDeserializeException] naming [id] and the actual sum,
         * so [dev.willram.ramcore.content.SpecLoader] tags it with the offending file + path (the same way
         * [statRolls]' `min > max` check does for stat rolls).
         */
        private fun damageSplit(node: ConfigurationNode, id: ContentId): Map<DamageTypeKey, Double> {
            val splitNode = node.node("damage-split")
            if (splitNode.virtual()) return emptyMap()
            val out = LinkedHashMap<DamageTypeKey, Double>()
            splitNode.childrenMap().forEach { (rawKey, value) ->
                out[DamageTypeKey(SpecNodes.parseId(rawKey.toString(), "damage-split key"))] = value.getDouble()
            }
            val total = out.values.sum()
            if (kotlin.math.abs(total - 1.0) > DAMAGE_SPLIT_EPSILON) {
                throw ContentDeserializeException(
                    "damage-split for '$id' must sum to 1.0 but summed to $total",
                )
            }
            return out
        }
    }
}
