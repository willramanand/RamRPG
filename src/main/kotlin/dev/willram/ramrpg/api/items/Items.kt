/**
 * Item definition + per-stack instance data. [ItemDefinition] is the static
 * blueprint; [ItemInstanceData] is the per-stack mutable state (enchants,
 * sockets, reforge, custom rolls). [LoreTemplate] composes packet-rendered
 * lore from [LoreSection]s; rendering never mutates the canonical PDC item.
 */
package dev.willram.ramrpg.api.items

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.effects.Effect
import dev.willram.ramrpg.api.effects.ScalingContext
import dev.willram.ramrpg.api.effects.StatEffect
import dev.willram.ramrpg.api.identity.DamageTypeKey
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.StatKey
import dev.willram.ramrpg.api.stats.StatFormat
import dev.willram.ramrpg.api.stats.StatModifier
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.translation.GlobalTranslator
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.util.UUID

enum class Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC }

enum class ItemCategory {
    SWORD, AXE, PICKAXE, SHOVEL, HOE, BOW, CROSSBOW, TRIDENT, MACE,
    FISHING_ROD, ELYTRA, HELMET, CHESTPLATE, LEGGINGS, BOOTS,
    SHIELD, ENCHANTED_BOOK, MISC
}

/**
 * WP-2.1a: a gate that must be met before an [ItemDefinition] is usable. Definition-side only --
 * enforcement (inert-on-unmet) and lore rendering are WP-2.1c's job, not this one's. See
 * `docs/design/2.1a-item-level-requirements.md` for the itemLevel bands these are meant to line up with.
 */
sealed interface ItemRequirement {
    data class SkillLevel(val skill: SkillKey, val level: Int) : ItemRequirement
    data class StatThreshold(val stat: StatKey, val min: Double) : ItemRequirement

    /**
     * STUB (WP-2.1a): `PerkKey`/`PerkService` do not exist yet (WP-5.1a ships them), so this carries the
     * raw [ContentId] a content author names rather than a typed key. [isMet] treats this arm as
     * fail-closed (always UNMET) rather than silently reporting "met" -- a gate must never quietly
     * disappear just because its backing system isn't built yet. WP-5.1a replaces this evaluation to
     * delegate to the real PerkService (and may promote [perk] to a typed `PerkKey` then).
     */
    data class PerkOwned(val perk: ContentId) : ItemRequirement
}

/**
 * A minimal, pure snapshot [ItemRequirement.isMet] evaluates against -- decoupled from a live
 * [org.bukkit.entity.Player] so evaluation is off-server-testable. Adapting a real player's skill levels
 * / stat snapshot onto this shape is WP-2.1c's job (enforcement), not this one's.
 */
interface ItemRequirementState {
    fun skillLevel(skill: SkillKey): Int
    fun statValue(stat: StatKey): Double
}

/** Evaluates one requirement against [state]. See [ItemRequirement.PerkOwned] for its stub behavior. */
fun ItemRequirement.isMet(state: ItemRequirementState): Boolean = when (this) {
    is ItemRequirement.SkillLevel -> state.skillLevel(skill) >= level
    is ItemRequirement.StatThreshold -> state.statValue(stat) >= min
    is ItemRequirement.PerkOwned -> false
}

/**
 * WP-2.1c: why an item instance is inert (contributes no stats from any item-based
 * [dev.willram.ramrpg.api.stats.StatProvider]). See `docs/design/2.1c-inert-items.md` for the single
 * check ([ItemDefinition.inertReason]) every such provider must consult.
 */
enum class InertReason {
    /** At least one [ItemDefinition.requirements] entry is [isMet] `false` against the wearer's state. */
    UNMET_REQUIREMENT,

    /**
     * [ItemInstanceData.durability] has reached `0`. The field exists since WP-2.1b and this WP wires the
     * inert consequence, but nothing drains durability yet -- WP-2.2 owns the drain-on-hit mechanic. It
     * must NOT re-define what "inert" means; it only needs to make durability reach `<= 0` in the first
     * place for this reason to ever fire in practice.
     */
    ZERO_DURABILITY,
}

/**
 * WP-2.1a: the default [EquipmentSlot] each [ItemCategory] occupies when equipped -- see
 * `docs/design/2.1a-item-level-requirements.md` for the full table and rationale.
 * [ItemCategory.MISC] and [ItemCategory.ENCHANTED_BOOK] have no default slot (not directly equippable);
 * an [ItemDefinition] can still override with an explicit `equipSlots`.
 */
object EquipSlotDefaults {
    private val BY_CATEGORY: Map<ItemCategory, EquipmentSlot> = mapOf(
        ItemCategory.SWORD to EquipmentSlot.HAND,
        ItemCategory.AXE to EquipmentSlot.HAND,
        ItemCategory.PICKAXE to EquipmentSlot.HAND,
        ItemCategory.SHOVEL to EquipmentSlot.HAND,
        ItemCategory.HOE to EquipmentSlot.HAND,
        ItemCategory.BOW to EquipmentSlot.HAND,
        ItemCategory.CROSSBOW to EquipmentSlot.HAND,
        ItemCategory.TRIDENT to EquipmentSlot.HAND,
        ItemCategory.MACE to EquipmentSlot.HAND,
        ItemCategory.FISHING_ROD to EquipmentSlot.HAND,
        ItemCategory.SHIELD to EquipmentSlot.OFF_HAND,
        ItemCategory.HELMET to EquipmentSlot.HEAD,
        ItemCategory.CHESTPLATE to EquipmentSlot.CHEST,
        ItemCategory.ELYTRA to EquipmentSlot.CHEST,
        ItemCategory.LEGGINGS to EquipmentSlot.LEGS,
        ItemCategory.BOOTS to EquipmentSlot.FEET,
        // ItemCategory.ENCHANTED_BOOK, ItemCategory.MISC: intentionally absent -- no default slot.
    )

    /** The union of each category's default slot; categories with no default contribute nothing. */
    fun forCategories(categories: Set<ItemCategory>): Set<EquipmentSlot> =
        categories.mapNotNullTo(LinkedHashSet()) { BY_CATEGORY[it] }
}

data class ReforgeKey(val id: ContentId)

data class SocketData(val key: ContentId, val gem: ContentId? = null)

data class ItemIdentity(
    val key: ItemKey,
    val instanceId: UUID? = null,
    val schemaVersion: Int = ItemSchema.CURRENT,
)

data class ItemInstanceData(
    val identity: ItemIdentity,
    val upgradeLevel: Int = 0,
    val reforge: ReforgeKey? = null,
    val sockets: List<SocketData> = emptyList(),
    val customRolls: Map<StatKey, Double> = emptyMap(),
    val enchantments: Map<EnchantmentKey, Int> = emptyMap(),
    val owner: UUID? = null,
    val customName: String? = null,
    /**
     * WP-2.1b: instance quality in `[0.0, 1.0]` -- how good this particular roll of the item is.
     * Scales a stat roll's effective value within its `[min, max]` band (see
     * `ItemInstanceServiceImpl.qualityScaledRoll` and `docs/design/2.1b-quality-durability.md`).
     * Defaults to [DEFAULT_QUALITY] (0.5, the mid band) for drops; crafting (a later WP) sets it from
     * the crafter's skill. Values outside `[0,1]` are the caller's responsibility to keep sane.
     */
    val quality: Double = DEFAULT_QUALITY,
    /** WP-2.1b: the crafter's UUID, or `null` for drops / world-generated items. Set later by the crafting WP. */
    val craftedBy: UUID? = null,
    /**
     * WP-2.1b: this instance's durability ceiling. No definition-side field seeds it yet, so [create]
     * uses [DEFAULT_MAX_DURABILITY]; the number is a placeholder tuned in WP-2.2.
     */
    val maxDurability: Int = DEFAULT_MAX_DURABILITY,
    /**
     * WP-2.1b: current durability. Defaults to [maxDurability] (full). The DRAIN-on-use behaviour and
     * inert-at-0 handling are WP-2.2 / WP-2.1c -- this WP only carries the number.
     */
    val durability: Int = maxDurability,
) {
    companion object {
        /** WP-2.1b: default instance quality (mid band) for drops and un-crafted items. */
        const val DEFAULT_QUALITY: Double = 0.5

        /**
         * WP-2.1b: fallback durability ceiling used when no definition-side durability seeds an instance.
         * A placeholder in the spirit of the 0.1 power curve -- WP-2.2 tunes the real numbers alongside
         * drain rates. See `docs/design/2.1b-quality-durability.md`.
         */
        const val DEFAULT_MAX_DURABILITY: Int = 500
    }
}

data class ItemInstanceInit(
    val upgradeLevel: Int = 0,
    val reforge: ReforgeKey? = null,
    val sockets: List<SocketData> = emptyList(),
    val customRolls: Map<StatKey, Double> = emptyMap(),
    val enchantments: Map<EnchantmentKey, Int> = emptyMap(),
    val owner: UUID? = null,
    val assignInstanceId: Boolean = true,
    /** When set, deterministic seed for [ItemDefinition.statRolls]. */
    val rollSeed: Long? = null,
    /**
     * WP-2.1b: when non-null, quality DETERMINISTICALLY positions each [ItemDefinition.statRolls] roll
     * within its `[min, max]` band (`min + quality*(max-min)`) instead of the RNG/[rollSeed] path, and is
     * stored as [ItemInstanceData.quality]. Null (the default) keeps the existing RNG/seed roll and the
     * default quality -- so every current caller (loot, `/skills give`) is unchanged. Coerced into `[0,1]`.
     */
    val quality: Double? = null,
)

/**
 * WP-5.3: what [LoreSection.SetBonus] needs to render one item's set-bonus block for a viewer -- the
 * owning set's display name, its total member count, the viewer's current ACTIVE (non-inert, per
 * `SetStatProvider`'s own count) member count, and the full threshold -> effects map (only
 * [StatEffect] entries are rendered, mirroring `SetStatProvider`'s stats-only contract). Thresholds
 * render ascending; a threshold whose count is `<= activeCount` is "active" (lit), otherwise
 * "inactive" (dimmed) -- see docs/design/5.3-armor-sets.md.
 */
data class SetLoreInfo(
    val displayName: Component,
    val totalMembers: Int,
    val activeCount: Int,
    val thresholds: Map<Int, List<Effect>>,
)

data class LoreContext(
    val definition: ItemDefinition,
    val instance: ItemInstanceData,
    val viewer: Player? = null,
    val enchantNameLookup: (EnchantmentKey) -> Component? = { null },
    val statNameLookup: (StatKey) -> Component? = { null },
    val statColorLookup: (StatKey) -> TextColor? = { null },
    val statFormatLookup: (StatKey) -> StatFormat = { StatFormat.WHOLE },
    val reforgeNameLookup: (ReforgeKey) -> Component? = { null },
    val gemNameLookup: (ContentId) -> Component? = { null },
    /** WP-2.1c: display name for a [SkillKey] referenced by an [ItemRequirement.SkillLevel]. */
    val skillNameLookup: (SkillKey) -> Component? = { null },
    /**
     * WP-2.1c: the viewer's [ItemRequirementState], used by [LoreSection.Requirements] to render each
     * requirement as met/unmet. `null` (the default -- no caller populates this yet; see
     * `docs/design/2.1c-inert-items.md`'s wiring-gap note) renders every requirement as unmet, matching
     * this WP's fail-closed default elsewhere: a requirement line must never look silently satisfied just
     * because the real state wasn't wired in.
     */
    val requirementState: ItemRequirementState? = null,
    /**
     * WP-5.3: this item's set-bonus block, or `null` when it is not a set member, OR (matching the
     * WP-2.1c `requirementState` precedent above) no caller has wired live set data in yet -- rendering
     * live per-viewer set progress into `PacketItemRenderer.kt` is left to a future WP, same as
     * `requirementState`'s own wiring gap. `null` renders nothing (there is no "unmet" fail-closed
     * banner for this section -- a non-member item legitimately has nothing to show).
     */
    val setBonus: SetLoreInfo? = null,
) {
    /** Resolves a Component through viewer locale via Adventure GlobalTranslator. */
    fun localize(c: Component): Component {
        val v = viewer ?: return c
        return GlobalTranslator.render(c, v.locale())
    }
}

sealed interface LoreSection {
    fun render(ctx: LoreContext): List<Component>

    data object Blank : LoreSection { override fun render(ctx: LoreContext): List<Component> = listOf(Component.empty()) }
    data object Stats : LoreSection { override fun render(ctx: LoreContext) = LoreRender.stats(ctx) }
    data object Description : LoreSection { override fun render(ctx: LoreContext) = ctx.definition.description }
    data object Enchantments : LoreSection { override fun render(ctx: LoreContext) = LoreRender.enchants(ctx) }
    data object EffectsHint : LoreSection { override fun render(ctx: LoreContext) = LoreRender.effectsHint(ctx) }
    data object ReforgeLine : LoreSection { override fun render(ctx: LoreContext) = LoreRender.reforge(ctx) }
    data object SocketsLine : LoreSection { override fun render(ctx: LoreContext) = LoreRender.sockets(ctx) }
    /** WP-5.3: "Name (active/total)" header plus one line per set threshold (lit when active, dimmed
     *  when not), from [LoreContext.setBonus]. Renders nothing when the item is not a set member / no
     *  live set data is wired in yet. */
    data object SetBonus : LoreSection { override fun render(ctx: LoreContext) = LoreRender.setBonus(ctx) }
    /** WP-2.1c: one line per [ItemDefinition.requirements] entry (met/unmet styling), plus an inert banner. */
    data object Requirements : LoreSection { override fun render(ctx: LoreContext) = LoreRender.requirements(ctx) }
    /** WP-2.1c: the [ItemDefinition.itemLevel] line. */
    data object ItemLevel : LoreSection { override fun render(ctx: LoreContext) = LoreRender.itemLevel(ctx) }
    /** WP-2.1c: the current/max [ItemInstanceData] durability line, plus an inert banner at zero. */
    data object Durability : LoreSection { override fun render(ctx: LoreContext) = LoreRender.durability(ctx) }
    data object RarityLine : LoreSection { override fun render(ctx: LoreContext) = LoreRender.rarity(ctx) }
    data class Static(val lines: List<Component>) : LoreSection { override fun render(ctx: LoreContext) = lines }
    data class Conditional(val cond: (LoreContext) -> Boolean, val inner: LoreSection) : LoreSection {
        override fun render(ctx: LoreContext) = if (cond(ctx)) inner.render(ctx) else emptyList()
    }
}

internal object LoreRender {
    fun stats(ctx: LoreContext): List<Component> {
        val agg = HashMap<StatKey, Double>()
        for (m in ctx.definition.baseStats) agg[m.stat] = (agg[m.stat] ?: 0.0) + m.amount
        for ((k, v) in ctx.instance.customRolls) agg[k] = (agg[k] ?: 0.0) + v

        val out = ArrayList<Component>(agg.size)
        for ((stat, amt) in agg) {
            if (amt == 0.0) continue
            val name = ctx.localize(ctx.statNameLookup(stat) ?: Component.text(stat.id.value()))
            val color = ctx.statColorLookup(stat) ?: NamedTextColor.GRAY
            val sign = if (amt >= 0) "+" else ""
            val value = ctx.statFormatLookup(stat).format(amt)
            out += Component.text("")
                .append(name.color(NamedTextColor.GRAY))
                .append(Component.text(": "))
                .append(Component.text("$sign$value").color(color))
        }
        return out
    }

    fun enchants(ctx: LoreContext): List<Component> {
        if (ctx.instance.enchantments.isEmpty()) return emptyList()
        val out = ArrayList<Component>(ctx.instance.enchantments.size)
        for ((ek, lvl) in ctx.instance.enchantments) {
            val name = ctx.localize(ctx.enchantNameLookup(ek) ?: Component.text(ek.id.value()))
            out += Component.text("")
                .append(name.color(NamedTextColor.BLUE))
                .append(Component.text(" $lvl").color(NamedTextColor.BLUE))
        }
        return out
    }

    fun reforge(ctx: LoreContext): List<Component> {
        val rk = ctx.instance.reforge ?: return emptyList()
        val name = ctx.reforgeNameLookup(rk)?.let { ctx.localize(it) } ?: Component.text(rk.id.value())
        return listOf(
            Component.text("")
                .append(Component.text("Reforge: ", NamedTextColor.GRAY))
                .append(name.color(NamedTextColor.AQUA))
        )
    }

    fun sockets(ctx: LoreContext): List<Component> {
        if (ctx.instance.sockets.isEmpty()) return emptyList()
        val out = ArrayList<Component>(ctx.instance.sockets.size)
        for (s in ctx.instance.sockets) {
            val gemId = s.gem
            out += if (gemId == null) {
                Component.text("[ ]", NamedTextColor.DARK_GRAY)
            } else {
                val raw = ctx.gemNameLookup(gemId) ?: Component.text(gemId.value())
                Component.text()
                    .append(Component.text("[ ", NamedTextColor.DARK_GRAY))
                    .append(ctx.localize(raw).color(NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" ]", NamedTextColor.DARK_GRAY))
                    .build()
            }
        }
        return out
    }

    /**
     * WP-5.3: "Name (active/total)" header, then one line per threshold ascending -- GREEN/lit when
     * `count <= activeCount`, DARK_GRAY/dimmed otherwise -- followed by each threshold's [StatEffect]
     * lines (stat name + signed amount), reusing the SAME [LoreContext.statNameLookup]/[statFormatLookup]
     * the [stats] section uses. `activeCount` seeds the [ScalingContext.level] a threshold's amount
     * evaluates with, mirroring `SetStatProvider.setStatsFor`'s own scaling contract exactly.
     */
    fun setBonus(ctx: LoreContext): List<Component> {
        val info = ctx.setBonus ?: return emptyList()
        val out = ArrayList<Component>(info.thresholds.size + 1)
        out += Component.text("")
            .append(ctx.localize(Component.translatable(
                "ramrpg.set.progress",
                info.displayName,
                Component.text(info.activeCount),
                Component.text(info.totalMembers),
            )))
            .color(NamedTextColor.YELLOW)
        for (count in info.thresholds.keys.sorted()) {
            val active = count <= info.activeCount
            val color = if (active) NamedTextColor.GREEN else NamedTextColor.DARK_GRAY
            out += Component.text("")
                .append(ctx.localize(Component.translatable("ramrpg.set.threshold", Component.text(count))))
                .color(color)
            val scaleCtx = object : ScalingContext {
                override val level: Int = count
                override fun statValue(key: StatKey): Double = 0.0
                override fun extra(key: String): Double? = null
            }
            for (eff in info.thresholds.getValue(count)) {
                if (eff !is StatEffect) continue
                val name = ctx.localize(ctx.statNameLookup(eff.stat) ?: Component.text(eff.stat.id.value()))
                val amount = eff.amount.eval(scaleCtx)
                val sign = if (amount >= 0) "+" else ""
                val value = ctx.statFormatLookup(eff.stat).format(amount)
                out += Component.text("  ")
                    .append(name.color(color))
                    .append(Component.text(": "))
                    .append(Component.text("$sign$value").color(color))
                    .color(color)
            }
        }
        return out
    }

    fun effectsHint(ctx: LoreContext): List<Component> {
        if (ctx.definition.effects.isEmpty()) return emptyList()
        return ctx.definition.effects.map { eff ->
            Component.text("• ${eff.key.id.value().replace('_', ' ')}", NamedTextColor.LIGHT_PURPLE)
        }
    }

    /** WP-2.1c: one line per requirement, colored by [ItemRequirement.isMet] against [LoreContext.requirementState]. */
    fun requirements(ctx: LoreContext): List<Component> {
        val reqs = ctx.definition.requirements
        if (reqs.isEmpty()) return emptyList()
        val state = ctx.requirementState
        val out = ArrayList<Component>(reqs.size + 1)
        var anyUnmet = false
        for (req in reqs) {
            // No state wired (see LoreContext.requirementState KDoc) renders as unmet -- fail closed,
            // never a silently-satisfied requirement line.
            val met = state != null && req.isMet(state)
            if (!met) anyUnmet = true
            out += Component.text("").append(requirementLabel(ctx, req)).color(if (met) NamedTextColor.GREEN else NamedTextColor.RED)
        }
        if (anyUnmet) out += inertBanner(ctx)
        return out
    }

    private fun requirementLabel(ctx: LoreContext, req: ItemRequirement): Component = when (req) {
        is ItemRequirement.SkillLevel -> {
            val name = ctx.localize(ctx.skillNameLookup(req.skill) ?: Component.text(req.skill.id.value()))
            ctx.localize(Component.translatable("ramrpg.item.requirement.skill_level", name, Component.text(req.level)))
        }
        is ItemRequirement.StatThreshold -> {
            val name = ctx.localize(ctx.statNameLookup(req.stat) ?: Component.text(req.stat.id.value()))
            ctx.localize(Component.translatable(
                "ramrpg.item.requirement.stat_threshold",
                name,
                Component.text(ctx.statFormatLookup(req.stat).format(req.min)),
            ))
        }
        is ItemRequirement.PerkOwned ->
            ctx.localize(Component.translatable("ramrpg.item.requirement.perk_owned", Component.text(req.perk.value())))
    }

    private fun inertBanner(ctx: LoreContext): Component =
        ctx.localize(Component.translatable("ramrpg.item.inert")).color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD)

    /** WP-2.1c: the [ItemDefinition.itemLevel] line. */
    fun itemLevel(ctx: LoreContext): List<Component> = listOf(
        Component.text("")
            .append(ctx.localize(Component.translatable("ramrpg.item.level", Component.text(ctx.definition.itemLevel))))
            .color(NamedTextColor.GRAY)
    )

    /** WP-2.1c: current/max durability, plus the shared inert banner once durability hits zero. */
    fun durability(ctx: LoreContext): List<Component> {
        val cur = ctx.instance.durability
        val max = ctx.instance.maxDurability
        val depleted = cur <= 0
        val line = Component.text("")
            .append(ctx.localize(Component.translatable("ramrpg.item.durability", Component.text(cur), Component.text(max))))
            .color(if (depleted) NamedTextColor.RED else NamedTextColor.GRAY)
        return if (depleted) listOf(line, inertBanner(ctx)) else listOf(line)
    }

    fun rarity(ctx: LoreContext): List<Component> {
        return listOf(
            Component.text("")
                .append(
                    Component.text(ctx.definition.rarity.name)
                        .color(colorOf(ctx.definition.rarity))
                        .decorate(TextDecoration.BOLD)
                )
                .append(Component.text(" "))
                .append(ctx.localize(qualityBandComponent(ctx.instance.quality)).color(NamedTextColor.GRAY))
        )
    }

    /** WP-2.1c: renders the WP-2.1b quality placeholder bands (`docs/design/2.1b-quality-durability.md`). */
    private fun qualityBandComponent(quality: Double): Component = when {
        quality < 0.20 -> Component.translatable("ramrpg.item.quality.crude")
        quality < 0.40 -> Component.translatable("ramrpg.item.quality.rough")
        quality < 0.60 -> Component.translatable("ramrpg.item.quality.standard")
        quality < 0.80 -> Component.translatable("ramrpg.item.quality.fine")
        quality < 0.95 -> Component.translatable("ramrpg.item.quality.superior")
        else -> Component.translatable("ramrpg.item.quality.masterwork")
    }
}

fun colorOf(r: Rarity): NamedTextColor = when (r) {
    Rarity.COMMON -> NamedTextColor.WHITE
    Rarity.UNCOMMON -> NamedTextColor.GREEN
    Rarity.RARE -> NamedTextColor.BLUE
    Rarity.EPIC -> NamedTextColor.DARK_PURPLE
    Rarity.LEGENDARY -> NamedTextColor.GOLD
    Rarity.MYTHIC -> NamedTextColor.LIGHT_PURPLE
}

class LoreTemplate(private val sections: List<LoreSection>) {
    fun render(ctx: LoreContext): List<Component> = sections.flatMap { it.render(ctx) }
    companion object {
        val DEFAULT = LoreTemplate(listOf(
            LoreSection.Stats,
            LoreSection.ReforgeLine,
            LoreSection.SocketsLine,
            LoreSection.SetBonus,
            LoreSection.Description,
            LoreSection.Enchantments,
            LoreSection.Requirements,
            LoreSection.ItemLevel,
            LoreSection.Durability,
            LoreSection.RarityLine,
        ))
    }
}

data class StatRoll(val stat: StatKey, val min: Double, val max: Double)

data class ItemDefinition(
    val key: ItemKey,
    val displayName: Component,
    val material: Material,
    val rarity: Rarity,
    val categories: Set<ItemCategory>,
    val baseStats: List<StatModifier> = emptyList(),
    val effects: List<Effect> = emptyList(),
    val loreTemplate: LoreTemplate = LoreTemplate.DEFAULT,
    val maxStack: Int? = null,
    val customModelData: Int? = null,
    val allowVanillaWrapper: Boolean = false,
    val description: List<Component> = emptyList(),
    /** Random bonus rolls applied to ItemInstanceData.customRolls on create. */
    val statRolls: List<StatRoll> = emptyList(),
    /**
     * WP-2.1a: the player level this item is "meant for" -- a planning/lore number, not a live
     * mechanic. Fits the 0.1 power-curve bands; see `docs/design/2.1a-item-level-requirements.md`.
     */
    val itemLevel: Int = 1,
    /** WP-2.1a: gates that must be met before this item is usable. WP-2.1c enforces; definition only here. */
    val requirements: List<ItemRequirement> = emptyList(),
    /**
     * WP-2.1a: which [EquipmentSlot]s this item occupies when worn/held. Defaults from [categories] via
     * [EquipSlotDefaults]; pass explicitly to override (e.g. a trinket that occupies the off hand).
     */
    val equipSlots: Set<EquipmentSlot> = EquipSlotDefaults.forCategories(categories),
    /**
     * WP-2.3b: this weapon's declared damage-component split -- the fraction of its damage that lands
     * as each [DamageTypeKey], consumed by [dev.willram.ramrpg.builtin.stats.ElementalBreakdownStage]
     * instead of that stage's WP-2.3a all-physical default. [dev.willram.ramrpg.core.config.specs.ItemSpec]
     * validates a non-empty split sums to `1.0` (within a small epsilon) at load time -- a partial or
     * over-full split never reaches a built [ItemDefinition]. Empty (the default) means "no declared
     * split", the same as an item that never set this field at all: [ElementalBreakdownStage] falls back
     * to its existing all-physical seeding. A DEFINITION field, not an [ItemInstanceData] field -- see
     * `docs/design/2.3a-damage-types.md`'s WP-2.3b append.
     */
    val damageSplit: Map<DamageTypeKey, Double> = emptyMap(),
)

/**
 * WP-2.1c: the SINGLE inert-item check. Every item-based [dev.willram.ramrpg.api.stats.StatProvider]
 * (equipment base stats, enchantments, reforge, sockets -- and sets, once WP-5.3's `SetStatProvider`
 * lands) MUST route through this (or [isInert]) before contributing ANY stat modifier for [instance]. A
 * provider that skips this check is an exploit: it lets an under-leveled, unmet-requirement, or
 * (eventually) broken item quietly out-contribute every correctly-gated provider. See
 * `docs/design/2.1c-inert-items.md`.
 *
 * Checked in order:
 * 1. [ItemInstanceData.durability] `<= 0` -> [InertReason.ZERO_DURABILITY].
 * 2. Any [requirements] entry [isMet] `false` against [state] -> [InertReason.UNMET_REQUIREMENT].
 *
 * `null` means fully active (contributes normally).
 */
fun ItemDefinition.inertReason(instance: ItemInstanceData, state: ItemRequirementState): InertReason? {
    if (instance.durability <= 0) return InertReason.ZERO_DURABILITY
    if (requirements.any { !it.isMet(state) }) return InertReason.UNMET_REQUIREMENT
    return null
}

/** Convenience over [inertReason]: `true` iff the item is inert for any reason. */
fun ItemDefinition.isInert(instance: ItemInstanceData, state: ItemRequirementState): Boolean =
    inertReason(instance, state) != null

interface ItemDefinitionRegistry {
    fun get(key: ItemKey): ItemDefinition?
    fun register(owner: String, def: ItemDefinition)
    fun unregisterOwner(owner: String): Int
    fun all(): Collection<ItemDefinition>
    fun revision(): Int
}

interface ItemInstanceService {
    fun identify(item: ItemStack?): ItemInstanceData?
    fun create(def: ItemDefinition, init: ItemInstanceInit = ItemInstanceInit()): ItemStack
    fun read(item: ItemStack): ItemInstanceData?
    fun write(item: ItemStack, data: ItemInstanceData): ItemStack
}

/**
 * The current [ItemInstanceData] PDC schema version. WP-2.1b bumped this 1 -> 2 for the quality,
 * craftedBy and durability/maxDurability fields (the single Phase-2 instance-schema bump; see rule 5 and
 * `docs/design/2.1b-quality-durability.md`). Per the fresh-server / no-migration policy there is no
 * V1->V2 migration engine: a v1 DTO simply omits the new JSON fields and they fill from the schema
 * defaults on read (see `ItemInstanceServiceImpl.ItemDto.toDomain`).
 */
object ItemSchema { const val CURRENT = 2 }
