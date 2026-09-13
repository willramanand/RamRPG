package dev.willram.ramrpg

import dev.willram.ramcore.content.ContentDeserializeException
import dev.willram.ramcore.content.ContentId
import dev.willram.ramrpg.core.effects.BlockMatcherRegistry
import dev.willram.ramrpg.core.effects.BuiltinEffectActions
import dev.willram.ramrpg.core.effects.EffectActionRegistry
import dev.willram.ramrpg.core.effects.EffectConditionRegistry
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WP-1.5b: the third registry gets the same guard as the action/condition ones - an unknown block
 * matcher id (the `<matcher-id>` in `on_block_break:<matcher-id>`) is a load error that NAMES the id,
 * so a typo aggregates rather than crashing. Registry-level, off-server (matchers are pure).
 */
class BlockMatcherRegistryUnknownIdTest {

    private fun registry(): BlockMatcherRegistry = BlockMatcherRegistry().also {
        // populate the builtin matcher ids through the one place that knows them
        BuiltinEffectActions.registerAll(EffectActionRegistry(), EffectConditionRegistry(), it, RecordingScheduler())
    }

    @Test
    fun `require names the unknown matcher id and lists the registered ones`() {
        val reg = registry()
        val ex = assertThrows(ContentDeserializeException::class.java) {
            reg.require(ContentId.of("ramrpg", "does_not_exist"))
        }
        assertTrue(ex.message!!.contains("ramrpg:does_not_exist"), "names the id: ${ex.message}")
        // a builtin id proves the "registered matchers" list is populated, not empty
        assertTrue(ex.message!!.contains("ramrpg:ores"), "lists registered matchers: ${ex.message}")
    }

    @Test
    fun `a registered builtin matcher resolves`() {
        val reg = registry()
        // ofMaterials is entry one; resolving it must not throw
        reg.require(ContentId.of("ramrpg", "ores"))
    }
}
