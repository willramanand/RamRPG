/**
 * RewardActionFactory registrations for the reward types RamRPG needs beyond RamCore's built-ins
 * (WP-1.2b). RamCore's [RewardActionFactories.standard] already ships `money`, `command`, `message`,
 * and `permission-node-check`; `item` is deliberately left to consumers because it needs a running
 * server to build an `ItemStack`, which is exactly what [RpgItemRewardFactory] does with RamRPG's own
 * [ItemInstanceService]. `buff` and `perk_point` are stubs: WP-4.1a and WP-5.1a don't exist yet, so
 * their factories validate the HOCON shape content authors will use (so existing content survives the
 * upgrade unchanged) but always build an [UnimplementedRewardAction] that fails validation -- see
 * docs/design/1.2b-reward-types.md.
 *
 * Every `create()` here only parses and validates the [ConfigurationNode]; it never touches a live
 * player, Bukkit scheduler, or the network. Bad params throw [ContentDeserializeException] naming the
 * offending key, matching the contract [RewardActionFactory.create] documents and the pattern RamCore
 * itself uses in `RewardActionFactories.requireString`.
 */
package dev.willram.ramrpg.core.rewards

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.reward.RewardAction
import dev.willram.ramcore.reward.RewardActionFactory
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.core.platform.PlatformScheduler
import org.spongepowered.configurate.ConfigurationNode

/** Builds the `skill_xp` reward action: grants a fixed amount of XP in one skill. */
class SkillXpRewardFactory(
    private val skillService: SkillService,
    private val platform: PlatformScheduler,
) : RewardActionFactory {

    override fun type(): String = TYPE

    override fun create(params: ConfigurationNode): RewardAction {
        val skill = requireContentId(TYPE, params, "skill")
        val amount = requirePositiveDouble(TYPE, params, "amount")
        return SkillXpRewardAction(skillService, platform, SkillKey(skill), amount)
    }

    companion object { const val TYPE = "skill_xp" }
}

/** Builds the `rpg_item` reward action: gives a freshly-rolled instance of a registered [ItemDefinition]. */
class RpgItemRewardFactory(
    private val itemDefs: ItemDefinitionRegistry,
    private val itemInstances: ItemInstanceService,
    private val platform: PlatformScheduler,
) : RewardActionFactory {

    override fun type(): String = TYPE

    override fun create(params: ConfigurationNode): RewardAction {
        val itemId = requireContentId(TYPE, params, "item")
        val definition = itemDefs.get(ItemKey(itemId))
            ?: throw ContentDeserializeException("$TYPE reward references unknown item '$itemId'")
        val count = optionalPositiveInt(TYPE, params, "count", DEFAULT_COUNT)
        return RpgItemRewardAction(itemInstances, platform, definition, count)
    }

    companion object {
        const val TYPE = "rpg_item"
        const val DEFAULT_COUNT = 1
    }
}

/**
 * Stub for the `buff` reward type: [dev.willram.ramrpg.api.buffs.BuffService] does not exist until
 * WP-4.1a. Validates that content already looks like a real buff reward (a buff id, an optional
 * duration and stack count) so nothing needs re-authoring once it lands; the built action always
 * fails validation until then.
 */
class BuffRewardFactory : RewardActionFactory {

    override fun type(): String = TYPE

    override fun create(params: ConfigurationNode): RewardAction {
        val buff = requireContentId(TYPE, params, "buff")
        val durationTicks = optionalPositiveInt(TYPE, params, "duration_ticks", DEFAULT_DURATION_TICKS)
        val stacks = optionalPositiveInt(TYPE, params, "stacks", DEFAULT_STACKS)
        return UnimplementedRewardAction(
            TYPE,
            "buff reward '$buff' ($durationTicks ticks, $stacks stack(s)) needs the buff system from WP-4.1a",
        )
    }

    companion object {
        const val TYPE = "buff"
        const val DEFAULT_DURATION_TICKS = 200
        const val DEFAULT_STACKS = 1
    }
}

/**
 * Stub for the `perk_point` reward type: the perk system does not exist until WP-5.1a. Validates that
 * content already looks like a real perk-point reward (a positive point count, an optional tree id)
 * so nothing needs re-authoring once it lands; the built action always fails validation until then.
 */
class PerkPointRewardFactory : RewardActionFactory {

    override fun type(): String = TYPE

    override fun create(params: ConfigurationNode): RewardAction {
        val points = optionalPositiveInt(TYPE, params, "points", DEFAULT_POINTS)
        val treeRaw = params.node("tree").getString()
        val tree = if (treeRaw.isNullOrBlank()) null else parseContentId(TYPE, "tree", treeRaw)
        val target = if (tree == null) "the general tree" else "tree '$tree'"
        return UnimplementedRewardAction(
            TYPE,
            "perk_point reward ($points point(s) in $target) needs the perk system from WP-5.1a",
        )
    }

    companion object {
        const val TYPE = "perk_point"
        const val DEFAULT_POINTS = 1
    }
}

private fun requireString(type: String, params: ConfigurationNode, key: String): String {
    val value = params.node(key).getString()
    if (value.isNullOrBlank()) {
        throw ContentDeserializeException("$type reward is missing '$key'")
    }
    return value
}

private fun requireContentId(type: String, params: ConfigurationNode, key: String): ContentId =
    parseContentId(type, key, requireString(type, params, key))

private fun parseContentId(type: String, key: String, raw: String): ContentId =
    try {
        ContentId.parse(raw)
    } catch (invalid: RuntimeException) {
        throw ContentDeserializeException("$type reward has an invalid '$key': '$raw'", invalid)
    }

private fun requirePositiveDouble(type: String, params: ConfigurationNode, key: String): Double {
    val node = params.node(key)
    if (node.virtual()) {
        throw ContentDeserializeException("$type reward is missing '$key'")
    }
    val value = node.getDouble()
    if (value <= 0.0) {
        throw ContentDeserializeException("$type reward '$key' must be positive")
    }
    return value
}

private fun optionalPositiveInt(type: String, params: ConfigurationNode, key: String, default: Int): Int {
    val node = params.node(key)
    if (node.virtual()) {
        return default
    }
    val value = node.getInt()
    if (value <= 0) {
        throw ContentDeserializeException("$type reward '$key' must be positive")
    }
    return value
}
