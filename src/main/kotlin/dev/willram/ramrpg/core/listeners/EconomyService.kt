/** Thin Vault Economy wrapper. Safe no-op when Vault absent. */
package dev.willram.ramrpg.core.listeners

import dev.willram.ramcore.economy.Economies
import dev.willram.ramcore.integration.Integrations
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

class EconomyService {
    private val econ: Economy? by lazy {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return@lazy null
        val rsp = Bukkit.getServer().servicesManager.getRegistration(Economy::class.java) ?: return@lazy null
        rsp.provider
    }

    val enabled: Boolean get() = econ != null

    fun deposit(p: OfflinePlayer, amount: Double): Boolean {
        val e = econ ?: return false
        if (amount <= 0) return false
        return e.depositPlayer(p, amount).transactionSuccess()
    }

    fun balance(p: OfflinePlayer): Double = econ?.getBalance(p) ?: 0.0

    /**
     * RamCore's [dev.willram.ramcore.economy.Economy] view of this plugin's money, for RamCore
     * consumers such as [dev.willram.ramcore.reward.RewardActionFactories.standard] (the `money`
     * reward action registered in [dev.willram.ramrpg.RamRPG]). Detected the same way RamCore detects
     * Vault everywhere else -- through the integration registry, never touching `net.milkbowl` classes
     * when Vault is absent -- and falls back to an in-memory economy so reward registration never
     * fails; money rewards then simply don't move a real balance.
     */
    val ramCoreEconomy: dev.willram.ramcore.economy.Economy by lazy {
        Economies.detect(Integrations.standard()).orElseGet(Economies::inMemory)
    }
}
