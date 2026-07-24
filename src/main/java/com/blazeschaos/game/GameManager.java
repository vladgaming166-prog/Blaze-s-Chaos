package com.blazeschaos.game;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
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

    public @NotNull GameInstance getOrCreate(@NotNull Arena arena) {
        return games.computeIfAbsent(arena.getName(), name -> {
            GameInstance game = new GameInstance(plugin, arena);
            game.startTicking();
            return game;
        });
    }

    public @Nullable GameInstance get(@NotNull String arenaName) {
        return games.get(arenaName.toLowerCase());
    }

    public @Nullable GameInstance getByPlayer(@NotNull Player player) {
        String arena = playerArena.get(player.getUniqueId());
        return arena == null ? null : games.get(arena);
    }

    public boolean join(@NotNull Player player, @Nullable Arena arena) {
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
        ensureWorldLoaded(target);
        if (target.getSpawn() == null || target.getWorld() == null) {
            plugin.lang().send(player, "arena.not-setup", Map.of("missing", "world"));
            return false;
        }
        GameInstance game = getOrCreate(target);
        if (game.isFull()) {
            plugin.lang().send(player, "game.full");
            return false;
        }
        if (!game.getState().isJoinable()) {
            plugin.lang().send(player, "arena.in-use");
            return false;
        }
        if (game.join(player)) {
            playerArena.put(player.getUniqueId(), target.getName());
            return true;
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
            game.shutdown();
        }
        games.clear();
        playerArena.clear();
    }
}
