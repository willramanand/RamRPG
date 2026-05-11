/** Brigadier root command exposing all RamRPG subcommands. */
package dev.willram.ramrpg.core.listeners

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.items.RarityRules
import dev.willram.ramrpg.api.items.ReforgeKey
import dev.willram.ramrpg.api.items.SocketData
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.XpContext
import dev.willram.ramrpg.api.skills.XpSource
import dev.willram.ramrpg.api.sockets.GemKey
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.StatDirtyReason
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.storage.PlayerStore
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

@Suppress("UnstableApiUsage")
class SkillsCommand(
    private val skills: SkillRegistry,
    private val skillService: SkillService,
    private val stats: StatService,
    private val enchants: EnchantmentRegistry,
    private val items: ItemInstanceService,
    private val itemDefs: ItemDefinitionRegistry,
    private val reforges: ReforgeRegistry,
    private val gems: GemRegistry,
    private val playerStore: PlayerStore,
) {

    fun register(commands: Commands) {
        commands.register(
            Commands.literal("skills")
                .executes(::guiCommand)
                .then(Commands.literal("gui").executes(::guiCommand))
                .then(Commands.literal("help").executes(::helpCommand))
                .then(Commands.literal("version").executes(::versionCommand))
                .then(
                    Commands.literal("reload")
                        .requires { it.sender.hasPermission("ramrpg.admin") }
                        .executes(::reloadCommand)
                )
                .then(Commands.literal("stats").executes(::statsCommand))
                .then(Commands.literal("statsgui").executes(::statsGuiCommand))
                .then(Commands.literal("quests").executes(::questsCommand))
                .then(
                    Commands.literal("quest")
                        .then(Commands.literal("abandon")
                            .then(Commands.argument("key", StringArgumentType.word())
                                .executes(::questAbandonCommand)))
                )
                .then(Commands.literal("inspect").executes(::inspectCommand))
                .then(
                    Commands.literal("upgrade")
                        .then(Commands.argument("delta", IntegerArgumentType.integer(-20, 20))
                            .executes(::upgradeCommand))
                )
                .then(
                    Commands.literal("reset")
                        .requires { it.sender.hasPermission("ramrpg.admin") }
                        .executes(::resetCommand)
                        .then(Commands.argument("target", ArgumentTypes.player())
                            .executes(::resetTargetCommand))
                )
                .then(
                    Commands.literal("give")
                        .requires { it.sender.hasPermission("ramrpg.admin") }
                        .then(Commands.argument("itemkey", StringArgumentType.word())
                            .executes { c -> giveCommand(c, 1, null, null) }
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes { c -> giveCommand(c, IntegerArgumentType.getInteger(c, "count"), null, null) }
                                .then(Commands.argument("target", ArgumentTypes.players())
                                    .executes { c -> giveCommand(c, IntegerArgumentType.getInteger(c, "count"), "target", null) }
                                    .then(Commands.argument("seed", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                        .executes { c -> giveCommand(c, IntegerArgumentType.getInteger(c, "count"), "target", com.mojang.brigadier.arguments.LongArgumentType.getLong(c, "seed")) }))))
                )
                .then(
                    Commands.literal("level")
                        .then(Commands.argument("skill", StringArgumentType.word())
                            .then(Commands.argument("n", IntegerArgumentType.integer(1, 200))
                                .executes(::levelCommand)))
                )
                .then(
                    Commands.literal("xp")
                        .then(Commands.argument("skill", StringArgumentType.word())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(::xpCommand)))
                )
                .then(
                    Commands.literal("enchant")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                                .executes(::enchantCommand)))
                )
                .then(
                    Commands.literal("reforge")
                        .then(Commands.literal("clear").executes(::reforgeClearCommand))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(::reforgeCommand))
                )
                .then(
                    Commands.literal("socket")
                        .then(Commands.literal("clear")
                            .then(Commands.argument("slot", IntegerArgumentType.integer(1, 16))
                                .executes(::socketClearCommand)))
                        .then(Commands.argument("gem", StringArgumentType.word())
                            .executes(::socketCommand))
                )
                .build(),
            "Skills root command",
            listOf("sk")
        )
    }

    private fun reforgeCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val name = StringArgumentType.getString(ctx, "name").lowercase()
        val key = ReforgeKey(ContentId.of("ramrpg", name))
        val rdef = reforges.get(key) ?: return fail(p, "Unknown reforge")
        val held = p.inventory.itemInMainHand
        val data = items.identify(held) ?: return fail(p, "Held item not registered")
        val def = itemDefs.get(data.identity.key) ?: return fail(p, "Item definition missing")
        val cost = RarityRules.reforgeXpCost(def.rarity)
        val creative = p.gameMode == org.bukkit.GameMode.CREATIVE
        if (!creative && p.level < cost) return fail(p, "Need $cost xp levels")
        if (!creative) p.level -= cost
        p.inventory.setItemInMainHand(items.write(held, data.copy(reforge = key)))
        stats.markDirty(p, StatDirtyReason.EQUIPMENT_CHANGED)
        p.sendMessage(Component.text("Applied reforge ").append(rdef.displayName))
        return Command.SINGLE_SUCCESS
    }

    private fun reforgeClearCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val held = p.inventory.itemInMainHand
        val data = items.identify(held) ?: return fail(p, "Held item not registered")
        if (data.reforge == null) return fail(p, "No reforge to clear")
        p.inventory.setItemInMainHand(items.write(held, data.copy(reforge = null)))
        stats.markDirty(p, StatDirtyReason.EQUIPMENT_CHANGED)
        p.sendMessage(Component.text("Reforge cleared"))
        return Command.SINGLE_SUCCESS
    }

    private fun socketClearCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val slot = IntegerArgumentType.getInteger(ctx, "slot")
        val held = p.inventory.itemInMainHand
        val data = items.identify(held) ?: return fail(p, "Held item not registered")
        if (slot - 1 !in data.sockets.indices) return fail(p, "Slot out of range")
        val target = data.sockets[slot - 1]
        if (target.gem == null) return fail(p, "Slot already empty")
        val newSockets = data.sockets.toMutableList()
        newSockets[slot - 1] = target.copy(gem = null)
        p.inventory.setItemInMainHand(items.write(held, data.copy(sockets = newSockets)))
        stats.markDirty(p, StatDirtyReason.EQUIPMENT_CHANGED)
        p.sendMessage(Component.text("Cleared socket $slot"))
        return Command.SINGLE_SUCCESS
    }

    private fun socketCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val name = StringArgumentType.getString(ctx, "gem").lowercase()
        val gemKey = GemKey.of("ramrpg", name)
        val gdef = gems.get(gemKey) ?: return fail(p, "Unknown gem")
        val held = p.inventory.itemInMainHand
        val data = items.identify(held) ?: return fail(p, "Held item not registered")
        val def = itemDefs.get(data.identity.key) ?: return fail(p, "Item definition missing")
        val cap = RarityRules.socketCap(def.rarity)
        if (data.sockets.size >= cap) return fail(p, "Socket cap reached ($cap)")
        val newSockets = data.sockets + SocketData(
            key = ContentId.of("ramrpg", "slot_${data.sockets.size + 1}"),
            gem = gemKey.id,
        )
        p.inventory.setItemInMainHand(items.write(held, data.copy(sockets = newSockets)))
        stats.markDirty(p, StatDirtyReason.EQUIPMENT_CHANGED)
        p.sendMessage(Component.text("Socketed ").append(gdef.displayName))
        return Command.SINGLE_SUCCESS
    }

    private fun enchantCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val name = StringArgumentType.getString(ctx, "name").lowercase()
        val lvl = IntegerArgumentType.getInteger(ctx, "level")
        val key = EnchantmentKey.of("ramrpg", name)
        val ench = enchants.get(key) ?: return fail(p, "Unknown enchant")
        val held = p.inventory.itemInMainHand
        val data = items.identify(held) ?: return fail(p, "Held item not registered")
        val newMap = data.enchantments + (key to lvl.coerceAtMost(ench.maxLevel))
        p.inventory.setItemInMainHand(items.write(held, data.copy(enchantments = newMap)))
        stats.markDirty(p, StatDirtyReason.ENCHANT_CHANGED)
        p.sendMessage(Component.text("Applied ").append(ench.displayName).append(Component.text(" $lvl")))
        return Command.SINGLE_SUCCESS
    }

    private fun guiCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        SkillsGui(p, skills, skillService, stats).open()
        return Command.SINGLE_SUCCESS
    }

    private fun helpCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        val mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
        val lines = listOf(
            "<gold>RamRPG <gray>commands:",
            "<yellow>/skills [gui]<gray> — open skills GUI",
            "<yellow>/skills stats<gray> — show stat values",
            "<yellow>/skills statsgui<gray> — open stats GUI",
            "<yellow>/skills inspect<gray> — held item details",
            "<yellow>/skills level <white><skill> <n><gray> — set skill level",
            "<yellow>/skills xp <white><skill> <amount><gray> — add xp",
            "<yellow>/skills enchant <white><name> <level><gray> — apply enchant",
            "<yellow>/skills reforge <white><name>|clear<gray> — apply or clear reforge",
            "<yellow>/skills socket <white><gem>|clear <slot><gray> — manage sockets",
            "<yellow>/skills upgrade <white><delta><gray> — bump upgrade level",
            "<dark_red>/skills give <white><itemkey> [count]<gray> — admin spawn item",
            "<dark_red>/skills reset <white>[target]<gray> — admin reset",
            "<yellow>/skills version<gray> — plugin info",
        )
        for (l in lines) sender.sendMessage(mm.deserialize(l))
        return Command.SINGLE_SUCCESS
    }

    private fun reloadCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        dev.willram.ramrpg.RamRPG.get().reloadContent()
        sender.sendMessage(Component.text("Content reloaded"))
        return Command.SINGLE_SUCCESS
    }

    private fun versionCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("RamRPG")
        val core = org.bukkit.Bukkit.getPluginManager().getPlugin("RamCore")
        val v = plugin?.pluginMeta?.version ?: "unknown"
        val cv = core?.pluginMeta?.version ?: "unknown"
        sender.sendMessage(Component.text("RamRPG $v (RamCore $cv)"))
        return Command.SINGLE_SUCCESS
    }

    private fun questAbandonCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val raw = StringArgumentType.getString(ctx, "key").lowercase()
        val cid = runCatching { if (raw.contains(':')) ContentId.parse(raw) else ContentId.of("ramrpg", raw) }
            .getOrNull() ?: return fail(p, "Bad quest id")
        val rpg = dev.willram.ramrpg.RamRPG.get()
        val changed = rpg.quests.abandon(p, dev.willram.ramrpg.api.quests.QuestKey(cid))
        p.sendMessage(Component.text(if (changed) "Quest abandoned" else "Nothing to abandon"))
        return Command.SINGLE_SUCCESS
    }

    private fun questsCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val rpg = dev.willram.ramrpg.RamRPG.get()
        QuestsGui(p, rpg.questRegistry, rpg.quests).open()
        return Command.SINGLE_SUCCESS
    }

    private fun statsGuiCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        StatsGui(p, stats).open()
        return Command.SINGLE_SUCCESS
    }

    private fun giveCommand(ctx: CommandContext<CommandSourceStack>, count: Int, targetArg: String?, seed: Long?): Int {
        val sender = ctx.source.sender
        val raw = StringArgumentType.getString(ctx, "itemkey").lowercase()
        val cid = runCatching {
            if (raw.contains(':')) ContentId.parse(raw) else ContentId.of("ramrpg", raw)
        }.getOrNull() ?: run { sender.sendMessage(Component.text("Bad item id")); return 0 }
        val def = itemDefs.get(dev.willram.ramrpg.api.identity.ItemKey(cid))
            ?: run { sender.sendMessage(Component.text("Unknown item: $cid")); return 0 }
        val targets: List<Player> = if (targetArg != null) {
            ctx.getArgument(targetArg, PlayerSelectorArgumentResolver::class.java).resolve(ctx.source)
        } else listOfNotNull(sender as? Player)
        if (targets.isEmpty()) { sender.sendMessage(Component.text("No targets")); return 0 }
        val init = dev.willram.ramrpg.api.items.ItemInstanceInit(rollSeed = seed)
        for (target in targets) {
            val stack = items.create(def, init)
            stack.amount = count
            target.inventory.addItem(stack)
        }
        sender.sendMessage(Component.text("Gave $count × ").append(def.displayName).append(Component.text(" to ${targets.size} player(s)")))
        return Command.SINGLE_SUCCESS
    }

    private fun resetCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        doReset(p)
        p.sendMessage(Component.text("Reset complete"))
        return Command.SINGLE_SUCCESS
    }

    private fun resetTargetCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver::class.java)
        val targets = resolver.resolve(ctx.source)
        for (t in targets) doReset(t)
        ctx.source.sender.sendMessage(Component.text("Reset ${targets.size} player(s)"))
        return Command.SINGLE_SUCCESS
    }

    private fun doReset(p: Player) {
        val data = playerStore.require(p.uniqueId)
        data.skillLevels.clear()
        data.skillXp.clear()
        data.currentMana = -1.0
        data.lastActiveSkillId = null
        data.markDirty()
        stats.markDirty(p, StatDirtyReason.SKILL_LEVEL_CHANGED)
    }

    private fun upgradeCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val delta = IntegerArgumentType.getInteger(ctx, "delta")
        val held = p.inventory.itemInMainHand
        val data = items.identify(held) ?: return fail(p, "Held item not registered")
        val def = itemDefs.get(data.identity.key) ?: return fail(p, "Definition missing")
        val cap = RarityRules.upgradeCap(def.rarity)
        val newLvl = (data.upgradeLevel + delta).coerceIn(0, cap)
        if (newLvl == data.upgradeLevel) return fail(p, "At cap or floor (max $cap)")
        val creative = p.gameMode == org.bukkit.GameMode.CREATIVE
        if (delta > 0 && !creative) {
            val totalCost = RarityRules.upgradeXpCost(data.upgradeLevel, newLvl, def.rarity)
            if (p.level < totalCost) return fail(p, "Need $totalCost xp levels")
            p.level -= totalCost
            p.sendMessage(Component.text("Spent $totalCost xp"))
        }
        p.inventory.setItemInMainHand(items.write(held, data.copy(upgradeLevel = newLvl)))
        stats.markDirty(p, StatDirtyReason.EQUIPMENT_CHANGED)
        p.sendMessage(Component.text("Upgrade: +$newLvl / +$cap"))
        return Command.SINGLE_SUCCESS
    }

    private fun inspectCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val held = p.inventory.itemInMainHand
        val data = items.identify(held) ?: return fail(p, "Held item not registered")
        val def = itemDefs.get(data.identity.key) ?: return fail(p, "Definition missing")
        val sb = StringBuilder()
        sb.append("=== ").append(def.key.id).append(" (").append(def.rarity).append(") ===\n")
        sb.append("Material: ").append(def.material).append('\n')
        sb.append("Categories: ").append(def.categories.joinToString()).append('\n')
        if (data.upgradeLevel > 0) sb.append("Upgrade: +").append(data.upgradeLevel).append('\n')
        data.reforge?.let { sb.append("Reforge: ").append(it.id).append('\n') }
        if (data.sockets.isNotEmpty()) {
            sb.append("Sockets:\n")
            for (s in data.sockets) sb.append("  ").append(s.key).append(" → ").append(s.gem ?: "(empty)").append('\n')
        }
        if (data.enchantments.isNotEmpty()) {
            sb.append("Enchants:\n")
            for ((k, v) in data.enchantments) sb.append("  ").append(k.id).append(" lvl ").append(v).append('\n')
        }
        if (data.customRolls.isNotEmpty()) {
            sb.append("Rolls:\n")
            for ((k, v) in data.customRolls) sb.append("  ").append(k.id).append(" = ").append(v).append('\n')
        }
        if (def.baseStats.isNotEmpty()) {
            sb.append("Base stats:\n")
            for (m in def.baseStats) sb.append("  ").append(m.stat.id).append(" ").append(m.operation).append(" ").append(m.amount).append('\n')
        }
        data.customName?.let { sb.append("Custom name: ").append(it).append('\n') }
        p.sendMessage(Component.text(sb.toString()))
        return Command.SINGLE_SUCCESS
    }

    private fun statsCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val snap = stats.snapshot(p)
        val msg = StringBuilder("=== Stats ===\n")
        for (def in stats.definitions().sortedBy { it.key.id.toString() }) {
            msg.append("${def.key.id.value()}: ${"%.1f".format(snap[def.key])}\n")
        }
        p.sendMessage(Component.text(msg.toString()))
        return Command.SINGLE_SUCCESS
    }

    private fun levelCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val skillStr = StringArgumentType.getString(ctx, "skill")
        val n = IntegerArgumentType.getInteger(ctx, "n")
        val key = resolve(skillStr) ?: return fail(p, "Unknown skill")
        skillService.setLevel(p, key, n)
        stats.markDirty(p, StatDirtyReason.SKILL_LEVEL_CHANGED)
        dev.willram.ramrpg.RamRPG.get().refreshAttributes(p)
        p.sendMessage(Component.text("Set ${key.id.value()} to $n"))
        return Command.SINGLE_SUCCESS
    }

    private fun xpCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val skillStr = StringArgumentType.getString(ctx, "skill")
        val amount = IntegerArgumentType.getInteger(ctx, "amount")
        val resolved = resolve(skillStr) ?: return fail(p, "Unknown skill")
        val src = object : XpSource {
            override val key = XpSourceKey.of("ramrpg", "command")
            override val skill: SkillKey = resolved
            override fun xp(c: XpContext): Double = amount.toDouble()
        }
        skillService.addXp(p, src)
        p.sendMessage(Component.text("Added $amount xp to ${resolved.id.value()}"))
        return Command.SINGLE_SUCCESS
    }

    private fun resolve(name: String): SkillKey? {
        val lower = name.lowercase()
        val direct = SkillKey(ContentId.of("ramrpg", lower))
        if (skills.get(direct) != null) return direct
        return skills.all().firstOrNull { it.key.id.value().equals(lower, ignoreCase = true) }?.key
    }

    private fun fail(p: Player, msg: String): Int {
        p.sendMessage(Component.text(msg))
        return 0
    }
}
