package dev.willram.ramrpg

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * WP-1.5e: every `hasPermission("...")` node referenced in Kotlin source must be DECLARED under
 * `permissions:` in paper-plugin.yml (forward), and every declared node must be referenced somewhere
 * in Kotlin (reverse) -- except a pure umbrella/aggregator node (one that declares `children:`), whose
 * whole purpose is to imply its children for a permission-plugin operator and which is therefore never
 * checked directly via `hasPermission`. This is the real guard against a typo'd node that silently
 * never grants: a node referenced in code but missing from paper-plugin.yml would let every check fail
 * silently for non-op senders, and a node declared but never checked would be dead config.
 *
 * Mirrors [LangKeyCoverageTest]'s project-root-finding + whole-source-tree-scan approach, but parses
 * paper-plugin.yml as real YAML (SnakeYAML ships transitively via paper-api, already on the test
 * classpath) instead of a regex, since the permissions block has real map/list structure (`children:`).
 */
class PermissionNodeCoverageTest {

    private fun root(): File {
        var dir: File? = File("").absoluteFile
        repeat(6) {
            val here = dir ?: return@repeat
            if (File(here, "src/main/kotlin").isDirectory && File(here, "src/main/resources/paper-plugin.yml").isFile) return here
            dir = here.parentFile
        }
        error("could not locate project root")
    }

    /** Declared node -> true if it declares `children:` (an umbrella node never checked directly). */
    @Suppress("UNCHECKED_CAST")
    private fun declaredNodes(root: File): Map<String, Boolean> {
        val yaml = Yaml()
        val doc = yaml.load<Map<String, Any?>>(File(root, "src/main/resources/paper-plugin.yml").readText())
        val permissions = doc["permissions"] as? Map<String, Any?> ?: emptyMap()
        return permissions.mapValues { (_, spec) ->
            val map = spec as? Map<String, Any?> ?: emptyMap()
            map.containsKey("children")
        }
    }

    private fun referencedNodes(root: File): Set<String> {
        // Whole-file scan (not line-by-line) so a wrapped hasPermission(...) call is still matched.
        val pattern = Regex("""hasPermission\(\s*"(ramrpg\.[A-Za-z0-9_.]+)"""")
        return File(root, "src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { pattern.findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toSet()
    }

    @Test
    fun `hasPermission nodes and paper-plugin yml declarations are consistent`() {
        val root = root()
        val declared = declaredNodes(root)
        val referenced = referencedNodes(root)

        val undeclared = referenced - declared.keys
        assertTrue(undeclared.isEmpty(), "hasPermission(...) nodes referenced in source but not declared under permissions: in paper-plugin.yml: $undeclared")

        val orphaned = declared.filterNot { (node, isUmbrella) -> node in referenced || isUmbrella }.keys
        assertTrue(orphaned.isEmpty(), "permissions declared in paper-plugin.yml but never referenced via hasPermission(...) in source (and not an umbrella `children:` node): $orphaned")
    }
}
