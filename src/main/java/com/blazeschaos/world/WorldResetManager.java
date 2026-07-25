package com.blazeschaos.world;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class WorldResetManager {

    private final BlazesChaosPlugin plugin;
    private final Set<String> skipNames = new HashSet<>();

    public WorldResetManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        reloadSkip();
    }

    public @NotNull Path templatePath(@NotNull Arena arena) {
        Path templates = plugin.getDataFolder().toPath()
                .resolve(plugin.configs().worldReset().getString("template-folder", "templates"));
        return templates.resolve(arena.getName());
    }

    /**
     * Creates a unique temporary world copy for one match instance.
     * Callback runs on the main thread with the loaded world (or null on failure).
     */
    public void createMatchWorld(@NotNull Arena arena, @NotNull String instanceWorldName,
                                 @NotNull Consumer<@Nullable World> callback) {
        ensureTemplate(arena);
        Path templatePath = templatePath(arena);
        if (!Files.isDirectory(templatePath)) {
            // Fallback: use live arena world as template source once
            String sourceName = arena.getWorldName();
            if (sourceName != null) {
                Path source = Bukkit.getWorldContainer().toPath().resolve(sourceName);
                if (Files.isDirectory(source)) {
                    try {
                        Files.createDirectories(templatePath.getParent());
                        copyDirectory(source, templatePath);
                    } catch (IOException ex) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to seed template for " + arena.getName(), ex);
                        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                        return;
                    }
                }
            }
        }
        if (!Files.isDirectory(templatePath)) {
            plugin.getLogger().warning("No template for arena " + arena.getName()
                    + " — cannot create match instance.");
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            return;
        }

        Path target = Bukkit.getWorldContainer().toPath().resolve(instanceWorldName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                deleteDirectory(target);
                copyDirectory(templatePath, target);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Avoid uid collision
                    try {
                        Files.deleteIfExists(target.resolve("uid.dat"));
                        Files.deleteIfExists(target.resolve("session.lock"));
                    } catch (IOException ignored) {
                    }
                    WorldCreator creator = new WorldCreator(instanceWorldName);
                    World loaded = Bukkit.createWorld(creator);
                    if (loaded != null) {
                        loaded.setKeepSpawnInMemory(false);
                        EntityCleanup.wipeMatchEntities(plugin, loaded);
                    }
                    callback.accept(loaded);
                });
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create match world " + instanceWorldName, exception);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            }
        });
    }

    /**
     * Unloads and deletes a temporary match world. Safe if already gone.
     */
    public void destroyMatchWorld(@NotNull String instanceWorldName, @NotNull Runnable onComplete) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(instanceWorldName);
            Location fallback = plugin.lobbyManager().getLobbyLocation();
            if (fallback == null && !Bukkit.getWorlds().isEmpty()) {
                fallback = Bukkit.getWorlds().getFirst().getSpawnLocation();
            }
            if (world != null) {
                EntityCleanup.wipeMatchEntities(plugin, world);
                for (Player player : new ArrayList<>(world.getPlayers())) {
                    if (fallback != null) {
                        player.teleport(fallback);
                    }
                }
                Bukkit.unloadWorld(world, false);
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    deleteDirectory(Bukkit.getWorldContainer().toPath().resolve(instanceWorldName));
                } catch (IOException exception) {
                    plugin.getLogger().log(Level.WARNING, "Failed to delete match world " + instanceWorldName, exception);
                }
                Bukkit.getScheduler().runTask(plugin, onComplete);
            });
        });
    }

    public void reloadSkip() {
        skipNames.clear();
        skipNames.addAll(plugin.configs().worldReset().getStringList("skip"));
    }

    public void ensureTemplate(@NotNull Arena arena) {
        if (!plugin.configs().worldReset().getBoolean("enabled", true)) {
            return;
        }
        String worldName = arena.getWorldName();
        if (worldName == null) {
            return;
        }
        Path templates = plugin.getDataFolder().toPath()
                .resolve(plugin.configs().worldReset().getString("template-folder", "templates"));
        Path templatePath = templates.resolve(arena.getName());
        if (Files.exists(templatePath)) {
            if (arena.getTemplateWorldName() == null) {
                arena.setTemplateWorldName(arena.getName());
            }
            return;
        }
        if (!plugin.configs().worldReset().getBoolean("backup-on-first-load", true)) {
            return;
        }
        Path source = Bukkit.getWorldContainer().toPath().resolve(worldName);
        if (!Files.isDirectory(source)) {
            return;
        }
        try {
            Files.createDirectories(templates);
            copyDirectory(source, templatePath);
            arena.setTemplateWorldName(arena.getName());
            plugin.getLogger().info("Created world template for arena " + arena.getName());
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create template for " + arena.getName(), exception);
        }
    }

    public void resetArenaWorld(@NotNull Arena arena, @NotNull Runnable onComplete) {
        // Entity leak prevention — strip plugin passive animals before unload
        plugin.passiveAnimals().clearAnimals(arena);
        if (!plugin.configs().worldReset().getBoolean("enabled", true)) {
            onComplete.run();
            return;
        }
        String worldName = arena.getWorldName();
        if (worldName == null) {
            onComplete.run();
            return;
        }
        Path templates = plugin.getDataFolder().toPath()
                .resolve(plugin.configs().worldReset().getString("template-folder", "templates"));
        Path templatePath = templates.resolve(arena.getName());
        if (!Files.isDirectory(templatePath)) {
            ensureTemplate(arena);
            onComplete.run();
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            Location fallback = plugin.lobbyManager().getLobbyLocation();
            if (fallback == null) {
                fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst().getSpawnLocation();
            }
            if (world != null) {
                List<Player> occupants = new ArrayList<>(world.getPlayers());
                for (Player player : occupants) {
                    if (fallback != null) {
                        player.teleport(fallback);
                    }
                }
                if (plugin.configs().worldReset().getBoolean("unload-before-reset", true)) {
                    boolean save = plugin.configs().worldReset().getBoolean("save-before-unload", false);
                    Bukkit.unloadWorld(world, save);
                }
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    Path target = Bukkit.getWorldContainer().toPath().resolve(worldName);
                    deleteDirectory(target);
                    copyDirectory(templatePath, target);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        WorldCreator creator = new WorldCreator(worldName);
                        World loaded = Bukkit.createWorld(creator);
                        if (loaded != null) {
                            arena.setWorldName(loaded.getName());
                            refreshLocations(arena, loaded);
                        }
                        onComplete.run();
                    });
                } catch (IOException exception) {
                    plugin.getLogger().log(Level.SEVERE, "World reset failed for " + arena.getName(), exception);
                    Bukkit.getScheduler().runTask(plugin, onComplete);
                }
            });
        });
    }

    private void refreshLocations(@NotNull Arena arena, @NotNull World world) {
        // Keep coordinates; rebind world name so StoredLocations resolve after unload/reload.
        arena.rebindLocationsToWorld(world.getName());
        arena.setWorldName(world.getName());
        plugin.arenaManager().saveArena(arena);
    }

    private void copyDirectory(@NotNull Path source, @NotNull Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                if (shouldSkip(relative)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                if (shouldSkip(relative)) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteDirectory(@NotNull Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean shouldSkip(@NotNull Path relative) {
        if (relative.getNameCount() == 0) {
            return false;
        }
        String first = relative.getName(0).toString().toLowerCase(Locale.ROOT);
        for (String skip : skipNames) {
            if (first.equalsIgnoreCase(skip) || relative.toString().equalsIgnoreCase(skip)) {
                return true;
            }
        }
        return false;
    }
}
