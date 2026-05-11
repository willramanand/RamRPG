/** Thin Vault Economy wrapper. Safe no-op when Vault absent. */
package dev.willram.ramrpg.core.listeners

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
}
