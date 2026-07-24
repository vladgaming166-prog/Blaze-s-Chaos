package com.blazeschaos.scoreboard;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.util.ColorUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
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
            "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b", "§c", "§d", "§e"
    };

    private final BlazesChaosPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private int titleFrame;
    private @Nullable BukkitTask animationTask;

    public ScoreboardManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (animationTask != null) {
            animationTask.cancel();
        }
        int interval = Math.max(1, plugin.configs().scoreboard().getInt("animations.frame-interval", 10));
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            titleFrame++;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (boards.containsKey(player.getUniqueId())) {
                    GameInstance game = plugin.gameManager().getByPlayer(player);
                    if (game != null) {
                        apply(player, game);
                    } else {
                        applyLobby(player);
                    }
                }
            }
        }, interval, interval);
    }

    public void stop() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
        for (UUID uuid : new ArrayList<>(boards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                remove(player);
            }
        }
        boards.clear();
    }

    public void applyLobby(@NotNull Player player) {
        if (!plugin.configs().scoreboard().getBoolean("enabled", true)) {
            return;
        }
        render(player, "lobby", null);
    }

    public void apply(@NotNull Player player, @NotNull GameInstance game) {
        if (!plugin.configs().scoreboard().getBoolean("enabled", true)) {
            return;
        }
        String section = game.isSpectator(player.getUniqueId()) ? "spectator" : "game";
        if (game.getState().isJoinable()) {
            section = "lobby";
        }
        render(player, section, game);
    }

    public void updateGame(@NotNull GameInstance game) {
        int interval = plugin.configs().scoreboard().getInt("update-interval", 20);
        if (game.getGameTicks() % Math.max(1, interval) != 0 && !game.getState().isJoinable()) {
            // still update frequently enough via animation task
        }
        for (Player player : game.getPlayers()) {
            apply(player, game);
        }
    }

    private void render(@NotNull Player player, @NotNull String section, @Nullable GameInstance game) {
        FileConfiguration config = plugin.configs().scoreboard();
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = board.getObjective("blazechaos");
        if (objective == null) {
            objective = board.registerNewObjective("blazechaos", Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        if (config.getBoolean("hide-scores", true)) {
            objective.numberFormat(NumberFormat.blank());
        }

        Component title = ColorUtil.parse(resolveTitle(config, section));
        objective.displayName(title);

        List<String> lines = config.getStringList(section + ".lines");
        List<String> rendered = new ArrayList<>();
        for (String line : lines) {
            rendered.add(applyPlaceholders(player, game, line));
        }
        while (rendered.size() > 15) {
            rendered.remove(rendered.size() - 1);
        }

        for (int i = 0; i < ENTRY_KEYS.length; i++) {
            String entry = ENTRY_KEYS[i];
            Team team = board.getTeam("bc_" + i);
            if (i >= rendered.size()) {
                board.resetScores(entry);
                if (team != null) {
                    team.unregister();
                }
                continue;
            }
            if (team == null) {
                team = board.registerNewTeam("bc_" + i);
                team.addEntry(entry);
            }
            team.prefix(ColorUtil.parse(rendered.get(i)));
            team.suffix(Component.empty());
            objective.getScore(entry).setScore(rendered.size() - i);
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
                .replace("%blazechaos_players%", game == null ? String.valueOf(Bukkit.getOnlinePlayers().size()) : String.valueOf(game.playerCount()))
                .replace("%blazechaos_alive%", game == null ? "0" : String.valueOf(game.aliveCount()))
                .replace("%blazechaos_event%", currentEventName(game))
                .replace("%blazechaos_next_event%", game == null ? "-" : String.valueOf(game.getNextEventSeconds()))
                .replace("%blazechaos_time%", game == null ? "0" : String.valueOf(game.getGameSeconds()))
                .replace("%blazechaos_map%", game == null ? "-" : game.getArena().getName())
                .replace("%blazechaos_state%", game == null ? "Lobby" : game.getState().display())
                .replace("%player%", player.getName());

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            text = PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    private @NotNull String currentEventName(@Nullable GameInstance game) {
        if (game == null || game.getActiveEvent() == null) {
            return "None";
        }
        String id = game.getActiveEvent().getId();
        return ColorUtil.strip(plugin.configs().events().getString("display-names." + id, game.getActiveEvent().getDefaultDisplayName()));
    }

    public void remove(@NotNull Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
