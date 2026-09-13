package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramrpg.api.identity.EffectKey
import dev.willram.ramrpg.core.config.RpgContentLoader
import dev.willram.ramrpg.core.config.specs.EffectSpec
import dev.willram.ramrpg.core.effects.BlockMatcherRegistry
import dev.willram.ramrpg.core.effects.BuiltinEffectActions
import dev.willram.ramrpg.core.effects.EffectActionRegistry
import dev.willram.ramrpg.core.effects.EffectConditionRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import java.io.BufferedReader
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path

/**
 * WP-1.5b: an unknown effect CONDITION id (in a [dev.willram.ramrpg.api.effects.TriggeredEffect]'s
 * `conditions`) NAMES the id and AGGREGATES, exactly like the action case -- resolution throws a
 * [ContentDeserializeException], and through the loader it becomes one source-tagged error rather than
 * a crash. The action here is valid, isolating the condition as the failure.
 */
class EffectConditionRegistryUnknownIdTest {

    private fun registries() = EffectSpec.Registries(
        EffectActionRegistry(), EffectConditionRegistry(), BlockMatcherRegistry(),
    ).also { BuiltinEffectActions.registerAll(it.actions, it.conditions, it.matchers, RecordingScheduler()) }

    @Test
    fun `resolving an unknown condition id throws naming the id`() {
        val node = HoconConfigurationLoader.builder()
            .source {
                BufferedReader(
                    StringReader(
                        """
                        type = triggered
                        trigger = "on_hit"
                        conditions = ["ramrpg:no_such_condition"]
                        action = "ramrpg:message"
                        params { text = "hi" }
                        """.trimIndent(),
                    ),
                )
            }
            .build()
            .load()

        val ex = assertThrows(ContentDeserializeException::class.java) {
            EffectSpec.one(node, EffectKey.of("ramrpg", "fx"), registries())
        }
        assertTrue(ex.message!!.contains("ramrpg:no_such_condition"), "names the id: ${ex.message}")
        assertTrue(ex.message!!.contains("condition"), "message says it is a condition: ${ex.message}")
    }

    @Test
    fun `an unknown condition id in a conf aggregates as one error and does not crash`(@TempDir root: Path) {
        writeConf(
            root, "enchants", "badcond",
            """
            id = "ramrpg:badcond"
            effects = [
              { type = triggered, trigger = "on_hit", conditions = ["ramrpg:no_such_condition"], action = "ramrpg:message", params { text = "hi" } }
            ]
            """,
        )

        val result = RpgContentLoader.load(root, registries())

        assertEquals(1, result.errors().size, "one aggregated error: ${result.errors()}")
        assertTrue(result.enchants.isEmpty(), "the broken enchant must not load")
        val error = result.errors().single()
        assertEquals("badcond.conf", error.source())
        assertTrue(error.message().contains("ramrpg:no_such_condition"), "names the id: ${error.message()}")
    }

    private fun writeConf(root: Path, type: String, name: String, body: String) {
        val dir = root.resolve(type)
        Files.createDirectories(dir)
        dir.resolve("$name.conf").toFile().writeText(body.trimIndent())
    }
}
