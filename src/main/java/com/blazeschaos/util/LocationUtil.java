package com.blazeschaos.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LocationUtil {

    private LocationUtil() {
    }

    public static @NotNull Map<String, Object> serialize(@Nullable Location location) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (location == null || location.getWorld() == null) {
            return map;
        }
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", location.getYaw());
        map.put("pitch", location.getPitch());
        return map;
    }

    public static @Nullable Location deserialize(@Nullable ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    public static @Nullable Location deserialize(@Nullable Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object worldObj = map.get("world");
        if (!(worldObj instanceof String worldName)) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                toDouble(map.get("x")),
                toDouble(map.get("y")),
                toDouble(map.get("z")),
                (float) toDouble(map.get("yaw")),
                (float) toDouble(map.get("pitch"))
        );
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
