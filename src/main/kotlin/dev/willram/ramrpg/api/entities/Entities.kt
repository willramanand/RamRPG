/**
 * Entity profile registry. Maps Bukkit `EntityType` (or Mythic mob type)
 * to baseline RPG stats (HP/DEF/DAMAGE) plus an XP grant skill + amount.
 * Spawn listener applies attributes; combat pipeline reads PDC for defense.
 */
package dev.willram.ramrpg.api.entities

import dev.willram.ramcore.loot.LootTable
import dev.willram.ramrpg.api.identity.EntityProfileKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import org.bukkit.entity.LivingEntity

data class EntityProfile(
    val key: EntityProfileKey,
    val baseStats: Map<StatKey, Double> = emptyMap(),
    val xpSourceKey: XpSourceKey? = null,
    val xpAmount: Double = 0.0,
    val skill: SkillKey? = null,
    /**
     * Drops for this mob as a RamCore LootTable, resolved by LootGenerator on death. Null = no custom
     * drops. Built with [dev.willram.ramrpg.core.loot.RpgLootTables]. (Phase-1 rule-7 waiver: this
     * replaced the old loot/lootPool/lootRolls fields with no deprecation cycle. WP-1.5a moves this to
     * a ContentId reference into a loot-table content registry.)
     */
    val lootTable: LootTable? = null,
    val isBoss: Boolean = false,
    /** Optional display name override; defaults to profile key value. */
    val displayName: String? = null,
)

interface EntityProfileRegistry {
    fun register(owner: String, profile: EntityProfile)
    fun unregisterOwner(owner: String): Int
    fun get(key: EntityProfileKey): EntityProfile?
    fun resolve(entity: LivingEntity): EntityProfile?
    fun all(): Collection<EntityProfile>
}
