package dev.willram.ramrpg

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * WP-0.2: every ramrpg.* key passed to Component.translatable in Kotlin source must exist in
 * lang/en_us.json (forward), and every key in en_us.json must be referenced in source (reverse) —
 * except keys built dynamically from a known prefix. Replaces the old Component.text grep: it catches
 * a key clobbered by a merge (present in Kotlin, missing from the file) and an orphaned lang entry.
 */
class LangKeyCoverageTest {

    /** Keys whose names are built at runtime (e.g. "ramrpg.stat.${'$'}{id}") and never appear as literals. */
    private val exemptPrefixes = listOf("ramrpg.stat.")

    private fun root(): File {
        var dir: File? = File("").absoluteFile
        repeat(6) {
            val here = dir ?: return@repeat
            if (File(here, "src/main/kotlin").isDirectory && File(here, "src/main/resources/lang/en_us.json").isFile) return here
            dir = here.parentFile
        }
        error("could not locate project root")
    }

    private fun jsonKeys(root: File): Set<String> =
        Regex(""""(ramrpg\.[^"]+)"\s*:""").findAll(File(root, "src/main/resources/lang/en_us.json").readText())
            .map { it.groupValues[1] }.toSet()

    private fun referencedKeys(root: File): Set<String> {
        // Whole-file scan (not line-by-line) so multi-line translatable(...) calls are matched.
        val pattern = Regex("""translatable\(\s*"(ramrpg\.[A-Za-z0-9_.]+)"""")
        return File(root, "src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { pattern.findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toSet()
    }

    @Test
    fun `translatable keys and lang file are consistent`() {
        val root = root()
        val json = jsonKeys(root)
        val referenced = referencedKeys(root)

        val missingFromJson = referenced - json
        assertTrue(missingFromJson.isEmpty(), "translatable keys used in source but missing from en_us.json: $missingFromJson")

        val orphaned = json.filter { key -> key !in referenced && exemptPrefixes.none { key.startsWith(it) } }
        assertTrue(orphaned.isEmpty(), "en_us.json keys never referenced in source (and not a dynamic prefix): $orphaned")
    }
}
