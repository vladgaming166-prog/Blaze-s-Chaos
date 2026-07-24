package com.blazeschaos.coins;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class CoinsManager {

    private final BlazesChaosPlugin plugin;

    public CoinsManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public int balance(@NotNull Player player) {
        return plugin.database().getCoins(player.getUniqueId(), player.getName());
    }

    public void add(@NotNull Player player, int amount, @NotNull String reasonKey) {
        if (amount <= 0) {
            return;
        }
        plugin.database().addCoins(player.getUniqueId(), player.getName(), amount);
        plugin.lang().send(player, reasonKey, Map.of(
                "amount", String.valueOf(amount),
                "balance", String.valueOf(balance(player))
        ));
    }

    public boolean spend(@NotNull Player player, int amount) {
        return plugin.database().removeCoins(player.getUniqueId(), player.getName(), amount);
    }

    public int winReward() {
        return plugin.configs().config().getInt("coins.win", 50);
    }

    public int killReward() {
        return plugin.configs().config().getInt("coins.kill", 5);
    }

    public int participationReward() {
        return plugin.configs().config().getInt("coins.participation", 10);
    }

    public int survivalPerMinute() {
        return plugin.configs().config().getInt("coins.survival-per-minute", 2);
    }
}
