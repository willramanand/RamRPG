package dev.willram.ramrpg

import dev.willram.ramcore.objective.ObjectiveAction
import dev.willram.ramcore.objective.ObjectiveEvent
import dev.willram.ramcore.objective.ObjectiveSubject
import dev.willram.ramrpg.api.identity.SkillKey
import dev.willram.ramrpg.api.quests.QuestDefinition
import dev.willram.ramrpg.api.quests.QuestGoal
import dev.willram.ramrpg.api.quests.QuestKey
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class QuestObjectiveMappingTest {

    @Test
    fun `kill goal maps to a KILL task on the profile id`() {
        val task = QuestGoal.KillEntityProfile("zombie", 5).toTask()
        assertEquals(ObjectiveAction.KILL, task.action())
        assertEquals("zombie", task.target())
        assertEquals(5L, task.required())
    }

    @Test
    fun `break goal maps to INTERACT_BLOCK with a lowercased material`() {
        val task = QuestGoal.BreakBlocks(Material.STONE, 3).toTask()
        assertEquals(ObjectiveAction.INTERACT_BLOCK, task.action())
        assertEquals("stone", task.target())
        assertEquals(3L, task.required())
    }

    @Test
    fun `skill xp goal maps to RUN_ACTION on the skill id`() {
        val task = QuestGoal.GainSkillXp(SkillKey.of("ramrpg", "combat"), 100).toTask()
        assertEquals(ObjectiveAction.RUN_ACTION, task.action())
        assertEquals("ramrpg:combat", task.target())
        assertEquals(100L, task.required())
    }

    @Test
    fun `a star target matches any event of that action`() {
        val task = QuestGoal.KillEntityProfile("*", 2).toTask()
        val event = ObjectiveEvent.of(ObjectiveSubject.player(UUID.randomUUID()), ObjectiveAction.KILL, "creeper")
        assertTrue(task.matches(event))
    }

    @Test
    fun `objective wraps the goal's single task`() {
        val q = QuestDefinition(QuestKey.of("test", "q"), Component.text("Q"), Component.text("d"),
            QuestGoal.KillEntityProfile("zombie", 5))
        val obj = q.objective()
        assertEquals(q.key.id, obj.id())
        assertEquals(1, obj.tasks().size)
        assertEquals("zombie", obj.tasks()[0].target())
    }
}
