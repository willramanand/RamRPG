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
        val data = ItemInstanceData(
            identity = ItemIdentity(ItemKey.of("ramrpg", "iron_sword"), instanceId = UUID.randomUUID()),
            upgradeLevel = 3,
            customRolls = mapOf(StatKey.of("ramrpg", "strength") to 12.0),
            enchantments = mapOf(EnchantmentKey.of("ramrpg", "sharpness") to 5),
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
    }
}
