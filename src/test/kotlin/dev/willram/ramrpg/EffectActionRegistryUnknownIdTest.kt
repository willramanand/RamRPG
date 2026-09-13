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
 * WP-1.5b: an unknown effect ACTION id is a load error that NAMES the id and AGGREGATES -- it never
 * crashes the load. Asserted twice: (1) the resolution point ([EffectSpec.one]) throws a
 * [ContentDeserializeException] naming the id, and (2) through [RpgContentLoader.load] the SpecLoader
 * records it as a [dev.willram.ramcore.exception.ValidationError] with the offending file, so a whole
 * content pack does not sink on one typo.
 */
class EffectActionRegistryUnknownIdTest {

    private fun registries() = EffectSpec.Registries(
        EffectActionRegistry(), EffectConditionRegistry(), BlockMatcherRegistry(),
    ).also { BuiltinEffectActions.registerAll(it.actions, it.conditions, it.matchers, RecordingScheduler()) }

    @Test
    fun `resolving an unknown action id throws naming the id`() {
        val node = HoconConfigurationLoader.builder()
            .source {
                BufferedReader(
                    StringReader(
                        """
                        type = triggered
                        trigger = "on_hit"
                        action = "ramrpg:does_not_exist"
                        """.trimIndent(),
                    ),
                )
            }
            .build()
            .load()

        val ex = assertThrows(ContentDeserializeException::class.java) {
            EffectSpec.one(node, EffectKey.of("ramrpg", "fx"), registries())
        }
        assertTrue(ex.message!!.contains("ramrpg:does_not_exist"), "message names the id: ${ex.message}")
        assertTrue(ex.message!!.contains("action"), "message says it is an action: ${ex.message}")
    }

    @Test
    fun `an unknown action id in a conf aggregates as one error and does not crash`(@TempDir root: Path) {
        writeConf(
            root, "enchants", "broken",
            """
            id = "ramrpg:broken"
            effects = [
              { type = triggered, trigger = "on_hit", action = "ramrpg:does_not_exist" }
            ]
            """,
        )

        val result = RpgContentLoader.load(root, registries())

        assertEquals(1, result.errors().size, "one aggregated error: ${result.errors()}")
        assertTrue(result.enchants.isEmpty(), "the broken enchant must not load")
        val error = result.errors().single()
        assertEquals("broken.conf", error.source())
        assertTrue(error.message().contains("ramrpg:does_not_exist"), "names the id: ${error.message()}")
    }

    private fun writeConf(root: Path, type: String, name: String, body: String) {
        val dir = root.resolve(type)
        Files.createDirectories(dir)
        dir.resolve("$name.conf").toFile().writeText(body.trimIndent())
    }
}
