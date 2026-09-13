/**
 * WP-3.0: the single canonical `/rpg` Brigadier tree, AND the sole owner of the `/skills` deprecation
 * alias. Before this WP, game-command logic (skills GUI, stats, quests, admin give/level/xp/enchant/
 * reset, ability toggles, reforge/socket/upgrade) lived in [dev.willram.ramrpg.core.listeners.SkillsCommand]
 * behind its own `literal("skills")` root, while this class only carried `/rpg validate` and
 * `/rpg reload`. That left two command roots and meant every future station WP (smithing, enchanting)
 * would have had to edit a command file to keep `/skills upgrade`/`reforge`/`socket` working. WP-3.0
 * moves ALL game-command logic here, so there is exactly ONE `literal("rpg")` root in the whole plugin,
 * and turns `/skills` into a thin aliasing shim registered by the SAME class (`buildSkillsAlias`) --
 * see [dev.willram.ramrpg.core.listeners.SkillsCommand]'s KDoc for why that class still exists.
 *
 * Registered by [dev.willram.ramrpg.core.modules.ContentModule] directly through Paper's command
 * lifecycle event, so this file (like the module that wires it) never touches `RamRPG.kt` (B5). This
 * class owns command parsing and result formatting only: content load/diff/registry mutation for
 * validate/reload still live in [dev.willram.ramrpg.core.modules.ContentModule], handed in as two
 * callbacks -- unchanged from before WP-3.0.
 *
 * `/rpg validate` and `/rpg reload` keep their own declared permission nodes (WP-1.5e;
 * `ramrpg.command.validate` / `ramrpg.command.reload`, children of `ramrpg.command.admin`). The new
 * `/rpg admin` subtree (give/level/xp/enchant/reset) reuses the existing `ramrpg.admin` node -- the same
 * one the old `/skills reload|reset|give` used -- guarded ONCE at the `admin` literal so the whole
 * subtree inherits it, which also closes a pre-existing gap where `/skills level|xp|enchant` had no
 * permission guard at all (see docs/design/3.0-command-surface.md).
 *
 * `/skills` is registered as a SEPARATE literal root (not a true Brigadier `.redirect()`) so every leaf
 * can emit the WP-3.0 deprecation warning naming its new `/rpg` path before doing anything else. Every
 * leaf that is STILL a live player feature under `/rpg` -- bare, `gui`, `help`, `version`, `stats`,
 * `quests`, `statsgui`, `inspect`, `quest abandon <key>`, `ability list`, `ability toggle <name>` --
 * warns AND forwards to the exact same handler its `/rpg` equivalent calls (adversarial review of the
 * initial WP-3.0 commit flagged the first cut's `statsgui`/`inspect`/`quest abandon`/`ability` as
 * warn-only dead signposts for features that hadn't actually moved anywhere -- fixed). Only leaves that
 * were truly STRIPPED, RENAMED or REGROUPED (`reload` -- superseded by the diff-based `/rpg reload`;
 * the station stubs `upgrade`/`reforge`/`socket`; the admin regroup `reset`/`give`/`level`/`xp`/`enchant`)
 * warn ONLY, since there is no unchanged behavior left to forward to. The full old->new mapping, and
 * which rows forward vs. warn-only, is [CommandAliasMap.OLD_TO_NEW] (see its KDoc), asserted by
 * `CommandAliasMappingTest` as pure data (no Brigadier dispatch -- BLOCKED on WP-RC2's
 * CommandTestHarness, same constraint noted on
 * `ValidateResultFormattingTest`).
 */
package dev.willram.ramrpg.core.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import dev.willram.ramcore.content.ContentId
import dev.willram.ramcore.exception.ValidationError
import dev.willram.ramrpg.api.abilities.AbilityRegistry
import dev.willram.ramrpg.api.abilities.AbilityService
import dev.willram.ramrpg.api.enchants.EnchantmentRegistry
import dev.willram.ramrpg.api.identity.AbilityKey
import dev.willram.ramrpg.api.identity.EnchantmentKey
import dev.willram.ramrpg.api.identity.ItemKey
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.identity.XpSourceKey
import dev.willram.ramrpg.api.items.ItemDefinitionRegistry
import dev.willram.ramrpg.api.items.ItemInstanceInit
import dev.willram.ramrpg.api.items.ItemInstanceService
import dev.willram.ramrpg.api.quests.QuestKey
import dev.willram.ramrpg.api.quests.QuestRegistry
import dev.willram.ramrpg.api.reforges.ReforgeRegistry
import dev.willram.ramrpg.api.skills.SkillRegistry
import dev.willram.ramrpg.api.skills.SkillService
import dev.willram.ramrpg.api.skills.XpContext
import dev.willram.ramrpg.api.skills.XpSource
import dev.willram.ramrpg.api.sockets.GemRegistry
import dev.willram.ramrpg.api.stats.StatDirtyReason
import dev.willram.ramrpg.api.stats.StatService
import dev.willram.ramrpg.core.listeners.QuestsGui
import dev.willram.ramrpg.core.listeners.SkillsGui
import dev.willram.ramrpg.core.listeners.StatsGui
import dev.willram.ramrpg.core.listeners.applyPlayerAttributes
import dev.willram.ramrpg.core.modules.ContentModule
import dev.willram.ramrpg.core.modules.ReloadOutcome
import dev.willram.ramrpg.core.services.QuestService
import dev.willram.ramrpg.core.storage.PlayerStore
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.nio.file.Path

@Suppress("UnstableApiUsage")
class RpgCommand(
    private val defaultDir: Path,
    private val validate: (Path, (List<ValidationError>) -> Unit) -> Unit,
    private val reload: (Boolean, (ReloadOutcome) -> Unit) -> Unit,
    private val skills: SkillRegistry,
    private val skillService: SkillService,
    private val stats: StatService,
    private val enchants: EnchantmentRegistry,
    private val items: ItemInstanceService,
    private val itemDefs: ItemDefinitionRegistry,
    @Suppress("unused") private val reforges: ReforgeRegistry,
    @Suppress("unused") private val gems: GemRegistry,
    private val playerStore: PlayerStore,
    private val abilities: AbilityRegistry,
    private val abilityService: AbilityService,
    private val quests: QuestService,
    private val questRegistry: QuestRegistry,
) {

    fun register(commands: Commands) {
        commands.register(buildRpgRoot(), "RamRPG command tree", emptyList())
        commands.register(buildSkillsAlias(), "Deprecated alias for /rpg (WP-3.0)", listOf("sk"))
    }

    // ---------------------------------------------------------------------------------------------
    // /rpg -- the canonical tree
    // ---------------------------------------------------------------------------------------------

    private fun buildRpgRoot(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("rpg")
            .then(
                Commands.literal("validate")
                    // Gates the whole subtree incl. the arbitrary-path `dir` arg below.
                    .requires { it.sender.hasPermission("ramrpg.command.validate") }
                    .executes { ctx -> runValidate(ctx, defaultDir) }
                    .then(
                        Commands.argument("dir", StringArgumentType.greedyString())
                            .executes { ctx ->
                                runValidate(ctx, Path.of(StringArgumentType.getString(ctx, "dir")))
                            }
                    )
            )
            .then(
                Commands.literal("reload")
                    .requires { it.sender.hasPermission("ramrpg.command.reload") }
                    .executes { ctx -> runReload(ctx, dryRun = false) }
                    .then(Commands.literal("--dry-run").executes { ctx -> runReload(ctx, dryRun = true) })
            )
            .then(
                Commands.literal("stats")
                    .executes(::statsCommand)
                    .then(Commands.literal("gui").executes(::statsGuiCommand))
            )
            .then(
                Commands.literal("skills")
                    .executes(::guiCommand)
                    .then(Commands.literal("gui").executes(::guiCommand))
                    .then(Commands.literal("inspect").executes(::inspectCommand))
                    .then(
                        Commands.literal("ability")
                            .then(Commands.literal("list").executes(::abilityListCommand))
                            .then(
                                Commands.literal("toggle")
                                    .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(::abilityToggleCommand))
                            )
                    )
            )
            .then(
                Commands.literal("quests")
                    .executes(::questsCommand)
                    .then(
                        Commands.literal("abandon")
                            .then(Commands.argument("key", StringArgumentType.word())
                                .executes(::questAbandonCommand))
                    )
            )
            .then(Commands.literal("perks").executes(::perksStubCommand))
            .then(
                Commands.literal("admin")
                    // Single guard for the whole subtree (give/level/xp/enchant/reset): reuses the
                    // existing ramrpg.admin node the old /skills reload|reset|give used. Also closes a
                    // pre-existing gap where /skills level|xp|enchant had no guard at all.
                    .requires { it.sender.hasPermission("ramrpg.admin") }
                    .then(
                        Commands.literal("give")
                            .then(Commands.argument("itemkey", StringArgumentType.word())
                                .executes { c -> giveCommand(c, 1, null, null) }
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                    .executes { c -> giveCommand(c, IntegerArgumentType.getInteger(c, "count"), null, null) }
                                    .then(Commands.argument("target", ArgumentTypes.players())
                                        .executes { c -> giveCommand(c, IntegerArgumentType.getInteger(c, "count"), "target", null) }
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                            .executes { c -> giveCommand(c, IntegerArgumentType.getInteger(c, "count"), "target", LongArgumentType.getLong(c, "seed")) }))))
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
                        Commands.literal("reset")
                            .executes(::resetCommand)
                            .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(::resetTargetCommand))
                    )
            )
            .then(Commands.literal("help").executes(::helpCommand))
            .then(Commands.literal("version").executes(::versionCommand))
            // Station-bound subcommands: stripped of all logic per WP-3.0 so later Phase-3 station WPs
            // (smithing 3.1b/3.3, enchanting 3.4b) never need to edit a command file. Deprecated no-op
            // stubs -- each just points the player at the station.
            .then(
                Commands.literal("upgrade")
                    .executes(::upgradeStubCommand)
                    .then(Commands.argument("args", StringArgumentType.greedyString()).executes(::upgradeStubCommand))
            )
            .then(
                Commands.literal("reforge")
                    .executes(::reforgeStubCommand)
                    .then(Commands.argument("args", StringArgumentType.greedyString()).executes(::reforgeStubCommand))
            )
            .then(
                Commands.literal("socket")
                    .executes(::socketStubCommand)
                    .then(Commands.argument("args", StringArgumentType.greedyString()).executes(::socketStubCommand))
            )
            .build()

    private fun runValidate(ctx: CommandContext<CommandSourceStack>, dir: Path): Int {
        val sender = ctx.source.sender
        validate(dir) { errors ->
            if (errors.isEmpty()) {
                sender.sendMessage(Component.translatable("ramrpg.rpg.validate.clean", Component.text(dir.toString())))
            } else {
                sender.sendMessage(
                    Component.translatable(
                        "ramrpg.rpg.validate.errors",
                        Component.text(errors.size),
                        Component.text(dir.toString()),
                    )
                )
                for (error in errors) sender.sendMessage(Component.text(ContentModule.formatValidationError(error)))
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun runReload(ctx: CommandContext<CommandSourceStack>, dryRun: Boolean): Int {
        val sender = ctx.source.sender
        reload(dryRun) { outcome ->
            val diff = outcome.diff
            val added = Component.text(diff.added().size)
            val changed = Component.text(diff.changed().size)
            val removed = Component.text(diff.removed().size)
            val broken = Component.text(diff.brokenReferences().size)
            val summary = if (dryRun) {
                Component.translatable("ramrpg.rpg.reload.dry_run", added, changed, removed, broken)
            } else {
                Component.translatable("ramrpg.rpg.reload.applied", added, changed, removed, broken)
            }
            sender.sendMessage(summary)

            if (outcome.statsOrphaned > 0) {
                sender.sendMessage(
                    Component.translatable("ramrpg.rpg.reload.stats_orphaned", Component.text(outcome.statsOrphaned))
                )
            }

            val errors = outcome.loadErrors + outcome.registrationErrors
            if (errors.isNotEmpty()) {
                val errorHeader = if (outcome.applied) {
                    Component.translatable("ramrpg.rpg.reload.errors", Component.text(errors.size))
                } else {
                    Component.translatable("ramrpg.rpg.reload.errors_dry_run", Component.text(errors.size))
                }
                sender.sendMessage(errorHeader)
                for (error in errors) sender.sendMessage(Component.text(ContentModule.formatValidationError(error)))
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun guiCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        SkillsGui.open(p, skills, skillService)
        return Command.SINGLE_SUCCESS
    }

    private fun helpCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        val mm = MiniMessage.miniMessage()
        val lines = listOf(
            "<gold>RamRPG <gray>commands:",
            "<yellow>/rpg stats<gray> — show stat values",
            "<yellow>/rpg stats gui<gray> — open stats GUI",
            "<yellow>/rpg skills [gui]<gray> — open skills GUI",
            "<yellow>/rpg skills inspect<gray> — held item details",
            "<yellow>/rpg skills ability list|toggle <white><name><gray> — manage abilities",
            "<yellow>/rpg quests<gray> — open quests GUI",
            "<yellow>/rpg quests abandon <white><id><gray> — abandon a quest",
            "<yellow>/rpg perks<gray> — perk trees (coming soon)",
            "<yellow>/rpg upgrade|reforge|socket<gray> — visit a smithing station",
            "<dark_red>/rpg admin give <white><itemkey> [count]<gray> — admin spawn item",
            "<dark_red>/rpg admin level|xp <white><skill> <n><gray> — admin set/add skill progress",
            "<dark_red>/rpg admin enchant <white><name> <level><gray> — admin apply enchant",
            "<dark_red>/rpg admin reset <white>[target]<gray> — admin reset",
            "<dark_red>/rpg reload [--dry-run]<gray> — reload content",
            "<dark_red>/rpg validate <white>[dir]<gray> — validate content",
            "<yellow>/rpg version<gray> — plugin info",
        )
        for (l in lines) sender.sendMessage(mm.deserialize(l))
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
        val changed = quests.abandon(p, QuestKey(cid))
        p.sendMessage(Component.text(if (changed) "Quest abandoned" else "Nothing to abandon"))
        return Command.SINGLE_SUCCESS
    }

    private fun questsCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        QuestsGui.open(p, questRegistry, quests)
        return Command.SINGLE_SUCCESS
    }

    private fun statsGuiCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        StatsGui.open(p, stats)
        return Command.SINGLE_SUCCESS
    }

    private fun giveCommand(ctx: CommandContext<CommandSourceStack>, count: Int, targetArg: String?, seed: Long?): Int {
        val sender = ctx.source.sender
        val raw = StringArgumentType.getString(ctx, "itemkey").lowercase()
        val cid = runCatching {
            if (raw.contains(':')) ContentId.parse(raw) else ContentId.of("ramrpg", raw)
        }.getOrNull() ?: run { sender.sendMessage(Component.text("Bad item id")); return 0 }
        val def = itemDefs.get(ItemKey(cid))
            ?: run { sender.sendMessage(Component.text("Unknown item: $cid")); return 0 }
        val targets: List<Player> = if (targetArg != null) {
            ctx.getArgument(targetArg, PlayerSelectorArgumentResolver::class.java).resolve(ctx.source)
        } else listOfNotNull(sender as? Player)
        if (targets.isEmpty()) { sender.sendMessage(Component.text("No targets")); return 0 }
        val init = ItemInstanceInit(rollSeed = seed)
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
        applyPlayerAttributes(stats, p)
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
            override fun xp(ctx: XpContext): Double = amount.toDouble()
        }
        skillService.addXp(p, src)
        p.sendMessage(Component.text("Added $amount xp to ${resolved.id.value()}"))
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

    private fun abilityListCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val all = abilities.all()
        if (all.isEmpty()) { p.sendMessage(Component.text("No abilities registered")); return Command.SINGLE_SUCCESS }
        p.sendMessage(Component.text("Abilities:"))
        for (ab in all) {
            val unlock = ab.unlockSkill?.let { sk ->
                val have = skillService.level(p, sk)
                val ok = have >= ab.unlockLevel
                " [${sk.id.value()} ${ab.unlockLevel} ${if (ok) "✓" else "$have"}]"
            } ?: ""
            val state = if (abilityService.isDisabled(p, ab.key)) "OFF" else "ON"
            p.sendMessage(Component.text("- ${ab.key.id.value()} $state$unlock"))
        }
        return Command.SINGLE_SUCCESS
    }

    private fun abilityToggleCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val p = ctx.source.sender as? Player ?: return 0
        val name = StringArgumentType.getString(ctx, "name").lowercase()
        val key = AbilityKey(ContentId.of("ramrpg", name))
        val ab = abilities.get(key) ?: return fail(p, "Unknown ability: $name")
        val nowDisabled = !abilityService.isDisabled(p, ab.key)
        abilityService.setDisabled(p, ab.key, nowDisabled)
        p.sendMessage(Component.text("Ability ${ab.key.id.value()} ${if (nowDisabled) "disabled" else "enabled"}"))
        return Command.SINGLE_SUCCESS
    }

    /**
     * Deprecated no-op stub (WP-3.0): `upgrade`/`reforge`/`socket` are station-bound and carry NO logic
     * here -- see the class KDoc. Smithing (3.1b/3.3) replaces this with a real recipe-based flow.
     */
    private fun upgradeStubCommand(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.source.sender.sendMessage(Component.translatable("ramrpg.rpg.upgrade.stub"))
        return Command.SINGLE_SUCCESS
    }

    /** Deprecated no-op stub (WP-3.0) -- see [upgradeStubCommand]. */
    private fun reforgeStubCommand(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.source.sender.sendMessage(Component.translatable("ramrpg.rpg.reforge.stub"))
        return Command.SINGLE_SUCCESS
    }

    /** Deprecated no-op stub (WP-3.0) -- see [upgradeStubCommand]. */
    private fun socketStubCommand(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.source.sender.sendMessage(Component.translatable("ramrpg.rpg.socket.stub"))
        return Command.SINGLE_SUCCESS
    }

    private fun perksStubCommand(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.source.sender.sendMessage(Component.translatable("ramrpg.rpg.perks.stub"))
        return Command.SINGLE_SUCCESS
    }

    // ---------------------------------------------------------------------------------------------
    // /skills -- deprecated alias (one release window; see CommandAliasMap)
    // ---------------------------------------------------------------------------------------------

    private fun buildSkillsAlias(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("skills")
            .executes { ctx -> deprecated(ctx, "skills"); guiCommand(ctx) }
            .then(Commands.literal("gui").executes { ctx -> deprecated(ctx, "skills gui"); guiCommand(ctx) })
            .then(Commands.literal("help").executes { ctx -> deprecated(ctx, "skills help"); helpCommand(ctx) })
            .then(Commands.literal("version").executes { ctx -> deprecated(ctx, "skills version"); versionCommand(ctx) })
            .then(Commands.literal("stats").executes { ctx -> deprecated(ctx, "skills stats"); statsCommand(ctx) })
            .then(Commands.literal("quests").executes { ctx -> deprecated(ctx, "skills quests"); questsCommand(ctx) })
            .then(
                Commands.literal("reload")
                    .requires { it.sender.hasPermission("ramrpg.admin") }
                    .executes { ctx -> deprecated(ctx, "skills reload") }
            )
            .then(Commands.literal("statsgui").executes { ctx -> deprecated(ctx, "skills statsgui"); statsGuiCommand(ctx) })
            .then(
                // Bare "quest"/"abandon" (no key) never had a real handler on the old tree either --
                // warn only. The key-bearing leaf mirrors /rpg quests abandon <key>'s exact argument
                // shape (name "key", word()) and forwards, per WP-3.0's FIX 1: this is a still-live
                // player feature, not a stripped/regrouped one.
                Commands.literal("quest")
                    .executes { ctx -> deprecated(ctx, "skills quest abandon") }
                    .then(
                        Commands.literal("abandon")
                            .executes { ctx -> deprecated(ctx, "skills quest abandon") }
                            .then(Commands.argument("key", StringArgumentType.word())
                                .executes { ctx -> deprecated(ctx, "skills quest abandon"); questAbandonCommand(ctx) })
                    )
            )
            .then(Commands.literal("inspect").executes { ctx -> deprecated(ctx, "skills inspect"); inspectCommand(ctx) })
            .then(
                Commands.literal("upgrade")
                    .executes { ctx -> deprecated(ctx, "skills upgrade") }
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes { ctx -> deprecated(ctx, "skills upgrade") })
            )
            .then(
                Commands.literal("reset")
                    .requires { it.sender.hasPermission("ramrpg.admin") }
                    .executes { ctx -> deprecated(ctx, "skills reset") }
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes { ctx -> deprecated(ctx, "skills reset") })
            )
            .then(
                Commands.literal("give")
                    .requires { it.sender.hasPermission("ramrpg.admin") }
                    .executes { ctx -> deprecated(ctx, "skills give") }
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes { ctx -> deprecated(ctx, "skills give") })
            )
            .then(
                Commands.literal("level")
                    .executes { ctx -> deprecated(ctx, "skills level") }
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes { ctx -> deprecated(ctx, "skills level") })
            )
            .then(
                Commands.literal("xp")
                    .executes { ctx -> deprecated(ctx, "skills xp") }
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes { ctx -> deprecated(ctx, "skills xp") })
            )
            .then(
                Commands.literal("enchant")
                    .executes { ctx -> deprecated(ctx, "skills enchant") }
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes { ctx -> deprecated(ctx, "skills enchant") })
            )
            .then(
                Commands.literal("reforge")
                    .executes { ctx -> deprecated(ctx, "skills reforge") }
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes { ctx -> deprecated(ctx, "skills reforge") })
            )
            .then(
                // "ability" bare has no old-tree handler to forward to (list/toggle are the leaves) --
                // warn only. list/toggle mirror /rpg skills ability list|toggle's exact shapes (toggle's
                // argument name "name", word()) and forward: still-live player features (FIX 1).
                Commands.literal("ability")
                    .executes { ctx -> deprecated(ctx, "skills ability") }
                    .then(Commands.literal("list").executes { ctx -> deprecated(ctx, "skills ability list"); abilityListCommand(ctx) })
                    .then(
                        Commands.literal("toggle")
                            .executes { ctx -> deprecated(ctx, "skills ability toggle") }
                            .then(Commands.argument("name", StringArgumentType.word())
                                .executes { ctx -> deprecated(ctx, "skills ability toggle"); abilityToggleCommand(ctx) })
                    )
            )
            .then(
                Commands.literal("socket")
                    .executes { ctx -> deprecated(ctx, "skills socket") }
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes { ctx -> deprecated(ctx, "skills socket") })
            )
            .build()

    /** Sends the WP-3.0 deprecation warning naming [oldPath]'s documented new path, per [CommandAliasMap]. */
    private fun deprecated(ctx: CommandContext<CommandSourceStack>, oldPath: String): Int {
        val newPath = CommandAliasMap.OLD_TO_NEW.getValue(oldPath)
        ctx.source.sender.sendMessage(Component.translatable("ramrpg.rpg.skills_deprecated", Component.text(newPath)))
        return Command.SINGLE_SUCCESS
    }
}

/**
 * The complete `/skills` (old) -> `/rpg` (new) command path mapping for WP-3.0, expressed as pure data so
 * `CommandAliasMappingTest` can assert it without dispatching Brigadier (no Bukkit statics, no live
 * server -- see [RpgCommand]'s KDoc). [RpgCommand.deprecated] looks up this SAME map when building the
 * `/skills` alias tree, so the map's new-path STRINGS can never drift from what the alias actually tells
 * a player.
 *
 * Paths whose old and new names/locations are identical (`stats`, `quests`, bare `skills`/`gui`, `help`,
 * `version`) still appear here even though they keep working -- the alias warns on every leaf, not just
 * the ones that moved. Nested old leaves that collapsed into one warning under a shared parent with no
 * argument-bearing forward target (`reforge clear`/`reforge <name>` both warn-only under `reforge`;
 * `socket clear`/`socket <gem>` both warn-only under `socket`) are still listed individually so every
 * stripped/renamed/regrouped path a player could have typed is documented -- both map to the SAME value
 * as their parent since `/rpg reforge`/`/rpg socket` are themselves stubs with no sub-form, so there is
 * no drift risk from having two keys share one value.
 *
 * [FORWARDED] is the second half of that fidelity guarantee: it says WHICH keys the alias tree actually
 * warns-AND-forwards for (still-live player features) versus warns-only for (truly stripped, renamed or
 * regrouped, per the WP-3.0 contract -- see [RpgCommand]'s KDoc). It has to be hand-kept in sync with
 * `RpgCommand.buildSkillsAlias()` (there is no live dispatch to derive it from off-server), but
 * `CommandAliasMappingTest` at least pins its exact contents so a future edit to one without the other
 * fails the build instead of silently drifting.
 */
object CommandAliasMap {
    val OLD_TO_NEW: Map<String, String> = mapOf(
        "skills" to "rpg skills",
        "skills gui" to "rpg skills gui",
        "skills help" to "rpg help",
        "skills version" to "rpg version",
        "skills stats" to "rpg stats",
        "skills quests" to "rpg quests",
        "skills reload" to "rpg reload",
        "skills statsgui" to "rpg stats gui",
        "skills quest abandon" to "rpg quests abandon",
        "skills inspect" to "rpg skills inspect",
        "skills upgrade" to "rpg upgrade",
        "skills reset" to "rpg admin reset",
        "skills give" to "rpg admin give",
        "skills level" to "rpg admin level",
        "skills xp" to "rpg admin xp",
        "skills enchant" to "rpg admin enchant",
        "skills reforge" to "rpg reforge",
        "skills reforge clear" to "rpg reforge",
        "skills ability" to "rpg skills ability",
        "skills ability list" to "rpg skills ability list",
        "skills ability toggle" to "rpg skills ability toggle",
        "skills socket" to "rpg socket",
        "skills socket clear" to "rpg socket",
    )

    /**
     * Keys of [OLD_TO_NEW] whose alias leaf warns AND forwards to the live `/rpg` handler, because the
     * underlying player feature still exists unchanged -- everything else in [OLD_TO_NEW] is warn-only.
     * See the class KDoc.
     */
    val FORWARDED: Set<String> = setOf(
        "skills",
        "skills gui",
        "skills help",
        "skills version",
        "skills stats",
        "skills quests",
        "skills statsgui",
        "skills inspect",
        "skills quest abandon",
        "skills ability list",
        "skills ability toggle",
    )
}
