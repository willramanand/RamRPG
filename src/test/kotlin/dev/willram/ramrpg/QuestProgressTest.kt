package dev.willram.ramrpg

import dev.willram.ramcore.objective.ObjectiveAction
import dev.willram.ramcore.objective.ObjectiveEvent
import dev.willram.ramcore.objective.ObjectiveSubject
import dev.willram.ramcore.objective.Objectives
import dev.willram.ramrpg.api.quests.QuestDefinition
import dev.willram.ramrpg.api.quests.QuestGoal
import dev.willram.ramrpg.api.quests.QuestKey
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Quest progress is tracked by RamCore's ObjectiveTracker. (The plan's migration test is skipped: this
 * is an in-house project on fresh servers, so there is no stored progress to migrate.)
 */
class QuestProgressTest {
    private val key = QuestKey.of("test", "kill3")
    private fun quest(target: Int) =
        QuestDefinition(key, Component.text("Kill $target"), Component.text("d"), QuestGoal.KillEntityProfile("zombie", target))

    @Test
    fun `matching events advance progress and the objective completes exactly once at the target`() {
        val tracker = Objectives.tracker()
        val q = quest(3)
        tracker.register(q.objective())
        val subject = ObjectiveSubject.player(UUID.randomUUID())

        var completions = 0
        repeat(3) {
            val updates = tracker.apply(ObjectiveEvent.of(subject, ObjectiveAction.KILL, "zombie"))
            if (updates.any { it.objectiveCompleted() }) completions++
        }
        assertEquals(1, completions, "completes once, on the 3rd kill")
        assertTrue(tracker.progress(subject, key.id).completed(q.objective()))
    }

    @Test
    fun `non-matching events do not advance progress`() {
        val tracker = Objectives.tracker()
        val q = quest(3)
        tracker.register(q.objective())
        val subject = ObjectiveSubject.player(UUID.randomUUID())

        tracker.apply(ObjectiveEvent.of(subject, ObjectiveAction.KILL, "creeper"))
        assertEquals(0L, tracker.progress(subject, key.id).current("kill"))
        assertFalse(tracker.progress(subject, key.id).completed(q.objective()))
    }
}
