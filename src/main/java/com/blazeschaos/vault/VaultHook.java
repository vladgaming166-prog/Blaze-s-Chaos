package com.blazeschaos.vault;

import com.blazeschaos.BlazesChaosPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

public final class VaultHook {

    private final BlazesChaosPlugin plugin;
    private Economy economy;

    public VaultHook(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found — economy rewards disabled.");
            return;
        }
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            plugin.getLogger().info("No Vault economy provider found.");
            return;
        }
        economy = registration.getProvider();
        plugin.getLogger().info("Hooked into Vault economy: " + economy.getName());
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public void deposit(@NotNull Player player, double amount) {
        if (economy == null || amount <= 0) {
            return;
        }
        economy.depositPlayer(player, amount);
    }
}
