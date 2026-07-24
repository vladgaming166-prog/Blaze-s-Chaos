package com.blazeschaos.util;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.game.GameState;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Central placeholder resolution for scoreboards, tablist, messages, bossbars, titles, animations.
 */
public final class PlaceholderService {

    private final BlazesChaosPlugin plugin;

    public PlaceholderService(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public @NotNull String apply(@Nullable CommandSender sender, @NotNull String input) {
        if (sender instanceof Player player) {
            return apply(player, plugin.gameManager().getByPlayer(player), input);
        }
        return applyInternal(null, null, input);
    }

    public @NotNull String apply(@NotNull Player player, @NotNull String input) {
        GameInstance game = null;
        try {
            if (plugin.gameManager() != null) {
                game = plugin.gameManager().getByPlayer(player);
            }
        } catch (Throwable ignored) {
        }
        return apply(player, game, input);
    }

    public @NotNull String apply(@NotNull Player player, @Nullable GameInstance game, @NotNull String input) {
        String text = applyInternal(player, game, input);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                text = PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable ex) {
                if (plugin.configs().debug()) {
                    plugin.getLogger().warning("PlaceholderAPI error: " + ex.getMessage());
                }
            }
        }
        return text;
    }

    private @NotNull String applyInternal(@Nullable Player player, @Nullable GameInstance game, @NotNull String input) {
        String text = input;
        text = text
                .replace("%blazeschaos_players%", game == null ? "0" : String.valueOf(game.playerCount()))
                .replace("%blazechaos_players%", game == null ? "0" : String.valueOf(game.playerCount()))
                .replace("%blazeschaos_alive%", game == null ? "0" : String.valueOf(game.aliveCount()))
                .replace("%blazechaos_alive%", game == null ? "0" : String.valueOf(game.aliveCount()))
                .replace("%blazeschaos_event%", currentEventName(game))
                .replace("%blazechaos_event%", currentEventName(game))
                .replace("%blazeschaos_next_event%", game == null ? "-" : String.valueOf(game.getNextEventSeconds()))
                .replace("%blazechaos_next_event%", game == null ? "-" : String.valueOf(game.getNextEventSeconds()))
                .replace("%blazeschaos_time%", game == null ? "0" : String.valueOf(resolveTime(game)))
                .replace("%blazechaos_time%", game == null ? "0" : String.valueOf(resolveTime(game)))
                .replace("%blazeschaos_map%", game == null ? "-" : game.getArena().getDisplayName())
                .replace("%blazechaos_map%", game == null ? "-" : game.getArena().getDisplayName())
                .replace("%blazeschaos_state%", game == null ? "Lobby" : game.getState().display())
                .replace("%blazechaos_state%", game == null ? "Lobby" : game.getState().display())
                .replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()));

        if (player != null) {
            text = text.replace("%player%", player.getName());
            var stats = plugin.database().getStats(player.getUniqueId(), player.getName());
            text = text
                    .replace("%blazeschaos_wins%", String.valueOf(stats.wins()))
                    .replace("%blazechaos_wins%", String.valueOf(stats.wins()))
                    .replace("%blazeschaos_games%", String.valueOf(stats.games()))
                    .replace("%blazechaos_games%", String.valueOf(stats.games()))
                    .replace("%blazeschaos_kills%", String.valueOf(stats.kills()))
                    .replace("%blazechaos_kills%", String.valueOf(stats.kills()))
                    .replace("%blazeschaos_coins%", String.valueOf(stats.coins()))
                    .replace("%blazechaos_coins%", String.valueOf(stats.coins()))
                    .replace("%blazeschaos_balance%", String.valueOf(stats.coins()))
                    .replace("%blazechaos_balance%", String.valueOf(stats.coins()));
        }
        return text;
    }

    private int resolveTime(@NotNull GameInstance game) {
        if (game.getState() == GameState.STARTING) {
            return game.getCountdownSecondsLeft();
        }
        return game.getGameSeconds();
    }

    private @NotNull String currentEventName(@Nullable GameInstance game) {
        if (game == null || game.getActiveEvent() == null) {
            return "None";
        }
        String id = game.getActiveEvent().getId();
        return ColorUtil.strip(plugin.configs().events().getString("display-names." + id,
                game.getActiveEvent().getDefaultDisplayName()));
    }
}
