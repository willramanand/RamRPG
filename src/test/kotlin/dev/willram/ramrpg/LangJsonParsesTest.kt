package dev.willram.ramrpg

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * WP-0.2: lang/en_us.json must stay valid JSON with keys in ascending sorted order. Runs on main
 * after every merge (execution-plan F.3 step 9): catches a merge that produced invalid JSON, and
 * keeps the sorted-position insert rule enforceable so lang edits stay one-line merges.
 */
class LangJsonParsesTest {

    private fun langFile(): File {
        var dir: File? = File("").absoluteFile
        repeat(6) {
            val here = dir ?: return@repeat
            val f = File(here, "src/main/resources/lang/en_us.json")
            if (f.isFile) return f
            dir = here.parentFile
        }
        error("could not locate src/main/resources/lang/en_us.json")
    }

    @Test
    fun `en_us is valid json`() {
        val obj = Gson().fromJson(langFile().readText(), JsonObject::class.java)
        assertTrue(obj.size() > 0, "en_us.json must have entries")
    }

    @Test
    fun `en_us keys are in ascending sorted order`() {
        val keys = Regex(""""(ramrpg\.[^"]+)"\s*:""").findAll(langFile().readText())
            .map { it.groupValues[1] }.toList()
        assertEquals(keys.sorted(), keys, "en_us.json keys must be sorted; insert new keys in sorted position")
    }
}
