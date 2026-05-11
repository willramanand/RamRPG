package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemCategory
import dev.willram.ramrpg.api.items.ItemDefinition
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceInit
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.items.StatRoll
import dev.willram.ramrpg.core.services.ItemInstanceServiceImpl
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StatRollTest {

    private fun stubRegistry(def: ItemDefinition): ItemDefinitionRegistry = object : ItemDefinitionRegistry {
        override fun get(key: ItemKey) = if (key == def.key) def else null
        override fun register(owner: String, def: ItemDefinition) {}
        override fun unregisterOwner(owner: String) = 0
        override fun all() = listOf(def)
        override fun revision() = 0
    }

    @Test
    fun `same seed produces identical rolls`() {
        val s1 = StatKey.of("test", "a")
        val s2 = StatKey.of("test", "b")
        val def = ItemDefinition(
            key = ItemKey.of("test", "x"),
            displayName = Component.text("X"),
            material = Material.STICK,
            rarity = Rarity.COMMON,
            categories = setOf(ItemCategory.MISC),
            statRolls = listOf(StatRoll(s1, 1.0, 10.0), StatRoll(s2, 5.0, 20.0)),
        )
        val svc = ItemInstanceServiceImpl(stubRegistry(def))
        val a = svc.rollStats(def, ItemInstanceInit(rollSeed = 42L))
        val b = svc.rollStats(def, ItemInstanceInit(rollSeed = 42L))
        assertEquals(a, b)
    }

    @Test
    fun `fixed range collapses to single value`() {
        val s = StatKey.of("test", "fixed")
        val def = ItemDefinition(
            key = ItemKey.of("test", "y"),
            displayName = Component.text("Y"),
            material = Material.STICK,
            rarity = Rarity.COMMON,
            categories = setOf(ItemCategory.MISC),
            statRolls = listOf(StatRoll(s, 7.0, 7.0)),
        )
        val svc = ItemInstanceServiceImpl(stubRegistry(def))
        val rolls = svc.rollStats(def, ItemInstanceInit(rollSeed = 1L))
        assertEquals(7.0, rolls[s])
    }
}
