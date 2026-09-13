package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemRequirement
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.StatRoll
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.builtin.identity.RamSkills
import dev.willram.ramrpg.builtin.identity.RamStats
import dev.willram.ramrpg.builtin.items.BuiltinItems
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-1.5d: proves the HOCON-loaded builtin item registry --
 * `content/items/builtin.conf` loaded through `BuiltinItems.registerAll` ->
 * `RpgContentLoader.loadPackaged` -> `ContentRegistrarRpg.buildItem` -- is FIELD-FOR-FIELD identical to
 * the hand-written Kotlin registry `BuiltinItems.kt` carried through WP-2.1a (before this WP rewrote it
 * into a loader-backed shim).
 *
 * [LegacySnapshot] is a frozen copy of that pre-WP-1.5d Kotlin table (the `IDef` list + the four
 * `CUSTOM` items + the exact same `itemLevelFor`/`combatLevelFor`/`requirementsFor`/`weaponRolls`/
 * `armorRolls` derivations `registerAll` used to run) kept ONLY as this test's independent ground truth.
 * It is deliberately NOT derived from any code this WP touches, so a regression in `builtin.conf` or in
 * the loader/registrar wiring fails here instead of silently drifting.
 */
class BuiltinItemsParityTest {

    @Test
    fun `HOCON-loaded builtin items equal the frozen Kotlin snapshot field-for-field`() {
        val legacy = LegacySnapshot.buildAll()
        assertEquals(65, legacy.size, "sanity: 61 vanilla-wrapper DEFS + 4 CUSTOM items")

        val actual = FakeItemRegistry()
        BuiltinItems.registerAll(actual)

        assertEquals(legacy.keys, actual.all().map { it.key }.toSet(), "same set of item keys")

        for ((key, expected) in legacy) {
            val def = actual.get(key) ?: error("missing from HOCON-loaded registry: $key")
            assertEquals(expected.key, def.key, "key mismatch for $key")
            assertEquals(expected.displayName, def.displayName, "displayName mismatch for $key")
            assertEquals(expected.material, def.material, "material mismatch for $key")
            assertEquals(expected.rarity, def.rarity, "rarity mismatch for $key")
            assertEquals(expected.categories, def.categories, "categories mismatch for $key")
            assertEquals(expected.baseStats.toSet(), def.baseStats.toSet(), "baseStats mismatch for $key")
            assertEquals(expected.effects, def.effects, "effects mismatch for $key")
            assertEquals(expected.maxStack, def.maxStack, "maxStack mismatch for $key")
            assertEquals(expected.customModelData, def.customModelData, "customModelData mismatch for $key")
            assertEquals(expected.allowVanillaWrapper, def.allowVanillaWrapper, "allowVanillaWrapper mismatch for $key")
            assertEquals(expected.description, def.description, "description mismatch for $key")
            assertEquals(expected.statRolls.toSet(), def.statRolls.toSet(), "statRolls mismatch for $key")
            assertEquals(expected.itemLevel, def.itemLevel, "itemLevel mismatch for $key")
            assertEquals(expected.requirements.toSet(), def.requirements.toSet(), "requirements mismatch for $key")
            assertEquals(expected.equipSlots, def.equipSlots, "equipSlots mismatch for $key")
        }
    }

    @Test
    fun `packaged content loads with no errors and every entry resolves its material`() {
        val result = dev.willram.ramrpg.core.config.RpgContentLoader.loadPackaged()
        assertTrue(result.errors().isEmpty(), "no load errors: ${result.errors()}")
        assertEquals(65, result.items.size)
        for (spec in result.items) {
            // Throws IllegalArgumentException if the packaged material name is bad -- fail loud.
            Material.valueOf(spec.material)
        }
    }

    private class FakeItemRegistry : ItemDefinitionRegistry {
        private val map = LinkedHashMap<ItemKey, ItemDefinition>()
        override fun get(key: ItemKey) = map[key]
        override fun register(owner: String, def: ItemDefinition) { map[def.key] = def }
        override fun unregisterOwner(owner: String) = 0
        override fun all() = map.values
        override fun revision() = 0
    }

    /**
     * Frozen copy of `BuiltinItems.kt` as it stood at the end of WP-2.1a. Do not "fix" this to match a
     * future BuiltinItems.kt change -- its entire purpose is to stay independent so this test keeps
     * proving parity rather than tautology.
     */
    private object LegacySnapshot {
        private fun ik(v: String) = ItemKey.of("ramrpg", v)

        private fun stat(stat: StatKey, amount: Double, refId: String): StatModifier =
            StatModifier(stat, amount, ModifierOperation.ADD, ModifierSource(SourceType.ITEM, ContentId.of("ramrpg", refId)))

        private data class IDef(
            val id: String,
            val name: String,
            val mat: Material,
            val cats: Set<ItemCategory>,
            val rarity: Rarity,
            val stats: List<Pair<StatKey, Double>>,
            val rolls: List<StatRoll> = emptyList(),
        )

        private fun weaponRolls() = listOf(
            StatRoll(RamStats.STRENGTH, 1.0, 6.0),
            StatRoll(RamStats.CRIT_CHANCE, 0.0, 5.0),
        )
        private fun armorRolls() = listOf(
            StatRoll(RamStats.HEALTH, 2.0, 10.0),
            StatRoll(RamStats.DEFENSE, 1.0, 6.0),
        )

        private val DEFS = listOf(
            // Swords
            IDef("wooden_sword", "Wooden Sword", Material.WOODEN_SWORD, setOf(ItemCategory.SWORD), Rarity.COMMON, listOf(RamStats.DAMAGE to 2.75)),
            IDef("stone_sword", "Stone Sword", Material.STONE_SWORD, setOf(ItemCategory.SWORD), Rarity.COMMON, listOf(RamStats.DAMAGE to 3.0)),
            IDef("iron_sword", "Iron Sword", Material.IRON_SWORD, setOf(ItemCategory.SWORD), Rarity.COMMON, listOf(RamStats.DAMAGE to 4.5)),
            IDef("golden_sword", "Golden Sword", Material.GOLDEN_SWORD, setOf(ItemCategory.SWORD), Rarity.COMMON, listOf(RamStats.DAMAGE to 2.5)),
            IDef("diamond_sword", "Diamond Sword", Material.DIAMOND_SWORD, setOf(ItemCategory.SWORD), Rarity.UNCOMMON, listOf(RamStats.DAMAGE to 6.5)),
            IDef("netherite_sword", "Netherite Sword", Material.NETHERITE_SWORD, setOf(ItemCategory.SWORD), Rarity.RARE, listOf(RamStats.DAMAGE to 8.0)),
            // Axes
            IDef("wooden_axe", "Wooden Axe", Material.WOODEN_AXE, setOf(ItemCategory.AXE), Rarity.COMMON, listOf(RamStats.DAMAGE to 2.5)),
            IDef("stone_axe", "Stone Axe", Material.STONE_AXE, setOf(ItemCategory.AXE), Rarity.COMMON, listOf(RamStats.DAMAGE to 2.75)),
            IDef("iron_axe", "Iron Axe", Material.IRON_AXE, setOf(ItemCategory.AXE), Rarity.COMMON, listOf(RamStats.DAMAGE to 3.5)),
            IDef("golden_axe", "Golden Axe", Material.GOLDEN_AXE, setOf(ItemCategory.AXE), Rarity.COMMON, listOf(RamStats.DAMAGE to 2.0)),
            IDef("diamond_axe", "Diamond Axe", Material.DIAMOND_AXE, setOf(ItemCategory.AXE), Rarity.UNCOMMON, listOf(RamStats.DAMAGE to 6.0)),
            IDef("netherite_axe", "Netherite Axe", Material.NETHERITE_AXE, setOf(ItemCategory.AXE), Rarity.RARE, listOf(RamStats.DAMAGE to 7.5)),
            // Pickaxes
            IDef("wooden_pickaxe", "Wooden Pickaxe", Material.WOODEN_PICKAXE, setOf(ItemCategory.PICKAXE), Rarity.COMMON, listOf(RamStats.DAMAGE to 1.0)),
            IDef("stone_pickaxe", "Stone Pickaxe", Material.STONE_PICKAXE, setOf(ItemCategory.PICKAXE), Rarity.COMMON, listOf(RamStats.DAMAGE to 1.5)),
            IDef("iron_pickaxe", "Iron Pickaxe", Material.IRON_PICKAXE, setOf(ItemCategory.PICKAXE), Rarity.COMMON, listOf(RamStats.DAMAGE to 1.75)),
            IDef("golden_pickaxe", "Golden Pickaxe", Material.GOLDEN_PICKAXE, setOf(ItemCategory.PICKAXE), Rarity.COMMON, listOf(RamStats.DAMAGE to 1.5)),
            IDef("diamond_pickaxe", "Diamond Pickaxe", Material.DIAMOND_PICKAXE, setOf(ItemCategory.PICKAXE), Rarity.UNCOMMON, listOf(RamStats.DAMAGE to 1.5)),
            IDef("netherite_pickaxe", "Netherite Pickaxe", Material.NETHERITE_PICKAXE, setOf(ItemCategory.PICKAXE), Rarity.RARE, listOf(RamStats.DAMAGE to 2.5)),
            // Shovels (no damage)
            IDef("wooden_shovel", "Wooden Shovel", Material.WOODEN_SHOVEL, setOf(ItemCategory.SHOVEL), Rarity.COMMON, emptyList()),
            IDef("stone_shovel", "Stone Shovel", Material.STONE_SHOVEL, setOf(ItemCategory.SHOVEL), Rarity.COMMON, emptyList()),
            IDef("iron_shovel", "Iron Shovel", Material.IRON_SHOVEL, setOf(ItemCategory.SHOVEL), Rarity.COMMON, emptyList()),
            IDef("golden_shovel", "Golden Shovel", Material.GOLDEN_SHOVEL, setOf(ItemCategory.SHOVEL), Rarity.COMMON, emptyList()),
            IDef("diamond_shovel", "Diamond Shovel", Material.DIAMOND_SHOVEL, setOf(ItemCategory.SHOVEL), Rarity.UNCOMMON, emptyList()),
            IDef("netherite_shovel", "Netherite Shovel", Material.NETHERITE_SHOVEL, setOf(ItemCategory.SHOVEL), Rarity.RARE, emptyList()),
            // Hoes
            IDef("wooden_hoe", "Wooden Hoe", Material.WOODEN_HOE, setOf(ItemCategory.HOE), Rarity.COMMON, emptyList()),
            IDef("stone_hoe", "Stone Hoe", Material.STONE_HOE, setOf(ItemCategory.HOE), Rarity.COMMON, emptyList()),
            IDef("iron_hoe", "Iron Hoe", Material.IRON_HOE, setOf(ItemCategory.HOE), Rarity.COMMON, emptyList()),
            IDef("golden_hoe", "Golden Hoe", Material.GOLDEN_HOE, setOf(ItemCategory.HOE), Rarity.COMMON, emptyList()),
            IDef("diamond_hoe", "Diamond Hoe", Material.DIAMOND_HOE, setOf(ItemCategory.HOE), Rarity.UNCOMMON, emptyList()),
            IDef("netherite_hoe", "Netherite Hoe", Material.NETHERITE_HOE, setOf(ItemCategory.HOE), Rarity.RARE, emptyList()),
            // Bows / crossbows / trident / mace / rod
            IDef("bow", "Bow", Material.BOW, setOf(ItemCategory.BOW), Rarity.COMMON, listOf(RamStats.DAMAGE to 7.0)),
            IDef("crossbow", "Crossbow", Material.CROSSBOW, setOf(ItemCategory.CROSSBOW), Rarity.COMMON, listOf(RamStats.DAMAGE to 9.0)),
            IDef("trident", "Trident", Material.TRIDENT, setOf(ItemCategory.TRIDENT), Rarity.RARE, listOf(RamStats.DAMAGE to 10.0)),
            IDef("mace", "Mace", Material.MACE, setOf(ItemCategory.MACE), Rarity.RARE, listOf(RamStats.DAMAGE to 15.0)),
            IDef("fishing_rod", "Fishing Rod", Material.FISHING_ROD, setOf(ItemCategory.FISHING_ROD), Rarity.COMMON, emptyList()),
            IDef("elytra", "Elytra", Material.ELYTRA, setOf(ItemCategory.ELYTRA), Rarity.RARE, emptyList()),
            // Helmets
            IDef("leather_helmet", "Leather Helmet", Material.LEATHER_HELMET, setOf(ItemCategory.HELMET), Rarity.COMMON, listOf(RamStats.DEFENSE to 2.5)),
            IDef("chainmail_helmet", "Chainmail Helmet", Material.CHAINMAIL_HELMET, setOf(ItemCategory.HELMET), Rarity.COMMON, listOf(RamStats.DEFENSE to 2.5)),
            IDef("iron_helmet", "Iron Helmet", Material.IRON_HELMET, setOf(ItemCategory.HELMET), Rarity.COMMON, listOf(RamStats.DEFENSE to 15.0)),
            IDef("golden_helmet", "Golden Helmet", Material.GOLDEN_HELMET, setOf(ItemCategory.HELMET), Rarity.COMMON, listOf(RamStats.DEFENSE to 12.5)),
            IDef("diamond_helmet", "Diamond Helmet", Material.DIAMOND_HELMET, setOf(ItemCategory.HELMET), Rarity.UNCOMMON, listOf(RamStats.DEFENSE to 25.0)),
            IDef("netherite_helmet", "Netherite Helmet", Material.NETHERITE_HELMET, setOf(ItemCategory.HELMET), Rarity.RARE, listOf(RamStats.DEFENSE to 35.0, RamStats.TRUE_DEFENSE to 5.0)),
            // Chestplates
            IDef("leather_chestplate", "Leather Chestplate", Material.LEATHER_CHESTPLATE, setOf(ItemCategory.CHESTPLATE), Rarity.COMMON, listOf(RamStats.DEFENSE to 5.0)),
            IDef("chainmail_chestplate", "Chainmail Chestplate", Material.CHAINMAIL_CHESTPLATE, setOf(ItemCategory.CHESTPLATE), Rarity.COMMON, listOf(RamStats.DEFENSE to 5.0)),
            IDef("iron_chestplate", "Iron Chestplate", Material.IRON_CHESTPLATE, setOf(ItemCategory.CHESTPLATE), Rarity.COMMON, listOf(RamStats.DEFENSE to 25.0)),
            IDef("golden_chestplate", "Golden Chestplate", Material.GOLDEN_CHESTPLATE, setOf(ItemCategory.CHESTPLATE), Rarity.COMMON, listOf(RamStats.DEFENSE to 15.0)),
            IDef("diamond_chestplate", "Diamond Chestplate", Material.DIAMOND_CHESTPLATE, setOf(ItemCategory.CHESTPLATE), Rarity.UNCOMMON, listOf(RamStats.DEFENSE to 35.0)),
            IDef("netherite_chestplate", "Netherite Chestplate", Material.NETHERITE_CHESTPLATE, setOf(ItemCategory.CHESTPLATE), Rarity.RARE, listOf(RamStats.DEFENSE to 50.0, RamStats.TRUE_DEFENSE to 5.0)),
            // Leggings
            IDef("leather_leggings", "Leather Leggings", Material.LEATHER_LEGGINGS, setOf(ItemCategory.LEGGINGS), Rarity.COMMON, listOf(RamStats.DEFENSE to 3.5)),
            IDef("chainmail_leggings", "Chainmail Leggings", Material.CHAINMAIL_LEGGINGS, setOf(ItemCategory.LEGGINGS), Rarity.COMMON, listOf(RamStats.DEFENSE to 3.5)),
            IDef("iron_leggings", "Iron Leggings", Material.IRON_LEGGINGS, setOf(ItemCategory.LEGGINGS), Rarity.COMMON, listOf(RamStats.DEFENSE to 20.0)),
            IDef("golden_leggings", "Golden Leggings", Material.GOLDEN_LEGGINGS, setOf(ItemCategory.LEGGINGS), Rarity.COMMON, listOf(RamStats.DEFENSE to 12.5)),
            IDef("diamond_leggings", "Diamond Leggings", Material.DIAMOND_LEGGINGS, setOf(ItemCategory.LEGGINGS), Rarity.UNCOMMON, listOf(RamStats.DEFENSE to 25.0)),
            IDef("netherite_leggings", "Netherite Leggings", Material.NETHERITE_LEGGINGS, setOf(ItemCategory.LEGGINGS), Rarity.RARE, listOf(RamStats.DEFENSE to 35.0, RamStats.TRUE_DEFENSE to 5.0)),
            // Boots
            IDef("leather_boots", "Leather Boots", Material.LEATHER_BOOTS, setOf(ItemCategory.BOOTS), Rarity.COMMON, listOf(RamStats.DEFENSE to 2.5)),
            IDef("chainmail_boots", "Chainmail Boots", Material.CHAINMAIL_BOOTS, setOf(ItemCategory.BOOTS), Rarity.COMMON, listOf(RamStats.DEFENSE to 2.5)),
            IDef("iron_boots", "Iron Boots", Material.IRON_BOOTS, setOf(ItemCategory.BOOTS), Rarity.COMMON, listOf(RamStats.DEFENSE to 15.0)),
            IDef("golden_boots", "Golden Boots", Material.GOLDEN_BOOTS, setOf(ItemCategory.BOOTS), Rarity.COMMON, listOf(RamStats.DEFENSE to 10.0)),
            IDef("diamond_boots", "Diamond Boots", Material.DIAMOND_BOOTS, setOf(ItemCategory.BOOTS), Rarity.UNCOMMON, listOf(RamStats.DEFENSE to 15.0)),
            IDef("netherite_boots", "Netherite Boots", Material.NETHERITE_BOOTS, setOf(ItemCategory.BOOTS), Rarity.RARE, listOf(RamStats.DEFENSE to 25.0, RamStats.TRUE_DEFENSE to 5.0)),
            IDef("enchanted_book", "Enchanted Book", Material.ENCHANTED_BOOK, setOf(ItemCategory.ENCHANTED_BOOK), Rarity.UNCOMMON, emptyList()),
        )

        private val WEAPON_CATS = setOf(ItemCategory.SWORD, ItemCategory.AXE, ItemCategory.MACE, ItemCategory.TRIDENT, ItemCategory.BOW, ItemCategory.CROSSBOW)
        private val ARMOR_CATS = setOf(ItemCategory.HELMET, ItemCategory.CHESTPLATE, ItemCategory.LEGGINGS, ItemCategory.BOOTS)

        private fun itemLevelFor(rarity: Rarity): Int = when (rarity) {
            Rarity.COMMON -> 1
            Rarity.UNCOMMON -> 11
            Rarity.RARE -> 26
            Rarity.EPIC -> 46
            Rarity.LEGENDARY -> 60
            Rarity.MYTHIC -> 85
        }

        private fun combatLevelFor(rarity: Rarity): Int = when (rarity) {
            Rarity.COMMON -> 0
            Rarity.UNCOMMON -> 10
            Rarity.RARE -> 20
            Rarity.EPIC -> 35
            Rarity.LEGENDARY -> 45
            Rarity.MYTHIC -> 60
        }

        private fun requirementsFor(d: IDef): List<ItemRequirement> {
            val level = combatLevelFor(d.rarity)
            if (level <= 0) return emptyList()
            return when {
                d.cats.any { it in WEAPON_CATS } -> listOf(ItemRequirement.SkillLevel(RamSkills.COMBAT, level))
                d.cats.any { it in ARMOR_CATS } -> listOf(ItemRequirement.SkillLevel(RamSkills.COMBAT, level / 2))
                else -> emptyList()
            }
        }

        private val CUSTOM: List<ItemDefinition> = listOf(
            ItemDefinition(
                key = ik("rogue_blade"),
                displayName = Component.text("Rogue Blade"),
                material = Material.IRON_SWORD,
                rarity = Rarity.RARE,
                categories = setOf(ItemCategory.SWORD),
                baseStats = listOf(stat(RamStats.DAMAGE, 12.0, "rogue_blade")),
                statRolls = listOf(
                    StatRoll(RamStats.STRENGTH, 5.0, 15.0),
                    StatRoll(RamStats.CRIT_CHANCE, 5.0, 12.0),
                    StatRoll(RamStats.CRIT_DAMAGE, 10.0, 30.0),
                ),
                itemLevel = itemLevelFor(Rarity.RARE),
                requirements = listOf(
                    ItemRequirement.SkillLevel(RamSkills.COMBAT, 25),
                    ItemRequirement.StatThreshold(RamStats.STRENGTH, 20.0),
                ),
            ),
            ItemDefinition(
                key = ik("warden_husk_chest"),
                displayName = Component.text("Warden Husk Chestplate"),
                material = Material.NETHERITE_CHESTPLATE,
                rarity = Rarity.LEGENDARY,
                categories = setOf(ItemCategory.CHESTPLATE),
                baseStats = listOf(
                    stat(RamStats.DEFENSE, 60.0, "warden_husk_chest"),
                    stat(RamStats.TRUE_DEFENSE, 15.0, "warden_husk_chest"),
                ),
                statRolls = listOf(
                    StatRoll(RamStats.HEALTH, 30.0, 80.0),
                    StatRoll(RamStats.WISDOM, 20.0, 60.0),
                ),
                itemLevel = itemLevelFor(Rarity.LEGENDARY),
                requirements = listOf(
                    ItemRequirement.SkillLevel(RamSkills.COMBAT, 45),
                    ItemRequirement.StatThreshold(RamStats.HEALTH, 150.0),
                ),
            ),
            ItemDefinition(
                key = ik("dragon_fang"),
                displayName = Component.text("Dragon Fang"),
                material = Material.NETHERITE_SWORD,
                rarity = Rarity.MYTHIC,
                categories = setOf(ItemCategory.SWORD),
                baseStats = listOf(stat(RamStats.DAMAGE, 40.0, "dragon_fang")),
                statRolls = listOf(
                    StatRoll(RamStats.STRENGTH, 30.0, 80.0),
                    StatRoll(RamStats.CRIT_DAMAGE, 40.0, 100.0),
                    StatRoll(RamStats.FEROCITY, 20.0, 60.0),
                ),
                itemLevel = itemLevelFor(Rarity.MYTHIC),
                requirements = listOf(
                    ItemRequirement.SkillLevel(RamSkills.COMBAT, 70),
                    ItemRequirement.PerkOwned(ContentId.of("ramrpg", "dragon_slayer")),
                ),
            ),
            ItemDefinition(
                key = ik("ember_charm"),
                displayName = Component.text("Ember Charm"),
                material = Material.BLAZE_POWDER,
                rarity = Rarity.UNCOMMON,
                categories = setOf(ItemCategory.MISC),
                baseStats = listOf(stat(RamStats.STRENGTH, 5.0, "ember_charm")),
                itemLevel = itemLevelFor(Rarity.UNCOMMON),
            ),
        )

        fun buildAll(): Map<ItemKey, ItemDefinition> {
            val out = LinkedHashMap<ItemKey, ItemDefinition>()
            for (d in DEFS) {
                val mods = d.stats.map { (k, v) -> stat(k, v, d.id) }
                val rolls = when {
                    d.rolls.isNotEmpty() -> d.rolls
                    d.cats.any { it in WEAPON_CATS } -> weaponRolls()
                    d.cats.any { it in ARMOR_CATS } -> armorRolls()
                    else -> emptyList()
                }
                val def = ItemDefinition(
                    key = ik(d.id),
                    displayName = Component.text(d.name),
                    material = d.mat,
                    rarity = d.rarity,
                    categories = d.cats,
                    baseStats = mods,
                    allowVanillaWrapper = true,
                    statRolls = rolls,
                    itemLevel = itemLevelFor(d.rarity),
                    requirements = requirementsFor(d),
                )
                out[def.key] = def
            }
            for (custom in CUSTOM) out[custom.key] = custom
            return out
        }
    }
}
