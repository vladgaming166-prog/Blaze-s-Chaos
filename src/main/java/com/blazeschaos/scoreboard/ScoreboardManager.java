package com.blazeschaos.scoreboard;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.game.GameState;
import com.blazeschaos.util.ColorUtil;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScoreboardManager {

    private static final String[] ENTRY_KEYS = {
            "§0§r", "§1§r", "§2§r", "§3§r", "§4§r", "§5§r", "§6§r", "§7§r",
            "§8§r", "§9§r", "§a§r", "§b§r", "§c§r", "§d§r", "§e§r"
    };

    private final BlazesChaosPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private int titleFrame;
    private @Nullable BukkitTask task;

    public ScoreboardManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stopTask();
        int interval = Math.max(1, plugin.configs().scoreboard().getInt("update-interval", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            titleFrame++;
            for (Player player : Bukkit.getOnlinePlayers()) {
                refresh(player);
            }
        }, interval, interval);
    }

    public void stop() {
        stopTask();
        for (UUID uuid : new ArrayList<>(boards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                remove(player);
            }
        }
        boards.clear();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void refresh(@NotNull Player player) {
        if (!plugin.configs().scoreboard().getBoolean("enabled", true)) {
            return;
        }
        if (plugin.setupMode().isInSetup(player)) {
            return;
        }
        GameInstance game = plugin.gameManager().getByPlayer(player);
        String section = resolveSection(player, game);
        render(player, section, game);
    }

    public void applyLobby(@NotNull Player player) {
        render(player, "server-lobby", null);
    }

    public void apply(@NotNull Player player, @NotNull GameInstance game) {
        render(player, resolveSection(player, game), game);
    }

    public void updateGame(@NotNull GameInstance game) {
        for (Player player : game.getPlayers()) {
            apply(player, game);
        }
    }

    private @NotNull String resolveSection(@NotNull Player player, @Nullable GameInstance game) {
        if (game == null) {
            return "server-lobby";
        }
        if (game.isSpectator(player.getUniqueId())) {
            return "spectator";
        }
        return switch (game.getState()) {
            case LOBBY, WAITING -> "waiting";
            case STARTING -> "starting";
            case PLAYING, CHAOS_EVENT, DEATHMATCH -> "playing";
            case ENDING, RESETTING -> "ending";
        };
    }

    private void render(@NotNull Player player, @NotNull String section, @Nullable GameInstance game) {
        FileConfiguration config = plugin.configs().scoreboard();
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                id -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = board.getObjective("blazechaos");
        if (objective == null) {
            objective = board.registerNewObjective("blazechaos", Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        if (config.getBoolean("hide-scores", true)) {
            objective.numberFormat(NumberFormat.blank());
        }

        objective.displayName(ColorUtil.parse(resolveTitle(config, section)));

        List<String> lines = config.getStringList(section + ".lines");
        if (lines.isEmpty() && section.equals("server-lobby")) {
            lines = config.getStringList("lobby.lines");
        }
        List<String> rendered = new ArrayList<>();
        for (String line : lines) {
            rendered.add(applyPlaceholders(player, game, line));
        }
        while (rendered.size() > 15) {
            rendered.removeLast();
        }

        for (int i = 0; i < ENTRY_KEYS.length; i++) {
            String entry = ENTRY_KEYS[i];
            Team team = board.getTeam("bc" + i);
            if (i >= rendered.size()) {
                board.resetScores(entry);
                if (team != null) {
                    team.unregister();
                }
                continue;
            }
            if (team == null) {
                team = board.registerNewTeam("bc" + i);
                team.addEntry(entry);
            } else if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            // Modern TAB-like: put text in prefix, keep suffix empty
            String text = rendered.get(i);
            if (text.length() > 64) {
                text = text.substring(0, 64);
            }
            team.prefix(ColorUtil.parse(text));
            team.suffix(Component.empty());
            objective.getScore(entry).setScore(15 - i);
        }

        player.setScoreboard(board);
    }

    private @NotNull String resolveTitle(@NotNull FileConfiguration config, @NotNull String section) {
        if (config.getBoolean("animations.enabled", true)) {
            List<String> frames = config.getStringList("animations.title-frames");
            if (!frames.isEmpty()) {
                return frames.get(Math.floorMod(titleFrame, frames.size()));
            }
        }
        return config.getString(section + ".title", "<gold>Blaze's Chaos</gold>");
    }

    private @NotNull String applyPlaceholders(@NotNull Player player, @Nullable GameInstance game, @NotNull String input) {
        String text = input
                .replace("%blazechaos_players%", game == null ? "0" : String.valueOf(game.playerCount()))
                .replace("%blazechaos_alive%", game == null ? "0" : String.valueOf(game.aliveCount()))
                .replace("%blazechaos_event%", currentEventName(game))
                .replace("%blazechaos_next_event%", game == null ? "-" : String.valueOf(game.getNextEventSeconds()))
                .replace("%blazechaos_time%", game == null ? "0" : String.valueOf(resolveTime(game)))
                .replace("%blazechaos_map%", game == null ? "-" : game.getArena().getDisplayName())
                .replace("%blazechaos_state%", game == null ? "Lobby" : game.getState().display())
                .replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%player%", player.getName());

        var stats = plugin.database().getStats(player.getUniqueId(), player.getName());
        text = text
                .replace("%blazechaos_wins%", String.valueOf(stats.wins()))
                .replace("%blazechaos_games%", String.valueOf(stats.games()))
                .replace("%blazechaos_kills%", String.valueOf(stats.kills()));

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            text = PlaceholderAPI.setPlaceholders(player, text);
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

    public void remove(@NotNull Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
