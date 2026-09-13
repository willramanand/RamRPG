/** bStats metrics registration. Charts: registered skill count. */
package dev.willram.ramrpg.core.config

import dev.willram.ramrpg.RamRPG
import dev.willram.ramrpg.core.services.RpgServiceKeys
import org.bstats.bukkit.Metrics
import org.bstats.charts.SingleLineChart

object RamRpgMetrics {
    private const val PLUGIN_ID = 12345 // TODO: register on bstats.org and replace

    fun register(plugin: RamRPG) {
        val metrics = Metrics(plugin, PLUGIN_ID)
        metrics.addCustomChart(SingleLineChart("registered_skills") {
            plugin.services().require(RpgServiceKeys.SKILL_REGISTRY).all().size
        })
        metrics.addCustomChart(SingleLineChart("registered_items") {
            plugin.services().require(RpgServiceKeys.ITEM_DEFINITIONS).all().size
        })
    }
}
