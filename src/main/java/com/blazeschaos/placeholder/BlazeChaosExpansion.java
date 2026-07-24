package com.blazeschaos.placeholder;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.database.PlayerStats;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.util.ColorUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BlazeChaosExpansion extends PlaceholderExpansion {

    private final BlazesChaosPlugin plugin;

    public BlazeChaosExpansion(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "blazechaos";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Blaze";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(@Nullable Player player, @NotNull String params) {
        GameInstance game = player == null ? null : plugin.gameManager().getByPlayer(player);
        return switch (params.toLowerCase()) {
            case "players" -> game == null ? "0" : String.valueOf(game.playerCount());
            case "alive" -> game == null ? "0" : String.valueOf(game.aliveCount());
            case "event" -> {
                if (game == null || game.getActiveEvent() == null) {
                    yield "None";
                }
                String id = game.getActiveEvent().getId();
                yield ColorUtil.strip(plugin.configs().events().getString("display-names." + id, game.getActiveEvent().getDefaultDisplayName()));
            }
            case "next_event" -> game == null ? "-" : String.valueOf(game.getNextEventSeconds());
            case "time" -> game == null ? "0" : String.valueOf(game.getGameSeconds());
            case "map" -> game == null ? "-" : game.getArena().getName();
            case "state" -> game == null ? "Lobby" : game.getState().display();
            case "wins" -> {
                if (player == null) {
                    yield "0";
                }
                PlayerStats stats = plugin.database().getStats(player.getUniqueId(), player.getName());
                yield String.valueOf(stats.wins());
            }
            case "games" -> {
                if (player == null) {
                    yield "0";
                }
                PlayerStats stats = plugin.database().getStats(player.getUniqueId(), player.getName());
                yield String.valueOf(stats.games());
            }
            default -> null;
        };
    }
}
