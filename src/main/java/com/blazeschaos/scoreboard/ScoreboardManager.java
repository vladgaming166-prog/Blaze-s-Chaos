package com.blazeschaos.scoreboard;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.game.GameState;
import com.blazeschaos.util.ColorUtil;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScoreboardManager {

    private static final String[] ENTRY_KEYS = {
            "§0§r", "§1§r", "§2§r", "§3§r", "§4§r", "§5§r", "§6§r", "§7§r",
            "§8§r", "§9§r", "§a§r", "§b§r", "§c§r", "§d§r", "§e§r"
    };

    private final BlazesChaosPlugin plugin;
    private final java.util.Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private int titleFrame;
    private @Nullable BukkitTask task;

    public ScoreboardManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stopTask();
        if (!plugin.configs().scoreboard().getBoolean("enabled", true)) {
            return;
        }
        int interval = Math.max(1, plugin.configs().scoreboard().getInt("update-interval", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            titleFrame++;
            plugin.animationManager().tick();
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    refresh(player);
                } catch (Throwable ex) {
                    if (plugin.configs().debug()) {
                        plugin.getLogger().warning("Scoreboard refresh failed for "
                                + player.getName() + ": " + ex.getMessage());
                    }
                }
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
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
        Objective objective = board.getObjective("blazechaos");
        if (objective == null) {
            objective = board.registerNewObjective("blazechaos", Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        if (config.getBoolean("hide-scores", true)) {
            objective.numberFormat(NumberFormat.blank());
        }

        objective.displayName(ColorUtil.parse(applyAll(player, game, resolveTitle(config, section))));

        List<String> lines = config.getStringList(section + ".lines");
        if (lines.isEmpty() && section.equals("server-lobby")) {
            lines = config.getStringList("lobby.lines");
        }
        List<String> rendered = new ArrayList<>();
        for (String line : lines) {
            rendered.add(applyAll(player, game, line));
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
            String text = rendered.get(i);
            team.prefix(ColorUtil.parse(text));
            team.suffix(Component.empty());
            objective.getScore(entry).setScore(15 - i);
        }
    }

    private @NotNull String resolveTitle(@NotNull FileConfiguration config, @NotNull String section) {
        String configured = config.getString(section + ".title", "");
        if (configured != null && configured.contains("%animation:")) {
            return configured;
        }
        if (config.getBoolean("animations.enabled", true)) {
            // Prefer dedicated animation named "title"
            if (plugin.animationManager().get("title") != null) {
                return "%animation:title%";
            }
            List<String> frames = config.getStringList("animations.title-frames");
            if (!frames.isEmpty()) {
                int frameInterval = Math.max(1, config.getInt("animations.frame-interval", 1));
                return frames.get(Math.floorMod(titleFrame / frameInterval, frames.size()));
            }
        }
        return config.getString(section + ".title", "<gold>Blaze's Chaos</gold>");
    }

    private @NotNull String applyAll(@NotNull Player player, @Nullable GameInstance game, @NotNull String input) {
        String text = plugin.animationManager().resolve(input);
        return plugin.placeholders().apply(player, game, text);
    }

    public void remove(@NotNull Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
}
