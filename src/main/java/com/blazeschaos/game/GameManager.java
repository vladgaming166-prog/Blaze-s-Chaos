package com.blazeschaos.game;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
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
            plugin.configs().send(player, "game.already-in");
            return false;
        }
        Arena target = arena;
        if (target == null) {
            target = plugin.arenaManager().findJoinable();
        }
        if (target == null || !target.isReady()) {
            plugin.configs().send(player, "arena.not-setup");
            return false;
        }
        GameInstance game = getOrCreate(target);
        if (game.isFull()) {
            plugin.configs().send(player, "game.full");
            return false;
        }
        if (!game.getState().isJoinable()) {
            plugin.configs().send(player, "arena.in-use");
            return false;
        }
        if (game.join(player)) {
            playerArena.put(player.getUniqueId(), target.getName());
            return true;
        }
        return false;
    }

    public boolean leave(@NotNull Player player) {
        GameInstance game = getByPlayer(player);
        if (game == null) {
            plugin.configs().send(player, "game.not-in");
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
        // keep instance for reuse
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
