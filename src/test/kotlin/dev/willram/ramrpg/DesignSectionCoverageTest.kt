package dev.willram.ramrpg

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * WP-0.1: keeps the generated design index in lock-step with docs/design/. Every file in docs/design/
 * must be linked from docs/DESIGN.md and every link in docs/DESIGN.md must resolve to a real file, so a
 * design note that never landed (or an orphaned file the index forgot) fails the build. The orchestrator
 * regenerates docs/DESIGN.md at each merge (execution-plan F.3 step 5a); this test guards that.
 */
class DesignSectionCoverageTest {

    private fun docsDir(): Path {
        var dir: Path? = Path.of("").toAbsolutePath()
        repeat(6) {
            val here = dir ?: return@repeat
            val candidate = here.resolve("docs")
            if (candidate.resolve("DESIGN.md").toFile().exists()) return candidate
            dir = here.parent
        }
        error("could not locate docs/DESIGN.md from ${Path.of("").toAbsolutePath()}")
    }

    @Test
    fun `DESIGN index and docs-design directory are in one-to-one sync`() {
        val docs = docsDir()
        val designDir = docs.resolve("design").toFile()
        assertTrue(designDir.isDirectory, "docs/design must exist")

        val files = designDir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.map { it.name }?.toSortedSet() ?: sortedSetOf()

        val indexText = docs.resolve("DESIGN.md").toFile().readText()
        val linked = Regex("""design/([A-Za-z0-9._-]+\.md)""")
            .findAll(indexText).map { it.groupValues[1] }.toSortedSet()

        val missingFromIndex = files - linked
        val brokenLinks = linked - files
        assertTrue(missingFromIndex.isEmpty(), "design files not linked from docs/DESIGN.md: $missingFromIndex")
        assertTrue(brokenLinks.isEmpty(), "docs/DESIGN.md links a file that does not exist: $brokenLinks")
        assertTrue(files.isNotEmpty(), "docs/design must contain at least one design note")
    }
}
