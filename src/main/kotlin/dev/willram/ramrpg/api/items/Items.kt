/**
 * Item definition + per-stack instance data. [ItemDefinition] is the static
 * blueprint; [ItemInstanceData] is the per-stack mutable state (enchants,
 * sockets, reforge, custom rolls). [LoreTemplate] composes packet-rendered
 * lore from [LoreSection]s; rendering never mutates the canonical PDC item.
 */
package dev.willram.ramrpg.api.items

import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.effects.Effect
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
)

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

    fun effectsHint(ctx: LoreContext): List<Component> {
        if (ctx.definition.effects.isEmpty()) return emptyList()
        return ctx.definition.effects.map { eff ->
            Component.text("• ${eff.key.id.value().replace('_', ' ')}", NamedTextColor.LIGHT_PURPLE)
        }
    }

    fun rarity(ctx: LoreContext): List<Component> {
        return listOf(
            Component.text(ctx.definition.rarity.name)
                .color(colorOf(ctx.definition.rarity))
                .decorate(TextDecoration.BOLD)
        )
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
            LoreSection.Description,
            LoreSection.Enchantments,
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
)

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

object ItemSchema { const val CURRENT = 1 }
