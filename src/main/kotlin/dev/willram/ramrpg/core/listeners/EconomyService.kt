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
     * WP-3.3a: can [p] afford [amount] gold? A non-positive [amount] is always affordable, and when no
     * Vault economy is present crafts are treated as FREE (`true`) rather than blocked -- consistent with
     * this service's "safe no-op when Vault absent" contract. Used by the smithing-upgrade cost gate (the
     * gold figure comes from [dev.willram.ramrpg.core.crafting.UpgradeOutcomes.upgradeGoldCost]).
     */
    fun canAfford(p: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0.0) return true
        val e = econ ?: return true
        return e.getBalance(p) >= amount
    }

    /**
     * WP-3.3a: withdraw [amount] gold from [p], returning `true` iff the charge went through (or was a
     * no-op). A non-positive [amount] charges nothing; with no Vault economy the upgrade is free (`true`);
     * an insufficient balance fails (`false`) WITHOUT withdrawing. This is the charging helper the
     * smithing-upgrade runtime uses for both the full success cost and the reduced
     * [dev.willram.ramrpg.core.crafting.UpgradeOutcomes.goldCostOnFailure] fee.
     */
    fun charge(p: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0.0) return true
        val e = econ ?: return true
        if (e.getBalance(p) < amount) return false
        return e.withdrawPlayer(p, amount).transactionSuccess()
    }

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
