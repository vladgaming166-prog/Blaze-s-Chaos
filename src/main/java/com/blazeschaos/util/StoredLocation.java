package com.blazeschaos.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Location data that survives world unload/reload cycles.
 * Coordinates and world name are always kept even when the World object is unavailable.
 */
public final class StoredLocation {

    private final @Nullable String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public StoredLocation(@Nullable String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static @Nullable StoredLocation from(@Nullable Location location) {
        if (location == null) {
            return null;
        }
        String world = location.getWorld() != null ? location.getWorld().getName() : null;
        if (world == null) {
            return null;
        }
        return new StoredLocation(world, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    public static @Nullable StoredLocation deserialize(@Nullable ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        return new StoredLocation(
                worldName,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    public static @Nullable StoredLocation deserialize(@Nullable Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object worldObj = map.get("world");
        if (!(worldObj instanceof String worldName) || worldName.isBlank()) {
            return null;
        }
        return new StoredLocation(
                worldName,
                toDouble(map.get("x")),
                toDouble(map.get("y")),
                toDouble(map.get("z")),
                (float) toDouble(map.get("yaw")),
                (float) toDouble(map.get("pitch"))
        );
    }

    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (worldName != null) {
            map.put("world", worldName);
        }
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("yaw", yaw);
        map.put("pitch", pitch);
        return map;
    }

    public boolean isConfigured() {
        return worldName != null && !worldName.isBlank();
    }

    public @Nullable String worldName() {
        return worldName;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public @Nullable Location toLocation() {
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public @NotNull Location toLocation(@NotNull World fallbackWorld) {
        World world = worldName == null ? fallbackWorld : Bukkit.getWorld(worldName);
        if (world == null) {
            world = fallbackWorld;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public @NotNull StoredLocation withWorld(@NotNull String newWorld) {
        return new StoredLocation(newWorld, x, y, z, yaw, pitch);
    }

    private static double toDouble(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
