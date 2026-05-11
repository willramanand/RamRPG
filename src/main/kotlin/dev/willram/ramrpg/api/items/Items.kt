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
import org.bukkit.inventory.ItemStack
import java.util.UUID

enum class Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC }

enum class ItemCategory {
    SWORD, AXE, PICKAXE, SHOVEL, HOE, BOW, CROSSBOW, TRIDENT, MACE,
    FISHING_ROD, ELYTRA, HELMET, CHESTPLATE, LEGGINGS, BOOTS,
    SHIELD, ENCHANTED_BOOK, MISC
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
