/**
 * Runtime content override loader. Reads JSON files from
 * dataFolder/content/{stats,items,skills,entities,enchants}/&lt;id&gt;.json
 * and re-registers builtin definitions with override-applied copies.
 */
package dev.willram.ramrpg.core.config

import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.data.DataItem
import dev.willram.ramcore.gson.GsonProvider
import dev.willram.ramrpg.api.effects.Effect
import dev.willram.ramrpg.api.effects.ScalingFormula
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.enchants.EnchantingContext
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.enchants.RPGEnchantment
import dev.willram.ramrpg.api.entities.EntityProfile
import dev.willram.ramrpg.api.entities.EntityProfileRegistry
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.EntityProfileKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.Rarity
import dev.willram.ramrpg.api.skills.SkillDefinition
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.XpCurves
import dev.willram.ramrpg.api.stats.ModifierOperation
import dev.willram.ramrpg.api.stats.ModifierSource
import dev.willram.ramrpg.api.stats.SourceType
import dev.willram.ramrpg.api.stats.StatDefinition
import dev.willram.ramrpg.api.stats.StatModifier
import dev.willram.ramrpg.api.stats.StatService
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

private const val OWNER = "ramrpg-override"

class StatOverride : DataItem() {
    var defaultBase: Double? = null
    var perLevel: Double? = null
    var min: Double? = null
    var max: Double? = null
}

class ItemOverride : DataItem() {
    /** Keyed by stat ContentId string. ADD-only. */
    var baseStats: Map<String, Double> = emptyMap()
    var rarity: String? = null
    var allowVanillaWrapper: Boolean? = null
}

class SkillOverride : DataItem() {
    var maxLevel: Int? = null
    var xpBase: Double? = null
    var xpMul: Double? = null
}

class EntityOverride : DataItem() {
    /** Keyed by stat ContentId string. */
    var baseStats: Map<String, Double> = emptyMap()
    var xpAmount: Double? = null
    var skill: String? = null
    var xpSource: String? = null
}

class EnchantOverride : DataItem() {
    var maxLevel: Int? = null
    /** Multiplier applied to all StatEffect amounts produced by enchant.effects(level). */
    var statScale: Double? = null
}

private class OverrideEnchantment(
    private val base: RPGEnchantment,
    private val maxLevelOv: Int?,
    private val statScale: Double?,
) : RPGEnchantment {
    override val key get() = base.key
    override val displayName get() = base.displayName
    override val maxLevel: Int get() = maxLevelOv ?: base.maxLevel
    override val targets get() = base.targets
    override val rarity get() = base.rarity
    override fun description(level: Int) = base.description(level)
    override fun conflicts(other: EnchantmentKey) = base.conflicts(other)
    override fun bookshelfPower(level: Int) = base.bookshelfPower(level)
    override fun xpCost(level: Int, ctx: EnchantingContext) = base.xpCost(level, ctx)
    override fun effects(level: Int): List<Effect> {
        val effs = base.effects(level)
        val s = statScale ?: return effs
        return effs.map { eff ->
            if (eff is StatEffect) {
                val original = eff.amount
                eff.copy(amount = ScalingFormula { ctx -> original.eval(ctx) * s })
            } else eff
        }
    }
}

class ContentOverrideLoader(private val baseDir: Path) {

    fun apply(
        stats: StatService,
        items: ItemDefinitionRegistry,
        skills: SkillRegistry? = null,
        entities: EntityProfileRegistry? = null,
        enchants: EnchantmentRegistry? = null,
    ) {
        applyStatOverrides(stats)
        applyItemOverrides(items)
        skills?.let { applySkillOverrides(it) }
        entities?.let { applyEntityOverrides(it) }
        enchants?.let { applyEnchantOverrides(it) }
    }

    // Loads every <id>.json override file in a subdirectory, keyed by the URL-decoded file name (the
    // same encoding DataKeyCodec.stringKeys uses). Parsing is synchronous with RamCore's Gson so this
    // startup step neither blocks on the scheduler nor needs one under test. This is a temporary
    // JSON-patch path; WP-1.5a replaces it with the HOCON ContentLoader.
    private inline fun <reified V : DataItem> openRepo(sub: String): Map<String, V>? {
        val dir = baseDir.resolve(sub).toFile()
        if (!dir.isDirectory) return null
        val gson = GsonProvider.standard()
        val out = LinkedHashMap<String, V>()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { file ->
            val key = URLDecoder.decode(file.name.removeSuffix(".json"), StandardCharsets.UTF_8)
            file.reader().use { reader -> gson.fromJson(reader, V::class.java)?.let { out[key] = it } }
        }
        return out
    }

    private fun applyStatOverrides(stats: StatService) {
        val repo = openRepo<StatOverride>("stats") ?: return
        for ((idStr, ov) in repo) {
            val cid = parseId(idStr) ?: continue
            val current = stats.definition(StatKey(cid)) ?: continue
            stats.registerDefinition(current.copy(
                defaultBase = ov.defaultBase ?: current.defaultBase,
                perLevel = ov.perLevel ?: current.perLevel,
                min = ov.min ?: current.min,
                max = ov.max ?: current.max,
            ))
        }
    }

    private fun applyItemOverrides(items: ItemDefinitionRegistry) {
        val repo = openRepo<ItemOverride>("items") ?: return
        for ((idStr, ov) in repo) {
            val cid = parseId(idStr) ?: continue
            val current = items.get(ItemKey(cid)) ?: continue
            val newStats = if (ov.baseStats.isEmpty()) current.baseStats
            else ov.baseStats.mapNotNull { (k, v) ->
                val sk = parseId(k)?.let(::StatKey) ?: return@mapNotNull null
                StatModifier(sk, v, ModifierOperation.ADD, ModifierSource(SourceType.ITEM, cid))
            }
            val rarity = ov.rarity
                ?.let { runCatching { Rarity.valueOf(it.uppercase()) }.getOrNull() }
                ?: current.rarity
            items.register(OWNER, current.copy(
                baseStats = newStats,
                rarity = rarity,
                allowVanillaWrapper = ov.allowVanillaWrapper ?: current.allowVanillaWrapper,
            ))
        }
    }

    private fun applySkillOverrides(skills: SkillRegistry) {
        val repo = openRepo<SkillOverride>("skills") ?: return
        for ((idStr, ov) in repo) {
            val cid = parseId(idStr) ?: continue
            val current = skills.get(SkillKey(cid)) ?: continue
            val curve = if (ov.xpBase != null || ov.xpMul != null)
                XpCurves.polynomial(ov.xpBase ?: 50.0, ov.xpMul ?: 25.0)
            else current.xpCurve
            skills.register(OWNER, SkillDefinition(
                key = current.key,
                displayName = current.displayName,
                description = current.description,
                maxLevel = ov.maxLevel ?: current.maxLevel,
                xpCurve = curve,
                rewards = current.rewards,
                barColor = current.barColor,
            ))
        }
    }

    private fun applyEntityOverrides(entities: EntityProfileRegistry) {
        val repo = openRepo<EntityOverride>("entities") ?: return
        for ((idStr, ov) in repo) {
            val cid = parseId(idStr) ?: continue
            val current = entities.get(EntityProfileKey(cid)) ?: continue
            val newStats = if (ov.baseStats.isEmpty()) current.baseStats
            else ov.baseStats.mapKeys { (k, _) -> StatKey(ContentId.parse(k)) }
            entities.register(OWNER, EntityProfile(
                key = current.key,
                baseStats = newStats,
                xpSourceKey = ov.xpSource?.let { XpSourceKey(ContentId.parse(it)) } ?: current.xpSourceKey,
                xpAmount = ov.xpAmount ?: current.xpAmount,
                skill = ov.skill?.let { SkillKey(ContentId.parse(it)) } ?: current.skill,
                // Loot overriding via this JSON shim was dropped in WP-1.1a (loot is now a LootTable);
                // loot tables become editable content in WP-1.5a. The profile keeps its builtin table.
                lootTable = current.lootTable,
                isBoss = current.isBoss,
                displayName = current.displayName,
            ))
        }
    }

    private fun applyEnchantOverrides(reg: EnchantmentRegistry) {
        val repo = openRepo<EnchantOverride>("enchants") ?: return
        for ((idStr, ov) in repo) {
            val cid = parseId(idStr) ?: continue
            val current = reg.get(EnchantmentKey(cid)) ?: continue
            reg.register(OWNER, OverrideEnchantment(current, ov.maxLevel, ov.statScale))
        }
    }

    private fun parseId(s: String): ContentId? = runCatching { ContentId.parse(s) }.getOrNull()
}
