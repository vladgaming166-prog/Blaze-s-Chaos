package com.blazeschaos.arena;

import com.blazeschaos.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Arena {

    private final String name;
    private @Nullable Location lobby;
    private @Nullable Location spawn;
    private @Nullable Location spectator;
    private @Nullable String worldName;
    private @Nullable String templateWorldName;
    private int minPlayers = 2;
    private int maxPlayers = 24;
    private double borderSize = 200.0;
    private double borderSpeed = 1.0;
    private double borderDamage = 1.0;
    private int deathHeight = -64;
    private boolean enabled;
    private boolean setupComplete;

    public Arena(@NotNull String name) {
        this.name = name.toLowerCase();
    }

    public @NotNull String getName() {
        return name;
    }

    public @Nullable Location getLobby() {
        return lobby;
    }

    public void setLobby(@Nullable Location lobby) {
        this.lobby = lobby == null ? null : lobby.clone();
    }

    public @Nullable Location getSpawn() {
        return spawn;
    }

    public void setSpawn(@Nullable Location spawn) {
        this.spawn = spawn == null ? null : spawn.clone();
        if (spawn != null && spawn.getWorld() != null) {
            this.worldName = spawn.getWorld().getName();
        }
    }

    public @Nullable Location getSpectator() {
        return spectator;
    }

    public void setSpectator(@Nullable Location spectator) {
        this.spectator = spectator == null ? null : spectator.clone();
    }

    public @Nullable String getWorldName() {
        return worldName;
    }

    public void setWorldName(@Nullable String worldName) {
        this.worldName = worldName;
    }

    public @Nullable String getTemplateWorldName() {
        return templateWorldName;
    }

    public void setTemplateWorldName(@Nullable String templateWorldName) {
        this.templateWorldName = templateWorldName;
    }

    public @Nullable World getWorld() {
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = Math.max(1, minPlayers);
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = Math.max(this.minPlayers, maxPlayers);
    }

    public double getBorderSize() {
        return borderSize;
    }

    public void setBorderSize(double borderSize) {
        this.borderSize = Math.max(10.0, borderSize);
    }

    public double getBorderSpeed() {
        return borderSpeed;
    }

    public void setBorderSpeed(double borderSpeed) {
        this.borderSpeed = Math.max(0.1, borderSpeed);
    }

    public double getBorderDamage() {
        return borderDamage;
    }

    public void setBorderDamage(double borderDamage) {
        this.borderDamage = Math.max(0.0, borderDamage);
    }

    public int getDeathHeight() {
        return deathHeight;
    }

    public void setDeathHeight(int deathHeight) {
        this.deathHeight = deathHeight;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSetupComplete() {
        return setupComplete;
    }

    public void setSetupComplete(boolean setupComplete) {
        this.setupComplete = setupComplete;
    }

    public boolean isReady() {
        return setupComplete
                && lobby != null
                && spawn != null
                && spectator != null
                && worldName != null
                && Bukkit.getWorld(worldName) != null;
    }

    public void bindWorld(@NotNull World world) {
        this.worldName = world.getName();
        if (this.templateWorldName == null) {
            this.templateWorldName = world.getName();
        }
    }

    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lobby", LocationUtil.serialize(lobby));
        map.put("spawn", LocationUtil.serialize(spawn));
        map.put("spectator", LocationUtil.serialize(spectator));
        map.put("world", worldName);
        map.put("template-world", templateWorldName);
        map.put("min-players", minPlayers);
        map.put("max-players", maxPlayers);
        map.put("border-size", borderSize);
        map.put("border-speed", borderSpeed);
        map.put("border-damage", borderDamage);
        map.put("death-height", deathHeight);
        map.put("enabled", enabled);
        map.put("setup-complete", setupComplete);
        return map;
    }

    public static @NotNull Arena deserialize(@NotNull String name, @NotNull ConfigurationSection section) {
        Arena arena = new Arena(name);
        arena.setLobby(LocationUtil.deserialize(section.getConfigurationSection("lobby")));
        arena.setSpawn(LocationUtil.deserialize(section.getConfigurationSection("spawn")));
        arena.setSpectator(LocationUtil.deserialize(section.getConfigurationSection("spectator")));
        arena.setWorldName(section.getString("world"));
        arena.setTemplateWorldName(section.getString("template-world", arena.getWorldName()));
        arena.setMinPlayers(section.getInt("min-players", 2));
        arena.setMaxPlayers(section.getInt("max-players", 24));
        arena.setBorderSize(section.getDouble("border-size", 200.0));
        arena.setBorderSpeed(section.getDouble("border-speed", 1.0));
        arena.setBorderDamage(section.getDouble("border-damage", 1.0));
        arena.setDeathHeight(section.getInt("death-height", -64));
        arena.setEnabled(section.getBoolean("enabled", true));
        arena.setSetupComplete(section.getBoolean("setup-complete", false));
        return arena;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Arena arena)) {
            return false;
        }
        return name.equals(arena.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
