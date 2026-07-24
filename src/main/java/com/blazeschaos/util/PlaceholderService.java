package com.blazeschaos.util;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.game.GameState;
import com.blazeschaos.npc.NpcMode;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Central placeholder resolution for scoreboards, tablist, messages, bossbars, titles, animations, NPCs.
 */
public final class PlaceholderService {

    private final BlazesChaosPlugin plugin;

    public PlaceholderService(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public @NotNull String apply(@Nullable CommandSender sender, @NotNull String input) {
        if (sender instanceof Player player) {
            return apply(player, safeGame(player), input);
        }
        return finalize(null, null, input);
    }

    public @NotNull String apply(@NotNull Player player, @NotNull String input) {
        return apply(player, safeGame(player), input);
    }

    public @NotNull String apply(@NotNull Player player, @Nullable GameInstance game, @NotNull String input) {
        return finalize(player, game, input);
    }

    private @NotNull String finalize(@Nullable Player player, @Nullable GameInstance game, @NotNull String input) {
        String text = input;
        try {
            if (plugin.animationManager() != null) {
                text = plugin.animationManager().resolve(text);
            }
        } catch (Throwable ignored) {
        }
        text = applyInternal(player, game, text);
        if (player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                text = PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable ex) {
                if (plugin.configs().debug()) {
                    plugin.getLogger().warning("PlaceholderAPI error: " + ex.getMessage());
                }
            }
        }
        // Second pass: nested animation placeholders that appeared after PAPI / internals
        try {
            if (plugin.animationManager() != null
                    && (text.indexOf('%') >= 0 || text.indexOf('{') >= 0)
                    && (text.contains("animation:") || text.contains("blazechaosanimation_")
                    || text.contains("blazechaos_animation_"))) {
                text = plugin.animationManager().resolve(text);
            }
        } catch (Throwable ignored) {
        }
        return text;
    }

    private @Nullable GameInstance safeGame(@NotNull Player player) {
        try {
            if (plugin.gameManager() != null) {
                return plugin.gameManager().getByPlayer(player);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private @NotNull String applyInternal(@Nullable Player player, @Nullable GameInstance game, @NotNull String input) {
        String text = input;
        int onlinePlaying = 0;
        try {
            if (plugin.npcManager() != null) {
                onlinePlaying = plugin.npcManager().onlinePlaying();
            }
        } catch (Throwable ignored) {
        }

        text = text
                .replace("%blazechaos_online%", String.valueOf(onlinePlaying))
                .replace("%blazeschaos_online%", String.valueOf(onlinePlaying))
                .replace("%blazechaos_players%", game == null ? String.valueOf(onlinePlaying) : String.valueOf(game.playerCount()))
                .replace("%blazeschaos_players%", game == null ? String.valueOf(onlinePlaying) : String.valueOf(game.playerCount()))
                .replace("%blazechaos_alive%", game == null ? "0" : String.valueOf(game.aliveCount()))
                .replace("%blazeschaos_alive%", game == null ? "0" : String.valueOf(game.aliveCount()))
                .replace("%blazechaos_event%", currentEventName(game))
                .replace("%blazeschaos_event%", currentEventName(game))
                .replace("%blazechaos_next_event%", game == null ? "-" : String.valueOf(game.getNextEventSeconds()))
                .replace("%blazeschaos_next_event%", game == null ? "-" : String.valueOf(game.getNextEventSeconds()))
                .replace("%blazechaos_time%", game == null ? "0" : String.valueOf(resolveTime(game)))
                .replace("%blazeschaos_time%", game == null ? "0" : String.valueOf(resolveTime(game)))
                .replace("%blazechaos_map%", game == null ? "-" : game.getArena().getDisplayName())
                .replace("%blazeschaos_map%", game == null ? "-" : game.getArena().getDisplayName())
                .replace("%blazechaos_mode%", game == null ? "Lobby" : "Solo")
                .replace("%blazeschaos_mode%", game == null ? "Lobby" : "Solo")
                .replace("%blazechaos_state%", game == null ? "Lobby" : game.getState().display())
                .replace("%blazeschaos_state%", game == null ? "Lobby" : game.getState().display())
                .replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()));

        text = replaceQueue(text, "solo", NpcMode.SOLO);
        text = replaceQueue(text, "duos", NpcMode.DUOS);
        text = replaceQueue(text, "trios", NpcMode.TRIOS);
        text = replaceQueue(text, "squads", NpcMode.SQUADS);
        text = replaceQueue(text, "random", NpcMode.RANDOM);

        if (player != null) {
            text = text.replace("%player%", player.getName());
            var stats = plugin.database().getStats(player.getUniqueId(), player.getName());
            text = text
                    .replace("%blazechaos_wins%", String.valueOf(stats.wins()))
                    .replace("%blazeschaos_wins%", String.valueOf(stats.wins()))
                    .replace("%blazechaos_games%", String.valueOf(stats.games()))
                    .replace("%blazeschaos_games%", String.valueOf(stats.games()))
                    .replace("%blazechaos_kills%", String.valueOf(stats.kills()))
                    .replace("%blazeschaos_kills%", String.valueOf(stats.kills()))
                    .replace("%blazechaos_deaths%", String.valueOf(stats.deaths()))
                    .replace("%blazeschaos_deaths%", String.valueOf(stats.deaths()))
                    .replace("%blazechaos_coins%", String.valueOf(stats.coins()))
                    .replace("%blazeschaos_coins%", String.valueOf(stats.coins()))
                    .replace("%blazechaos_balance%", String.valueOf(stats.coins()))
                    .replace("%blazeschaos_balance%", String.valueOf(stats.coins()));
        } else {
            text = text
                    .replace("%blazechaos_wins%", "0")
                    .replace("%blazechaos_games%", "0")
                    .replace("%blazechaos_kills%", "0")
                    .replace("%blazechaos_deaths%", "0")
                    .replace("%blazechaos_coins%", "0")
                    .replace("%blazechaos_balance%", "0");
        }
        return text;
    }

    private @NotNull String replaceQueue(@NotNull String text, @NotNull String key, @NotNull NpcMode mode) {
        String value = "0";
        try {
            if (plugin.npcManager() != null) {
                value = String.valueOf(plugin.npcManager().queueCount(mode));
            }
        } catch (Throwable ignored) {
        }
        return text.replace("%blazechaos_queue_" + key + "%", value)
                .replace("%blazeschaos_queue_" + key + "%", value);
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
