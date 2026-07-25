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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Passive animals exist ONLY during {@link GameState#PLAYING} (and mid-match active states).
 * Spawn a fixed count at match start; replace one killed animal after a delay — never waves.
 */
public final class PassiveAnimalManager implements Listener {

    private final BlazesChaosPlugin plugin;
    private final NamespacedKey tagKey;
    private final NamespacedKey gameKey;
    /** instanceId → living animal UUIDs */
    private final Map<String, java.util.Set<UUID>> byInstance = new ConcurrentHashMap<>();
    private final Map<UUID, String> animalToInstance = new ConcurrentHashMap<>();
    private @Nullable BukkitTask watchdog;
    private boolean eventsRegistered;

    public PassiveAnimalManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        this.tagKey = new NamespacedKey(plugin, "passive_animal");
        this.gameKey = new NamespacedKey(plugin, "passive_game");
    }

    public void start() {
        stop();
        if (!eventsRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            eventsRegistered = true;
        }
        // Watchdog: ensure non-active states stay at ZERO animals
        watchdog = Bukkit.getScheduler().runTaskTimer(plugin, this::watchdogClear, 40L, 40L);
    }

    public void stop() {
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
        for (String id : new ArrayList<>(byInstance.keySet())) {
            clearInstance(id);
        }
    }

    private void watchdogClear() {
        for (GameInstance game : plugin.gameManager().all()) {
            if (!game.getState().isActive()) {
                clearForGame(game);
            }
        }
    }

    public int maxPassiveMobs() {
        int max = plugin.getConfig().getInt("chaos-world.passive-animals.max-passive-mobs", -1);
        if (max < 0) {
            max = plugin.getConfig().getInt("chaos-world.passive-animals.initial-count", 10);
        }
        if (max < 0) {
            max = plugin.getConfig().getInt("chaos-world.passive-animals.target-count", 10);
        }
        return Math.max(0, Math.min(64, max));
    }

    public int replaceDelayTicks() {
        return Math.max(20, plugin.getConfig().getInt(
                "chaos-world.passive-animals.replace-delay-ticks", 100));
    }

    /**
     * Spawn the initial fixed amount when the match enters PLAYING. No more until kills.
     */
    public void onMatchStart(@NotNull GameInstance game) {
        if (!enabled()) {
            return;
        }
        clearForGame(game);
        if (game.getState() != GameState.PLAYING && !game.getState().isActive()) {
            return;
        }
        World world = game.getInstanceWorld();
        Location center = game.centerLocation();
        if (world == null || center == null) {
            return;
        }
        if (plugin.getConfig().getBoolean("chaos-world.force-animal-spawn-flags", true)) {
            world.setSpawnFlags(world.getAllowMonsters(), true);
        }
        int count = maxPassiveMobs();
        List<EntityType> types = configuredTypes();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int radius = Math.max(16, (int) (game.getArena().getBorderSize() / 2.0));
        java.util.Set<UUID> tracked = byInstance.computeIfAbsent(
                game.getInstanceId(), k -> ConcurrentHashMap.newKeySet());

        int spawned = 0;
        for (int i = 0; i < count * 3 && spawned < count; i++) {
            Location spot = findSafeSpot(world, center, radius, random);
            if (spot == null) {
                continue;
            }
            EntityType type = types.get(random.nextInt(types.size()));
            try {
                Entity entity = world.spawnEntity(spot, type);
                tag(entity, game.getInstanceId());
                if (entity instanceof LivingEntity living) {
                    living.setRemoveWhenFarAway(false);
                }
                tracked.add(entity.getUniqueId());
                animalToInstance.put(entity.getUniqueId(), game.getInstanceId());
                spawned++;
            } catch (Exception ex) {
                if (plugin.configs().debug()) {
                    plugin.getLogger().warning("Passive spawn failed: " + ex.getMessage());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimalDeath(@NotNull EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Animals)) {
            return;
        }
        if (!entity.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) {
            return;
        }
        String instanceId = entity.getPersistentDataContainer().get(gameKey, PersistentDataType.STRING);
        if (instanceId == null) {
            instanceId = animalToInstance.remove(entity.getUniqueId());
        } else {
            animalToInstance.remove(entity.getUniqueId());
        }
        if (instanceId == null) {
            return;
        }
        java.util.Set<UUID> tracked = byInstance.get(instanceId);
        if (tracked != null) {
            tracked.remove(entity.getUniqueId());
        }
        GameInstance game = plugin.gameManager().getByInstanceId(instanceId);
        if (game == null || !game.getState().isActive()) {
            return;
        }
        // Replace ONLY ONE after delay — never a wave
        final String id = instanceId;
        Bukkit.getScheduler().runTaskLater(plugin, () -> tryReplaceOne(id), replaceDelayTicks());
    }

    private void tryReplaceOne(@NotNull String instanceId) {
        if (!enabled()) {
            return;
        }
        GameInstance game = plugin.gameManager().getByInstanceId(instanceId);
        if (game == null || !game.getState().isActive()) {
            return;
        }
        java.util.Set<UUID> tracked = byInstance.computeIfAbsent(instanceId, k -> ConcurrentHashMap.newKeySet());
        prune(tracked);
        if (tracked.size() >= maxPassiveMobs()) {
            return; // all slots filled — spawn NOTHING
        }
        World world = game.getInstanceWorld();
        Location center = game.centerLocation();
        if (world == null || center == null) {
            return;
        }
        int radius = Math.max(16, (int) (game.getArena().getBorderSize() / 2.0));
        Location spot = findSafeSpot(world, center, radius, ThreadLocalRandom.current());
        if (spot == null) {
            return;
        }
        List<EntityType> types = configuredTypes();
        EntityType type = types.get(ThreadLocalRandom.current().nextInt(types.size()));
        try {
            Entity entity = world.spawnEntity(spot, type);
            tag(entity, instanceId);
            if (entity instanceof LivingEntity living) {
                living.setRemoveWhenFarAway(false);
            }
            tracked.add(entity.getUniqueId());
            animalToInstance.put(entity.getUniqueId(), instanceId);
        } catch (Exception ignored) {
        }
    }

    public void clearForGame(@NotNull GameInstance game) {
        clearInstance(game.getInstanceId());
        World world = game.getInstanceWorld();
        if (world != null) {
            for (Entity entity : world.getEntitiesByClass(Animals.class)) {
                if (entity.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    /** @deprecated use {@link #clearForGame(GameInstance)} */
    public void clearAnimals(@NotNull Arena arena) {
        for (GameInstance game : plugin.gameManager().all()) {
            if (game.getArena().getName().equals(arena.getName())) {
                clearForGame(game);
            }
        }
    }

    private void clearInstance(@NotNull String instanceId) {
        java.util.Set<UUID> tracked = byInstance.remove(instanceId);
        if (tracked == null) {
            return;
        }
        for (UUID id : tracked) {
            animalToInstance.remove(id);
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        tracked.clear();
    }

    private void tag(@NotNull Entity entity, @NotNull String instanceId) {
        entity.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(gameKey, PersistentDataType.STRING, instanceId);
    }

    private void prune(@NotNull java.util.Set<UUID> tracked) {
        tracked.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            boolean dead = e == null || !e.isValid();
            if (dead) {
                animalToInstance.remove(id);
            }
            return dead;
        });
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("chaos-world.passive-animals.enabled", true);
    }

    private @Nullable Location findSafeSpot(@NotNull World world, @NotNull Location center,
                                            int radius, @NotNull ThreadLocalRandom random) {
        for (int attempt = 0; attempt < 20; attempt++) {
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
