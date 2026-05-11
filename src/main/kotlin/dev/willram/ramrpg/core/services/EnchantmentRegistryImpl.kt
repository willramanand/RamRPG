package dev.willram.ramrpg.core.services

import dev.willram.ramcore.content.ContentKey
import dev.willram.ramcore.content.ContentRegistry
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.enchants.RPGEnchantment
import dev.willram.ramrpg.api.identity.EnchantmentKey

class EnchantmentRegistryImpl(
    private val backing: ContentRegistry<RPGEnchantment> = ContentRegistry.create(RPGEnchantment::class.java)
) : EnchantmentRegistry {
    override fun register(owner: String, ench: RPGEnchantment) {
        backing.register(owner, ContentKey.of(ench.key.id, RPGEnchantment::class.java), ench)
    }
    override fun unregisterOwner(owner: String): Int = backing.unregisterOwner(owner)
    override fun get(key: EnchantmentKey): RPGEnchantment? = backing.get(key.id).orElse(null)
    override fun all(): Collection<RPGEnchantment> = backing.entries().map { it.value() }
}
