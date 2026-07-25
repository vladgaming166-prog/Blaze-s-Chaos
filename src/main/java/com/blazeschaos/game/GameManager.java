package com.blazeschaos.game;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import org.bukkit.World;
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

/**
 * Manages independent match instances. Each match gets its own temporary world copy.
 */
public final class GameManager {

    private final BlazesChaosPlugin plugin;
    private final Map<String, GameInstance> games = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerInstance = new ConcurrentHashMap<>();
    private final Map<UUID, Long> preparing = new ConcurrentHashMap<>();

    public GameManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
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
        List<GameModeType> out = new ArrayList<>();
        for (GameModeType mode : arena.getAvailableModes()) {
            if (fromGlobal.contains(mode) && !out.contains(mode)) {
                out.add(mode);
            }
        }
        if (out.isEmpty()) {
            out.addAll(fromGlobal);
        }
        return out;
    }

    public @Nullable GameInstance getByInstanceId(@NotNull String instanceId) {
        return games.get(instanceId);
    }

    public @Nullable GameInstance get(@NotNull String key) {
        GameInstance direct = games.get(key.toLowerCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        // Legacy: arena name → first joinable / any instance
        for (GameInstance game : games.values()) {
            if (game.getArena().getName().equalsIgnoreCase(key)) {
                return game;
            }
        }
        return null;
    }

    public @Nullable GameInstance get(@NotNull Arena arena, @NotNull GameModeType mode) {
        for (GameInstance game : games.values()) {
            if (game.getArena().getName().equals(arena.getName())
                    && game.getMode() == mode
                    && game.getState().isJoinable()
                    && !game.isFull()) {
                return game;
            }
        }
        return null;
    }

    public @Nullable GameInstance getByPlayer(@NotNull Player player) {
        String id = playerInstance.get(player.getUniqueId());
        return id == null ? null : games.get(id);
    }

    public boolean join(@NotNull Player player, @Nullable Arena arena) {
        return join(player, arena, GameModeType.SOLO, null);
    }

    public boolean join(@NotNull Player player, @Nullable Arena arena, @NotNull GameModeType mode) {
        return join(player, arena, mode, null);
    }

    public boolean join(@NotNull Player player, @Nullable Arena arena, @NotNull GameModeType mode,
                        @Nullable SurvivalObjective objective) {
        if (getByPlayer(player) != null) {
            plugin.lang().send(player, "game.already-in");
            return false;
        }
        if (preparing.containsKey(player.getUniqueId())) {
            plugin.lang().send(player, "game.preparing");
            return false;
        }
        Arena target = arena != null ? arena : plugin.arenaManager().findJoinable();
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
        // Solo Survival is its own mode and must NEVER be coerced to Solo.
        // Coercing to Solo reuses last-player-alive victory (1 player = instant win).
        GameModeType resolved;
        if (mode.isSoloSurvival()) {
            resolved = GameModeType.SOLO_SURVIVAL;
        } else if (multiModeEnabled(target)) {
            resolved = mode;
            if (!modesFor(target).contains(resolved)) {
                plugin.lang().send(player, "modes.not-available", Map.of("mode", resolved.display()));
                return false;
            }
        } else {
            resolved = GameModeType.SOLO;
        }

        SurvivalObjective obj = objective;
        if (resolved.isSoloSurvival() && obj == null) {
            obj = SurvivalObjective.SURVIVE;
        }

        // Solo Survival / full instances: always create a fresh instance
        boolean forceNew = resolved.isSoloSurvival()
                || resolved == GameModeType.MEGA
                || plugin.getConfig().getBoolean("modes.always-new-instance", false);

        if (!forceNew) {
            GameInstance existing = findJoinable(target, resolved);
            if (existing != null && existing.isWorldReady()) {
                if (existing.join(player)) {
                    playerInstance.put(player.getUniqueId(), existing.getInstanceId());
                    return true;
                }
            }
        }

        prepareAndJoin(player, target, resolved, obj);
        return true;
    }

    private @Nullable GameInstance findJoinable(@NotNull Arena arena, @NotNull GameModeType mode) {
        for (GameInstance game : games.values()) {
            if (!game.getArena().getName().equals(arena.getName())) {
                continue;
            }
            if (game.getMode() != mode) {
                continue;
            }
            if (!game.getState().isJoinable() || game.isFull() || !game.isWorldReady()) {
                continue;
            }
            return game;
        }
        return null;
    }

    private void prepareAndJoin(@NotNull Player player, @NotNull Arena arena,
                                @NotNull GameModeType mode, @Nullable SurvivalObjective objective) {
        preparing.put(player.getUniqueId(), System.currentTimeMillis());
        plugin.lang().send(player, "game.preparing");

        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String worldName = ("bc_" + arena.getName() + "_" + shortId).toLowerCase(Locale.ROOT);
        // World names: keep reasonably short
        if (worldName.length() > 48) {
            worldName = ("bc_" + shortId).toLowerCase(Locale.ROOT);
        }
        final String instanceId = shortId;
        final String instanceWorld = worldName;

        plugin.worldResetManager().createMatchWorld(arena, instanceWorld, world -> {
            preparing.remove(player.getUniqueId());
            if (!player.isOnline()) {
                if (world != null) {
                    plugin.worldResetManager().destroyMatchWorld(instanceWorld, () -> {
                    });
                }
                return;
            }
            if (world == null) {
                plugin.lang().send(player, "game.instance-failed");
                return;
            }
            if (getByPlayer(player) != null) {
                plugin.worldResetManager().destroyMatchWorld(instanceWorld, () -> {
                });
                plugin.lang().send(player, "game.already-in");
                return;
            }
            GameInstance game = new GameInstance(plugin, arena, mode, instanceId, instanceWorld, objective);
            games.put(instanceId, game);
            game.startTicking();
            if (game.join(player)) {
                playerInstance.put(player.getUniqueId(), instanceId);
            } else {
                games.remove(instanceId);
                plugin.worldResetManager().destroyMatchWorld(instanceWorld, () -> {
                });
                plugin.lang().send(player, "game.full");
            }
        });
    }

    public boolean leave(@NotNull Player player) {
        GameInstance game = getByPlayer(player);
        if (game == null) {
            plugin.lang().send(player, "game.not-in");
            return false;
        }
        game.leave(player, true);
        playerInstance.remove(player.getUniqueId());
        return true;
    }

    public void untrack(@NotNull Player player) {
        playerInstance.remove(player.getUniqueId());
    }

    public void onGameFinished(@NotNull GameInstance game) {
        String id = game.getInstanceId();
        games.remove(id);
        String worldName = game.getInstanceWorldName();
        if (worldName != null) {
            plugin.worldResetManager().destroyMatchWorld(worldName, () -> {
                if (plugin.configs().debug()) {
                    plugin.getLogger().info("Destroyed match world " + worldName);
                }
            });
        }
    }

    public void onGameReset(@NotNull GameInstance game) {
        onGameFinished(game);
    }

    public @NotNull Collection<GameInstance> all() {
        return games.values();
    }

    public void shutdown() {
        for (GameInstance game : new ArrayList<>(games.values())) {
            plugin.passiveAnimals().clearForGame(game);
            World world = game.getInstanceWorld();
            if (world != null) {
                com.blazeschaos.world.EntityCleanup.wipeMatchEntities(plugin, world);
            }
            game.shutdown();
            String worldName = game.getInstanceWorldName();
            if (worldName != null) {
                plugin.worldResetManager().destroyMatchWorld(worldName, () -> {
                });
            }
        }
        games.clear();
        playerInstance.clear();
        preparing.clear();
    }
}
