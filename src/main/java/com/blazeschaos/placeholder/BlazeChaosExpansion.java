package com.blazeschaos.placeholder;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.database.PlayerStats;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.npc.NpcMode;
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
        String key = params.toLowerCase();
        if (key.startsWith("animation_")) {
            return plugin.animationManager().frame(key.substring("animation_".length()));
        }
        if (key.startsWith("queue_")) {
            try {
                NpcMode mode = NpcMode.parse(key.substring("queue_".length()));
                return String.valueOf(plugin.npcManager().queueCount(mode));
            } catch (Exception ex) {
                return "0";
            }
        }

        GameInstance game = player == null ? null : plugin.gameManager().getByPlayer(player);
        return switch (key) {
            case "online" -> String.valueOf(plugin.npcManager().onlinePlaying());
            case "players" -> game == null
                    ? String.valueOf(plugin.npcManager().onlinePlaying())
                    : String.valueOf(game.playerCount());
            case "alive" -> game == null ? "0" : String.valueOf(game.aliveCount());
            case "event" -> {
                if (game == null || game.getActiveEvent() == null) {
                    yield "None";
                }
                String id = game.getActiveEvent().getId();
                yield ColorUtil.strip(plugin.configs().events().getString("display-names." + id,
                        game.getActiveEvent().getDefaultDisplayName()));
            }
            case "next_event" -> game == null ? "-" : String.valueOf(game.getNextEventSeconds());
            case "time" -> game == null ? "0" : String.valueOf(game.getGameSeconds());
            case "map" -> game == null ? "-" : game.getArena().getDisplayName();
            case "mode" -> game == null ? "Lobby" : "Solo";
            case "state" -> game == null ? "Lobby" : game.getState().display();
            case "wins" -> stats(player, s -> String.valueOf(s.wins()));
            case "games" -> stats(player, s -> String.valueOf(s.games()));
            case "kills" -> stats(player, s -> String.valueOf(s.kills()));
            case "deaths" -> stats(player, s -> String.valueOf(s.deaths()));
            case "coins", "balance" -> {
                if (player == null) {
                    yield "0";
                }
                yield String.valueOf(plugin.database().getCoins(player.getUniqueId(), player.getName()));
            }
            default -> null;
        };
    }

    private @NotNull String stats(@Nullable Player player, @NotNull java.util.function.Function<PlayerStats, String> fn) {
        if (player == null) {
            return "0";
        }
        return fn.apply(plugin.database().getStats(player.getUniqueId(), player.getName()));
    }
}
