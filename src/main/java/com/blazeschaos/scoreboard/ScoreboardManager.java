package com.blazeschaos.scoreboard;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.game.GameInstance;
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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flicker-free sidebar scoreboard.
 * Reuses one scoreboard/objective/teams per player and only writes when content changes.
 */
public final class ScoreboardManager {

    private static final String OBJECTIVE_NAME = "blazechaos";
    private static final String[] ENTRY_KEYS = {
            "§0§r", "§1§r", "§2§r", "§3§r", "§4§r", "§5§r", "§6§r", "§7§r",
            "§8§r", "§9§r", "§a§r", "§b§r", "§c§r", "§d§r", "§e§r"
    };

    private final BlazesChaosPlugin plugin;
    private final ConcurrentHashMap<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CachedBoard> cache = new ConcurrentHashMap<>();
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
            // Advance animations once per scoreboard update (identical to pre-redesign timing)
            plugin.animationManager().tick();
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    refresh(player);
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Scoreboard refresh failed for "
                            + player.getName() + ": " + ex.getMessage());
                    if (plugin.configs().debug()) {
                        ex.printStackTrace();
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
            } else {
                boards.remove(uuid);
                cache.remove(uuid);
            }
        }
        boards.clear();
        cache.clear();
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
        render(player, resolveSection(player, game), game);
    }

    public void applyLobby(@NotNull Player player) {
        cache.remove(player.getUniqueId());
        render(player, "server-lobby", null);
    }

    public void apply(@NotNull Player player, @NotNull GameInstance game) {
        cache.remove(player.getUniqueId());
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

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            // Clean up any stale objectives with same display slot
            Objective existing = board.getObjective(DisplaySlot.SIDEBAR);
            if (existing != null && !OBJECTIVE_NAME.equals(existing.getName())) {
                existing.unregister();
            }
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else if (objective.getDisplaySlot() != DisplaySlot.SIDEBAR) {
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        if (config.getBoolean("hide-scores", true)) {
            objective.numberFormat(NumberFormat.blank());
        }

        String titleRaw = applyAll(player, game, resolveTitle(config, section));
        List<String> lines = config.getStringList(section + ".lines");
        if (lines.isEmpty() && section.equals("server-lobby")) {
            lines = config.getStringList("lobby.lines");
        }

        List<String> rendered = new ArrayList<>(Math.min(15, lines.size()));
        for (String line : lines) {
            if (rendered.size() >= 15) {
                break;
            }
            rendered.add(applyAll(player, game, line));
        }

        CachedBoard previous = cache.get(player.getUniqueId());
        if (previous != null
                && Objects.equals(previous.title, titleRaw)
                && previous.lines.equals(rendered)
                && previous.section.equals(section)) {
            // Content unchanged — skip writes to avoid flicker
            return;
        }

        Component titleComponent = ColorUtil.parse(titleRaw);
        if (previous == null || !Objects.equals(previous.title, titleRaw)) {
            objective.displayName(titleComponent);
        }

        for (int i = 0; i < ENTRY_KEYS.length; i++) {
            String entry = ENTRY_KEYS[i];
            Team team = board.getTeam("bc" + i);
            if (i >= rendered.size()) {
                board.resetScores(entry);
                // Keep team registered empty to avoid recreate churn; clear prefix
                if (team != null) {
                    team.prefix(Component.empty());
                    team.suffix(Component.empty());
                }
                continue;
            }
            if (team == null) {
                team = board.registerNewTeam("bc" + i);
            }
            if (!team.hasEntry(entry)) {
                // Ensure only this entry
                for (String existing : new ArrayList<>(team.getEntries())) {
                    team.removeEntry(existing);
                }
                team.addEntry(entry);
            }
            String text = rendered.get(i);
            boolean lineChanged = previous == null
                    || previous.lines.size() <= i
                    || !Objects.equals(previous.lines.get(i), text);
            if (lineChanged) {
                team.prefix(ColorUtil.parse(text));
                team.suffix(Component.empty());
            }
            objective.getScore(entry).setScore(15 - i);
        }

        cache.put(player.getUniqueId(), new CachedBoard(section, titleRaw, List.copyOf(rendered)));
    }

    private @NotNull String resolveTitle(@NotNull FileConfiguration config, @NotNull String section) {
        String configured = config.getString(section + ".title", "");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (plugin.animationManager().get("title") != null) {
            return "%animation:title%";
        }
        return "<gradient:#FF4500:#FFD700><bold>Blaze's Chaos</bold></gradient>";
    }

    private @NotNull String applyAll(@NotNull Player player, @Nullable GameInstance game, @NotNull String input) {
        // Placeholders service also resolves animations; resolve once here for clarity
        String text = plugin.animationManager().resolve(input);
        return plugin.placeholders().apply(player, game, text);
    }

    public void remove(@NotNull Player player) {
        UUID id = player.getUniqueId();
        Scoreboard board = boards.remove(id);
        cache.remove(id);
        if (board != null) {
            try {
                Objective objective = board.getObjective(OBJECTIVE_NAME);
                if (objective != null) {
                    objective.unregister();
                }
                for (Team team : new ArrayList<>(board.getTeams())) {
                    if (team.getName().startsWith("bc")) {
                        team.unregister();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private record CachedBoard(@NotNull String section, @NotNull String title, @NotNull List<String> lines) {
    }
}
