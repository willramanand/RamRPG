/** Default ReforgeDefinitions: Fierce / Sharp / Heavy / Pure / Wise. */
package dev.willram.ramrpg.builtin.reforges

import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.reforges.ReforgeDefinition
import dev.willram.ramrpg.api.reforges.ReforgeIds
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.builtin.identity.RamStats
import net.kyori.adventure.text.Component

private const val OWNER = "ramrpg-builtin"

private val WEAPONS = setOf(ItemCategory.SWORD, ItemCategory.AXE, ItemCategory.MACE, ItemCategory.TRIDENT, ItemCategory.BOW, ItemCategory.CROSSBOW)
private val ARMOR = setOf(ItemCategory.HELMET, ItemCategory.CHESTPLATE, ItemCategory.LEGGINGS, ItemCategory.BOOTS)

object BuiltinReforges {
    fun registerAll(reg: ReforgeRegistry) {
        reg.register(OWNER, ReforgeDefinition(
            ReforgeIds.of("ramrpg", "fierce"), Component.text("Fierce"),
            bonusesByCategory = WEAPONS.associateWith { mapOf(RamStats.DAMAGE to 8.0, RamStats.STRENGTH to 6.0) },
        ))
        reg.register(OWNER, ReforgeDefinition(
            ReforgeIds.of("ramrpg", "sharp"), Component.text("Sharp"),
            bonusesByCategory = WEAPONS.associateWith { mapOf(RamStats.CRIT_CHANCE to 5.0, RamStats.CRIT_DAMAGE to 8.0) },
        ))
        reg.register(OWNER, ReforgeDefinition(
            ReforgeIds.of("ramrpg", "heavy"), Component.text("Heavy"),
            bonusesByCategory = WEAPONS.associateWith { mapOf(RamStats.DAMAGE to 12.0) } +
                ARMOR.associateWith { mapOf(RamStats.DEFENSE to 8.0) },
        ))
        reg.register(OWNER, ReforgeDefinition(
            ReforgeIds.of("ramrpg", "pure"), Component.text("Pure"),
            bonusesByCategory = ARMOR.associateWith { mapOf(RamStats.HEALTH to 12.0, RamStats.DEFENSE to 4.0) },
        ))
        reg.register(OWNER, ReforgeDefinition(
            ReforgeIds.of("ramrpg", "wise"), Component.text("Wise"),
            bonusesByCategory = ARMOR.associateWith { mapOf(RamStats.WISDOM to 30.0) },
        ))
    }
}
