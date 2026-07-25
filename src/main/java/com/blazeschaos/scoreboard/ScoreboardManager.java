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
 * Reuses one scoreboard / objective / teams per player and only writes when content changes.
 */
public final class ScoreboardManager {

    private static final String OBJECTIVE_NAME = "blazechaos";
    private static final String TEAM_PREFIX = "bc";
    private static final String[] ENTRY_KEYS = {
            "§0§r", "§1§r", "§2§r", "§3§r", "§4§r", "§5§r", "§6§r", "§7§r",
            "§8§r", "§9§r", "§a§r", "§b§r", "§c§r", "§d§r", "§e§r"
    };

    private final BlazesChaosPlugin plugin;
    private final ConcurrentHashMap<UUID, PlayerBoard> boards = new ConcurrentHashMap<>();
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
        render(player, resolveSection(player, game), game);
    }

    public void applyLobby(@NotNull Player player) {
        PlayerBoard board = boards.get(player.getUniqueId());
        if (board != null) {
            board.invalidate();
        }
        render(player, "server-lobby", null);
    }

    public void apply(@NotNull Player player, @NotNull GameInstance game) {
        PlayerBoard board = boards.get(player.getUniqueId());
        if (board != null) {
            board.invalidate();
        }
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
        PlayerBoard state = boards.computeIfAbsent(player.getUniqueId(), id -> createBoard(player));

        Scoreboard board = state.scoreboard;
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }

        Objective objective = state.objective;
        if (objective == null || board.getObjective(OBJECTIVE_NAME) == null) {
            // Remove any foreign sidebar objective to avoid duplicates
            Objective sidebar = board.getObjective(DisplaySlot.SIDEBAR);
            if (sidebar != null && !OBJECTIVE_NAME.equals(sidebar.getName())) {
                sidebar.unregister();
            }
            Objective existing = board.getObjective(OBJECTIVE_NAME);
            if (existing != null) {
                existing.unregister();
            }
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            state.objective = objective;
            ensureTeams(state);
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

        int count = Math.min(15, lines.size());
        List<String> rendered = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rendered.add(applyAll(player, game, lines.get(i)));
        }

        // Skip all packet writes when nothing changed
        if (state.section.equals(section)
                && Objects.equals(state.title, titleRaw)
                && state.lines.equals(rendered)) {
            return;
        }

        if (!Objects.equals(state.title, titleRaw)) {
            objective.displayName(ColorUtil.parse(titleRaw));
            state.title = titleRaw;
        }

        for (int i = 0; i < ENTRY_KEYS.length; i++) {
            String entry = ENTRY_KEYS[i];
            Team team = state.teams[i];
            if (team == null) {
                team = ensureTeam(board, i);
                state.teams[i] = team;
            }
            if (i >= rendered.size()) {
                if (state.activeLines > i) {
                    board.resetScores(entry);
                    team.prefix(Component.empty());
                    team.suffix(Component.empty());
                }
                continue;
            }
            if (!team.hasEntry(entry)) {
                for (String existing : new ArrayList<>(team.getEntries())) {
                    team.removeEntry(existing);
                }
                team.addEntry(entry);
            }
            String text = rendered.get(i);
            boolean lineChanged = state.lines.size() <= i || !Objects.equals(state.lines.get(i), text);
            if (lineChanged) {
                team.prefix(ColorUtil.parse(text));
                team.suffix(Component.empty());
            }
            // Score ranks are stable; only set once or when line newly appears
            if (state.activeLines <= i) {
                objective.getScore(entry).setScore(15 - i);
            }
        }

        // Clear leftover scores if board shrank
        for (int i = rendered.size(); i < state.activeLines; i++) {
            board.resetScores(ENTRY_KEYS[i]);
            if (state.teams[i] != null) {
                state.teams[i].prefix(Component.empty());
                state.teams[i].suffix(Component.empty());
            }
        }

        state.section = section;
        state.lines = List.copyOf(rendered);
        state.activeLines = rendered.size();
    }

    private @NotNull PlayerBoard createBoard(@NotNull Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.empty());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        PlayerBoard state = new PlayerBoard(board, objective);
        ensureTeams(state);
        player.setScoreboard(board);
        return state;
    }

    private void ensureTeams(@NotNull PlayerBoard state) {
        for (int i = 0; i < ENTRY_KEYS.length; i++) {
            if (state.teams[i] == null) {
                state.teams[i] = ensureTeam(state.scoreboard, i);
            }
        }
    }

    private @NotNull Team ensureTeam(@NotNull Scoreboard board, int index) {
        String name = TEAM_PREFIX + index;
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        String entry = ENTRY_KEYS[index];
        if (!team.hasEntry(entry)) {
            for (String existing : new ArrayList<>(team.getEntries())) {
                team.removeEntry(existing);
            }
            team.addEntry(entry);
        }
        return team;
    }

    private @NotNull String resolveTitle(@NotNull FileConfiguration config, @NotNull String section) {
        String configured = config.getString(section + ".title", "");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (config.getBoolean("animations.enabled", true)) {
            List<String> frames = config.getStringList("animations.title-frames");
            if (!frames.isEmpty()) {
                return frames.get(0);
            }
        }
        if (plugin.animationManager().get("title") != null) {
            return "%animation:title%";
        }
        return "<gradient:#FF4500:#FFD700><bold>Blaze's Chaos</bold></gradient>";
    }

    private @NotNull String applyAll(@NotNull Player player, @Nullable GameInstance game, @NotNull String input) {
        // Single path: PlaceholderService resolves animations + internal + PlaceholderAPI
        return plugin.placeholders().apply(player, game, input);
    }

    public void remove(@NotNull Player player) {
        UUID id = player.getUniqueId();
        PlayerBoard state = boards.remove(id);
        if (state != null) {
            try {
                if (state.objective != null) {
                    state.objective.unregister();
                }
                for (Team team : state.teams) {
                    if (team != null) {
                        try {
                            team.unregister();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private static final class PlayerBoard {
        private final Scoreboard scoreboard;
        private @Nullable Objective objective;
        private final Team[] teams = new Team[ENTRY_KEYS.length];
        private @NotNull String section = "";
        private @NotNull String title = "";
        private @NotNull List<String> lines = List.of();
        private int activeLines;

        private PlayerBoard(@NotNull Scoreboard scoreboard, @NotNull Objective objective) {
            this.scoreboard = scoreboard;
            this.objective = objective;
        }

        private void invalidate() {
            section = "";
            title = "";
            lines = List.of();
            activeLines = 0;
        }
    }
}
