/**
 * Stat aggregation API. Providers emit [StatModifier]s; the [StatService]
 * reduces them per-player into a [StatSnapshot] using ADD → MULTIPLY_BASE →
 * MULTIPLY_TOTAL aggregation, then applies optional min/max clamps.
 */
package dev.willram.ramrpg.api.stats

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.identity.StatKey
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.entity.Player

enum class StatFormat {
    WHOLE, ONE_DECIMAL, PERCENT;

    fun format(amount: Double): String = when (this) {
        WHOLE -> "%.0f".format(amount)
        ONE_DECIMAL -> "%.1f".format(amount)
        PERCENT -> "%.0f%%".format(amount)
    }
}

data class StatDefinition(
    val key: StatKey,
    val displayName: Component,
    val symbol: String? = null,
    val color: TextColor = NamedTextColor.WHITE,
    val defaultBase: Double = 0.0,
    val perLevel: Double = 0.0,
    val min: Double? = null,
    val max: Double? = null,
    val format: StatFormat = StatFormat.WHOLE,
    /** Translation key path; viewer-locale resolution applied via Adventure GlobalTranslator. */
    val translationKey: String? = null,
)

enum class ModifierOperation { ADD, MULTIPLY_BASE, MULTIPLY_TOTAL }

enum class SourceType { BASE, SKILL, ITEM, ENCHANT, REFORGE, SOCKET, BUFF, REGION, BOSS, PERM }

data class ModifierSource(val type: SourceType, val ref: ContentId)

data class StatModifier(
    val stat: StatKey,
    val amount: Double,
    val operation: ModifierOperation,
    val source: ModifierSource,
)

class StatSnapshot(private val values: Map<StatKey, Double>) {
    operator fun get(stat: StatKey): Double = values[stat] ?: 0.0
    fun asMap(): Map<StatKey, Double> = values
}

enum class StatDirtyReason {
    EQUIPMENT_CHANGED, SKILL_LEVEL_CHANGED, ENCHANT_CHANGED,
    EFFECT_ADDED, EFFECT_REMOVED, WORLD_CHANGED, INSTANCE_DATA_CHANGED, JOIN
}

interface StatContext {
    val player: Player
}

interface StatProvider {
    fun provideStats(ctx: StatContext, output: MutableList<StatModifier>)
}

interface StatService {
    fun registerDefinition(def: StatDefinition)
    fun definitions(): Collection<StatDefinition>
    fun definition(key: StatKey): StatDefinition?
    fun registerProvider(provider: StatProvider, owner: String)
    fun unregisterProviders(owner: String)
    fun markDirty(player: Player, reason: StatDirtyReason)
    fun snapshot(player: Player): StatSnapshot
    fun recalculateNow(player: Player): StatSnapshot
}
