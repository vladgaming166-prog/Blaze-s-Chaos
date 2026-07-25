package com.blazeschaos.game;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GameManager {

    private final BlazesChaosPlugin plugin;
    private final Map<String, GameInstance> games = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerArena = new ConcurrentHashMap<>();

    public GameManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public @NotNull String gameKey(@NotNull Arena arena, @NotNull GameModeType mode) {
        if (!multiModeEnabled(arena)) {
            return arena.getName();
        }
        return arena.getName() + ":" + mode.name().toLowerCase(Locale.ROOT);
    }

    public boolean multiModeEnabled(@NotNull Arena arena) {
        return arena.isAllowMultipleModes()
                || plugin.getConfig().getBoolean("modes.allow-multiple-modes", false);
    }

    public @NotNull List<GameModeType> modesFor(@NotNull Arena arena) {
        if (!multiModeEnabled(arena)) {
            return List.of(GameModeType.SOLO);
        }
        List<String> global = plugin.getConfig().getStringList("modes.available-modes");
        List<GameModeType> fromGlobal = global.isEmpty()
                ? List.of(GameModeType.SOLO, GameModeType.TEAMS, GameModeType.MEGA, GameModeType.SOLO_SURVIVAL)
                : GameModeType.parseList(global);
        List<GameModeType> arenaModes = arena.getAvailableModes();
        List<GameModeType> out = new ArrayList<>();
        for (GameModeType mode : arenaModes) {
            if (fromGlobal.contains(mode) && !out.contains(mode)) {
                out.add(mode);
            }
        }
        if (out.isEmpty()) {
            out.addAll(fromGlobal);
        }
        return out;
    }

    public @NotNull GameInstance getOrCreate(@NotNull Arena arena) {
        return getOrCreate(arena, GameModeType.SOLO);
    }

    public @NotNull GameInstance getOrCreate(@NotNull Arena arena, @NotNull GameModeType mode) {
        GameModeType resolved = multiModeEnabled(arena) ? mode : GameModeType.SOLO;
        String key = gameKey(arena, resolved);
        return games.computeIfAbsent(key, name -> {
            GameInstance game = new GameInstance(plugin, arena, resolved);
            game.startTicking();
            return game;
        });
    }

    public @Nullable GameInstance get(@NotNull String arenaName) {
        return games.get(arenaName.toLowerCase(Locale.ROOT));
    }

    public @Nullable GameInstance get(@NotNull Arena arena, @NotNull GameModeType mode) {
        return games.get(gameKey(arena, mode));
    }

    public @Nullable GameInstance getByPlayer(@NotNull Player player) {
        String key = playerArena.get(player.getUniqueId());
        return key == null ? null : games.get(key);
    }

    public boolean join(@NotNull Player player, @Nullable Arena arena) {
        return join(player, arena, GameModeType.SOLO);
    }

    public boolean join(@NotNull Player player, @Nullable Arena arena, @NotNull GameModeType mode) {
        if (getByPlayer(player) != null) {
            plugin.lang().send(player, "game.already-in");
            return false;
        }
        Arena target = arena;
        if (target == null) {
            target = plugin.arenaManager().findJoinable();
        }
        if (target == null) {
            plugin.lang().send(player, "game.no-arenas");
            return false;
        }
        if (!target.isReady()) {
            plugin.lang().send(player, "arena.not-setup", Map.of(
                    "missing", String.join(", ", target.missingRequirements())
            ));
            return false;
        }
        GameModeType resolved = multiModeEnabled(target) ? mode : GameModeType.SOLO;
        if (multiModeEnabled(target) && !modesFor(target).contains(resolved)) {
            plugin.lang().send(player, "modes.not-available", Map.of("mode", resolved.display()));
            return false;
        }
        if (isArenaWorldBusy(target, resolved)) {
            plugin.lang().send(player, "arena.in-use");
            return false;
        }
        ensureWorldLoaded(target);
        if (target.getSpawn() == null || target.getWorld() == null) {
            plugin.lang().send(player, "arena.not-setup", Map.of("missing", "world"));
            return false;
        }
        GameInstance game = getOrCreate(target, resolved);
        if (game.isFull()) {
            plugin.lang().send(player, "game.full");
            return false;
        }
        if (!game.getState().isJoinable()) {
            plugin.lang().send(player, "arena.in-use");
            return false;
        }
        if (game.join(player)) {
            playerArena.put(player.getUniqueId(), gameKey(target, resolved));
            return true;
        }
        return false;
    }

    /**
     * Same physical arena world can only host one populated/active mode instance at a time.
     */
    private boolean isArenaWorldBusy(@NotNull Arena arena, @NotNull GameModeType joiningMode) {
        String arenaName = arena.getName();
        for (GameInstance game : games.values()) {
            if (!game.getArena().getName().equals(arenaName)) {
                continue;
            }
            if (game.getMode() == joiningMode) {
                continue;
            }
            if (game.playerCount() > 0 || game.getState().isActive()
                    || game.getState() == GameState.STARTING
                    || game.getState() == GameState.ENDING
                    || game.getState() == GameState.RESETTING) {
                return true;
            }
        }
        return false;
    }

    private void ensureWorldLoaded(@NotNull Arena arena) {
        String worldName = arena.getWorldName();
        if (worldName == null) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(worldName));
            if (world != null) {
                arena.rebindLocationsToWorld(world.getName());
            }
        }
    }

    public boolean leave(@NotNull Player player) {
        GameInstance game = getByPlayer(player);
        if (game == null) {
            plugin.lang().send(player, "game.not-in");
            return false;
        }
        game.leave(player, true);
        playerArena.remove(player.getUniqueId());
        return true;
    }

    public void untrack(@NotNull Player player) {
        playerArena.remove(player.getUniqueId());
    }

    public void onGameReset(@NotNull GameInstance game) {
        // keep instance for reuse with restored world
    }

    public @NotNull Collection<GameInstance> all() {
        return games.values();
    }

    public void shutdown() {
        for (GameInstance game : games.values()) {
            plugin.passiveAnimals().clearAnimals(game.getArena());
            game.shutdown();
        }
        games.clear();
        playerArena.clear();
    }
}
