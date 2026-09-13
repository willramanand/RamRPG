/**
 * WP-1.5c: the `/rpg` brigadier command tree -- `validate [dir]` and `reload [--dry-run]`. Registered by
 * [dev.willram.ramrpg.core.modules.ContentModule] directly through Paper's command lifecycle event, so
 * this file (like the module that wires it) never touches `RamRPG.kt` (B5). This class owns only command
 * parsing and result formatting: the load, the diff and the registry mutation all live in
 * [dev.willram.ramrpg.core.modules.ContentModule], which is handed in as two callbacks -- the same
 * pattern `SkillsCommand` already uses for `reloadContent: () -> Unit`.
 *
 * Both literals are guarded by their own declared permission node (WP-1.5e; see `permissions:` in
 * paper-plugin.yml) rather than the ad-hoc `ramrpg.admin` string `SkillsCommand` uses: `validate` needs
 * `ramrpg.command.validate` and `reload` needs `ramrpg.command.reload`, each a child of the
 * `ramrpg.command.admin` umbrella node. The guard is NOT deferred: `/rpg validate <dir>` reads an
 * operator-supplied path and `/rpg reload` rebuilds every content registry, so neither may ship
 * reachable by an unprivileged player even transiently.
 */
package dev.willram.ramrpg.core.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.willram.ramcore.exception.ValidationError
import dev.willram.ramrpg.core.modules.ContentModule
import dev.willram.ramrpg.core.modules.ReloadOutcome
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import java.nio.file.Path

@Suppress("UnstableApiUsage")
class RpgCommand(
    private val defaultDir: Path,
    private val validate: (Path, (List<ValidationError>) -> Unit) -> Unit,
    private val reload: (Boolean, (ReloadOutcome) -> Unit) -> Unit,
) {

    fun register(commands: Commands) {
        commands.register(
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
                .build(),
            "RamRPG content validate/reload",
            emptyList(),
        )
    }

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
}
