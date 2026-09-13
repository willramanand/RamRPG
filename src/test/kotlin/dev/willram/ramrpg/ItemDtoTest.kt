package dev.willram.ramrpg

import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.items.ItemIdentity
import dev.willram.ramrpg.api.items.ItemInstanceData
import dev.willram.ramrpg.core.services.ItemInstanceServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class ItemDtoTest {

    @Test
    fun `dto roundtrip preserves identity and enchantments`() {
        val crafter = UUID.randomUUID()
        val data = ItemInstanceData(
            identity = ItemIdentity(ItemKey.of("ramrpg", "iron_sword"), instanceId = UUID.randomUUID()),
            upgradeLevel = 3,
            customRolls = mapOf(StatKey.of("ramrpg", "strength") to 12.0),
            enchantments = mapOf(EnchantmentKey.of("ramrpg", "sharpness") to 5),
            quality = 0.7,
            craftedBy = crafter,
            maxDurability = 300,
            durability = 275,
        )
        val dto = ItemInstanceServiceImpl.ItemDto.from(data)
        val json = dto.toJson()
        val parsed = ItemInstanceServiceImpl.ItemDto.parse(json)!!
        val back = parsed.toDomain()
        assertEquals(data.identity.key, back.identity.key)
        assertEquals(data.identity.instanceId, back.identity.instanceId)
        assertEquals(data.upgradeLevel, back.upgradeLevel)
        assertEquals(data.customRolls, back.customRolls)
        assertEquals(data.enchantments, back.enchantments)
        // WP-2.1b schema v2 fields survive the round-trip too.
        assertEquals(data.quality, back.quality)
        assertEquals(data.craftedBy, back.craftedBy)
        assertEquals(data.maxDurability, back.maxDurability)
        assertEquals(data.durability, back.durability)
    }

    @Test
    fun `v1 json missing the v2 fields deserializes with schema defaults`() {
        // Fresh-server / no-migration policy: a v1 blob simply omits quality/craftedBy/durability. It
        // must still parse, and the new fields fill from the ItemInstanceData schema defaults (NOT a
        // migration engine, NO checked-in fixture -- the JSON is constructed inline). The shape mirrors
        // what a real v1 `from()` wrote (empty collections serialize as []/{}); only the v2 keys are gone.
        val v1 = """{"v":1,"k":"ramrpg:iron_sword","u":2,"s":[],"rolls":{"ramrpg:strength":8.0},"ench":{}}"""
        val back = ItemInstanceServiceImpl.ItemDto.parse(v1)!!.toDomain()
        assertEquals(ItemInstanceData.DEFAULT_QUALITY, back.quality)
        assertEquals(null, back.craftedBy)
        assertEquals(ItemInstanceData.DEFAULT_MAX_DURABILITY, back.maxDurability)
        assertEquals(ItemInstanceData.DEFAULT_MAX_DURABILITY, back.durability)
        // Existing v1 data is untouched.
        assertEquals(2, back.upgradeLevel)
        assertEquals(mapOf(StatKey.of("ramrpg", "strength") to 8.0), back.customRolls)
    }
}
