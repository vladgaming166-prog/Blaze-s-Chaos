package com.blazeschaos.world;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns a small number of passive animals ONLY during active matches.
 * Never runs in server lobby / waiting / starting. Clears on end / reset.
 */
public final class PassiveAnimalManager {

    private final BlazesChaosPlugin plugin;
    private final NamespacedKey tagKey;
    private final ConcurrentHashMap<String, java.util.Set<UUID>> trackedByArena = new ConcurrentHashMap<>();
    private @Nullable BukkitTask task;

    public PassiveAnimalManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        this.tagKey = new NamespacedKey(plugin, "passive_animal");
    }

    public void start() {
        stop();
        if (!enabled()) {
            return;
        }
        int interval = Math.max(100, plugin.getConfig().getInt(
                "chaos-world.passive-animals.check-interval-ticks", 200));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("chaos-world.passive-animals.enabled", true);
    }

    private void tick() {
        if (!enabled()) {
            return;
        }
        for (GameInstance game : plugin.gameManager().all()) {
            GameState state = game.getState();
            // ONLY active matches — never lobby / waiting / starting / ending
            if (!state.isActive()) {
                continue;
            }
            if (game.playerCount() <= 0) {
                continue;
            }
            trySpawnOne(game);
        }
    }

    /**
     * Called when a match becomes PLAYING. Spawns at most one animal immediately
     * (no wave). Further animals refill slowly via the scheduler.
     */
    public void onMatchStart(@NotNull GameInstance game) {
        if (!enabled() || !game.getState().isActive()) {
            return;
        }
        trySpawnOne(game);
    }

    /**
     * Remove every passive animal for this arena (game end / world reset).
     */
    public void clearAnimals(@NotNull Arena arena) {
        World world = arena.getWorld();
        java.util.Set<UUID> tracked = trackedByArena.remove(arena.getName());
        if (tracked != null) {
            for (UUID id : tracked) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            }
            tracked.clear();
        }
        if (world == null) {
            return;
        }
        // Remove any remaining tagged passive animals in this arena world
        for (Entity entity : world.getEntitiesByClass(Animals.class)) {
            if (entity.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }

    private void trySpawnOne(@NotNull GameInstance game) {
        Arena arena = game.getArena();
        World world = arena.getWorld();
        Location center = arena.getCenter() != null ? arena.getCenter() : arena.getSpawn();
        if (world == null || center == null) {
            return;
        }

        if (plugin.getConfig().getBoolean("chaos-world.force-animal-spawn-flags", true)) {
            world.setSpawnFlags(world.getAllowMonsters(), true);
        }

        int max = maxPassiveMobs();
        int existing = countAnimals(arena, world, center);
        if (existing >= max) {
            return;
        }

        // Slow refill: never spawn more than max-spawn-per-check (default 1)
        int maxPerCheck = Math.max(1, plugin.getConfig().getInt(
                "chaos-world.passive-animals.max-spawn-per-check", 1));
        int missing = max - existing;
        int toSpawn = Math.min(maxPerCheck, missing);
        // Extra safety: never more than 2 in a single check
        toSpawn = Math.min(toSpawn, 2);

        List<EntityType> types = configuredTypes();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int radius = Math.max(16, (int) (arena.getBorderSize() / 2.0));
        java.util.Set<UUID> tracked = trackedByArena.computeIfAbsent(
                arena.getName(), k -> ConcurrentHashMap.newKeySet());

        for (int i = 0; i < toSpawn; i++) {
            Location spot = findSafeSpot(world, center, radius, random);
            if (spot == null) {
                continue;
            }
            EntityType type = types.get(random.nextInt(types.size()));
            try {
                Entity entity = world.spawnEntity(spot, type);
                entity.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
                if (entity instanceof LivingEntity living) {
                    living.setRemoveWhenFarAway(false);
                }
                tracked.add(entity.getUniqueId());
            } catch (Exception ex) {
                if (plugin.configs().debug()) {
                    plugin.getLogger().warning("Failed to spawn passive animal: " + ex.getMessage());
                }
            }
        }
        // Prune dead UUIDs
        tracked.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid();
        });
    }

    private int maxPassiveMobs() {
        int max = plugin.getConfig().getInt("chaos-world.passive-animals.max-passive-mobs", -1);
        if (max < 0) {
            max = plugin.getConfig().getInt("chaos-world.passive-animals.target-count", 12);
        }
        return Math.max(0, Math.min(64, max));
    }

    private int countAnimals(@NotNull Arena arena, @NotNull World world, @NotNull Location center) {
        int radius = Math.max(16, (int) (arena.getBorderSize() / 2.0));
        double radiusSq = (double) radius * radius;
        int existing = 0;
        for (Entity entity : world.getEntitiesByClass(Animals.class)) {
            if (!entity.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) {
                // Only count plugin-managed animals toward the cap
                continue;
            }
            if (entity.getLocation().distanceSquared(center) <= radiusSq) {
                existing++;
            }
        }
        return existing;
    }

    private @Nullable Location findSafeSpot(@NotNull World world, @NotNull Location center,
                                            int radius, @NotNull ThreadLocalRandom random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = center.getBlockX() + random.nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + random.nextInt(-radius, radius + 1);
            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight() + 1 || y >= world.getMaxHeight() - 2) {
                continue;
            }
            Location ground = new Location(world, x + 0.5, y, z + 0.5);
            Location feet = ground.clone().add(0, 1, 0);
            Location head = ground.clone().add(0, 2, 0);
            if (!ground.getBlock().getType().isSolid() || ground.getBlock().isLiquid()) {
                continue;
            }
            if (feet.getBlock().getType().isSolid() || feet.getBlock().isLiquid()) {
                continue;
            }
            if (head.getBlock().getType().isSolid()) {
                continue;
            }
            return feet;
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
                EntityType type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
                Class<? extends Entity> clazz = type.getEntityClass();
                if (type.isAlive() && type.isSpawnable() && clazz != null
                        && Animals.class.isAssignableFrom(clazz)) {
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
