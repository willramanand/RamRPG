/**
 * WP-1.5a: PURE spec for an `entities/` content entry -> [EntityProfile]. Bukkit-free ([EntityProfile]
 * itself references Bukkit only through the registry interface, not the data class), so [toProfile]
 * lives here.
 *
 * SEAM: [EntityProfile.lootTable] stays null here. Loot tables are RamCore `LootTable`s built in code
 * today (WP-1.1a); making them editable content (a `loot/` type resolved by id reference) is out of
 * WP-1.5a's scope, so a HOCON-authored profile inherits no drops until that lands. The builtin
 * registration path keeps its code-built table.
 *
 * HOCON shape:
 * ```
 * id = "ramrpg:zombie"
 * base-stats { "ramrpg:health" = 100.0, "ramrpg:damage" = 8.0 }
 * xp-source = "ramrpg:kill_zombie"
 * xp-amount = 7.0
 * skill = "ramrpg:combat"
 * is-boss = false
 * display-name = "Zombie"           # plain text (EntityProfile.displayName is a raw String)
 * ```
 */
package dev.willram.ramrpg.core.config.specs

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.entities.EntityProfile
import dev.willram.ramrpg.api.identity.EntityProfileKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.core.config.RpgContentSpec
import dev.willram.ramrpg.core.config.SpecNodes
import org.spongepowered.configurate.ConfigurationNode

data class EntityProfileSpec(
    val key: EntityProfileKey,
    val baseStats: Map<StatKey, Double>,
    val xpSource: XpSourceKey?,
    val xpAmount: Double,
    val skill: SkillKey?,
    val isBoss: Boolean,
    val displayName: String?,
) : RpgContentSpec {
    override val id: ContentId get() = key.id

    fun toProfile(): EntityProfile = EntityProfile(
        key = key,
        baseStats = baseStats,
        xpSourceKey = xpSource,
        xpAmount = xpAmount,
        skill = skill,
        lootTable = null,
        isBoss = isBoss,
        displayName = displayName,
    )

    companion object {
        fun deserialize(node: ConfigurationNode): EntityProfileSpec {
            val id = SpecNodes.requireId(node)
            return EntityProfileSpec(
                key = EntityProfileKey(id),
                baseStats = SpecNodes.statAmounts(node, "base-stats"),
                xpSource = SpecNodes.optionalId(node, "xp-source")?.let { XpSourceKey(it) },
                xpAmount = SpecNodes.doubleOr(node, "xp-amount", 0.0),
                skill = SpecNodes.optionalId(node, "skill")?.let { SkillKey(it) },
                isBoss = SpecNodes.boolOr(node, "is-boss", false),
                displayName = SpecNodes.optionalString(node, "display-name"),
            )
        }
    }
}
