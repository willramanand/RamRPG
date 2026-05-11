/**
 * Entity profile registry. Maps Bukkit `EntityType` (or Mythic mob type)
 * to baseline RPG stats (HP/DEF/DAMAGE) plus an XP grant skill + amount.
 * Spawn listener applies attributes; combat pipeline reads PDC for defense.
 */
package dev.willram.ramrpg.api.entities

import dev.willram.ramrpg.api.identity.EntityProfileKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import org.bukkit.entity.LivingEntity

/** Single weighted drop. */
data class LootEntry(
    val item: ItemKey,
    val weight: Double = 1.0,
    val minCount: Int = 1,
    val maxCount: Int = 1,
    /** Chance in [0,1] this entry rolls independently. */
    val chance: Double = 1.0,
)

data class EntityProfile(
    val key: EntityProfileKey,
    val baseStats: Map<StatKey, Double> = emptyMap(),
    val xpSourceKey: XpSourceKey? = null,
    val xpAmount: Double = 0.0,
    val skill: SkillKey? = null,
    /** Independent-chance drops (each rolls separately). */
    val loot: List<LootEntry> = emptyList(),
    /** Weighted pool drops (pick lootRolls entries by weight). */
    val lootPool: List<LootEntry> = emptyList(),
    val lootRolls: Int = 1,
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
