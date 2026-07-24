package com.blazeschaos.world;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ensures passive animals exist in arena worlds even when doMobSpawning is false.
 * Hostile mobs are unaffected unless configured.
 */
public final class PassiveAnimalManager {

    private final BlazesChaosPlugin plugin;
    private @Nullable BukkitTask task;

    public PassiveAnimalManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("chaos-world.passive-animals.enabled", true)) {
            return;
        }
        int interval = Math.max(100, plugin.getConfig().getInt("chaos-world.passive-animals.check-interval-ticks", 200));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("chaos-world.passive-animals.enabled", true)) {
            return;
        }
        for (GameInstance game : plugin.gameManager().all()) {
            GameState state = game.getState();
            if (!state.isActive() && state != GameState.WAITING
                    && state != GameState.STARTING && state != GameState.LOBBY) {
                continue;
            }
            ensureAnimals(game.getArena());
        }
    }

    public void ensureAnimals(@NotNull Arena arena) {
        World world = arena.getWorld();
        Location center = arena.getCenter() != null ? arena.getCenter() : arena.getSpawn();
        if (world == null || center == null) {
            return;
        }
        // Soft-enable animal spawning rules without forcing hostiles
        if (plugin.getConfig().getBoolean("chaos-world.force-animal-spawn-flags", true)) {
            world.setSpawnFlags(world.getAllowMonsters(), true);
        }
        int radius = Math.max(16, (int) (arena.getBorderSize() / 2.0));
        int target = Math.max(1, plugin.getConfig().getInt("chaos-world.passive-animals.target-count", 12));
        int maxPerTick = Math.max(1, plugin.getConfig().getInt("chaos-world.passive-animals.max-spawn-per-check", 4));

        int existing = 0;
        double radiusSq = (double) radius * radius;
        for (Entity entity : world.getEntitiesByClass(Animals.class)) {
            if (entity.getLocation().distanceSquared(center) <= radiusSq) {
                existing++;
            }
        }
        if (existing >= target) {
            return;
        }

        List<EntityType> types = configuredTypes();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int toSpawn = Math.min(maxPerTick, target - existing);
        for (int i = 0; i < toSpawn; i++) {
            Location spot = findSafeSpot(world, center, radius, random);
            if (spot == null) {
                continue;
            }
            EntityType type = types.get(random.nextInt(types.size()));
            try {
                world.spawnEntity(spot, type);
            } catch (Exception ex) {
                if (plugin.configs().debug()) {
                    plugin.getLogger().warning("Failed to spawn passive animal: " + ex.getMessage());
                }
            }
        }
    }

    private @Nullable Location findSafeSpot(@NotNull World world, @NotNull Location center,
                                            int radius, @NotNull ThreadLocalRandom random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = center.getBlockX() + random.nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + random.nextInt(-radius, radius + 1);
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            if (!loc.getBlock().getType().isSolid() && !loc.getBlock().isLiquid()) {
                return loc;
            }
        }
        return null;
    }

    private @NotNull List<EntityType> configuredTypes() {
        List<String> raw = plugin.getConfig().getStringList("chaos-world.passive-animals.types");
        List<EntityType> types = new ArrayList<>();
        if (raw.isEmpty()) {
            types.add(EntityType.COW);
            types.add(EntityType.PIG);
            types.add(EntityType.SHEEP);
            types.add(EntityType.CHICKEN);
            types.add(EntityType.RABBIT);
            return types;
        }
        for (String name : raw) {
            try {
                EntityType type = EntityType.valueOf(name.toUpperCase());
                if (type.isAlive() && type.isSpawnable()) {
                    types.add(type);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (types.isEmpty()) {
            types.add(EntityType.COW);
            types.add(EntityType.CHICKEN);
        }
        return types;
    }
}
