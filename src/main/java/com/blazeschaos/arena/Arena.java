package com.blazeschaos.arena;

import com.blazeschaos.game.GameModeType;
import com.blazeschaos.util.StoredLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Arena {

    private final String name;
    private String displayName;
    private @Nullable StoredLocation lobby;
    private @Nullable StoredLocation spawn;
    private @Nullable StoredLocation spectator;
    private @Nullable StoredLocation center;
    private @Nullable StoredLocation victoryAltar;
    private @Nullable String worldName;
    private @Nullable String templateWorldName;
    private int minPlayers = 2;
    private int maxPlayers = 24;
    private int countdownSeconds = 30;
    private int deathmatchAfterSeconds = 600;
    private double borderSize = 200.0;
    private double borderSpeed = 1.0;
    private double borderDamage = 1.0;
    private int deathHeight = -64;
    private int respawnDelayTicks = 0;
    private boolean autoReset = true;
    private boolean enabled = true;
    private boolean setupComplete;
    private boolean allowMultipleModes;
    private final List<GameModeType> availableModes = new ArrayList<>();

    public Arena(@NotNull String name) {
        this.name = name.toLowerCase(Locale.ROOT);
        this.displayName = name;
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull String getDisplayName() {
        return displayName == null || displayName.isBlank() ? name : displayName;
    }

    public void setDisplayName(@NotNull String displayName) {
        this.displayName = displayName;
    }

    public @Nullable StoredLocation getLobbyStored() {
        return lobby;
    }

    public @Nullable Location getLobby() {
        return lobby == null ? null : lobby.toLocation();
    }

    public void setLobby(@Nullable Location location) {
        this.lobby = StoredLocation.from(location);
        if (location != null && location.getWorld() != null && worldName == null) {
            worldName = location.getWorld().getName();
        }
    }

    public @Nullable StoredLocation getSpawnStored() {
        return spawn;
    }

    public @Nullable Location getSpawn() {
        return spawn == null ? null : spawn.toLocation();
    }

    public void setSpawn(@Nullable Location location) {
        this.spawn = StoredLocation.from(location);
        if (location != null && location.getWorld() != null) {
            this.worldName = location.getWorld().getName();
        }
    }

    public @Nullable StoredLocation getSpectatorStored() {
        return spectator;
    }

    public @Nullable Location getSpectator() {
        return spectator == null ? null : spectator.toLocation();
    }

    public void setSpectator(@Nullable Location location) {
        this.spectator = StoredLocation.from(location);
        if (location != null && location.getWorld() != null && worldName == null) {
            worldName = location.getWorld().getName();
        }
    }

    public @Nullable StoredLocation getCenterStored() {
        return center;
    }

    public @Nullable Location getCenter() {
        if (center != null) {
            Location resolved = center.toLocation();
            if (resolved != null) {
                return resolved;
            }
        }
        return getSpawn();
    }

    public void setCenter(@Nullable Location location) {
        this.center = StoredLocation.from(location);
        if (location != null && location.getWorld() != null && worldName == null) {
            worldName = location.getWorld().getName();
        }
    }

    public @Nullable StoredLocation getVictoryAltarStored() {
        return victoryAltar;
    }

    public @Nullable Location getVictoryAltar() {
        return victoryAltar == null ? null : victoryAltar.toLocation();
    }

    public void setVictoryAltar(@Nullable Location location) {
        this.victoryAltar = StoredLocation.from(location);
        if (location != null && location.getWorld() != null && worldName == null) {
            worldName = location.getWorld().getName();
        }
    }

    public boolean isAllowMultipleModes() {
        return allowMultipleModes;
    }

    public void setAllowMultipleModes(boolean allowMultipleModes) {
        this.allowMultipleModes = allowMultipleModes;
    }

    public @NotNull List<GameModeType> getAvailableModes() {
        if (availableModes.isEmpty()) {
            return List.of(GameModeType.SOLO);
        }
        return List.copyOf(availableModes);
    }

    public void setAvailableModes(@NotNull List<GameModeType> modes) {
        availableModes.clear();
        for (GameModeType mode : modes) {
            if (!availableModes.contains(mode)) {
                availableModes.add(mode);
            }
        }
        if (availableModes.isEmpty()) {
            availableModes.add(GameModeType.SOLO);
        }
    }

    public boolean supportsMode(@NotNull GameModeType mode) {
        if (!allowMultipleModes) {
            return mode == GameModeType.SOLO || availableModes.isEmpty() || availableModes.contains(mode);
        }
        return getAvailableModes().contains(mode);
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
        if (this.maxPlayers < this.minPlayers) {
            this.maxPlayers = this.minPlayers;
        }
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = Math.max(this.minPlayers, maxPlayers);
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public void setCountdownSeconds(int countdownSeconds) {
        this.countdownSeconds = Math.max(3, countdownSeconds);
    }

    public int getDeathmatchAfterSeconds() {
        return deathmatchAfterSeconds;
    }

    public void setDeathmatchAfterSeconds(int deathmatchAfterSeconds) {
        this.deathmatchAfterSeconds = Math.max(30, deathmatchAfterSeconds);
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

    public int getRespawnDelayTicks() {
        return respawnDelayTicks;
    }

    public void setRespawnDelayTicks(int respawnDelayTicks) {
        this.respawnDelayTicks = Math.max(0, respawnDelayTicks);
    }

    public boolean isAutoReset() {
        return autoReset;
    }

    public void setAutoReset(boolean autoReset) {
        this.autoReset = autoReset;
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

    public void bindWorld(@NotNull World world) {
        this.worldName = world.getName();
        if (this.templateWorldName == null) {
            this.templateWorldName = world.getName();
        }
        rebindLocationsToWorld(world.getName());
    }

    public void rebindLocationsToWorld(@NotNull String newWorldName) {
        this.worldName = newWorldName;
        if (lobby != null) {
            lobby = lobby.withWorld(newWorldName);
        }
        if (spawn != null) {
            spawn = spawn.withWorld(newWorldName);
        }
        if (spectator != null) {
            spectator = spectator.withWorld(newWorldName);
        }
        if (center != null) {
            center = center.withWorld(newWorldName);
        }
        if (victoryAltar != null) {
            victoryAltar = victoryAltar.withWorld(newWorldName);
        }
    }

    /**
     * Returns missing required setup keys, empty when playable.
     */
    public @NotNull List<String> missingRequirements() {
        List<String> missing = new ArrayList<>();
        if (lobby == null || !lobby.isConfigured()) {
            missing.add("lobby");
        }
        if (spawn == null || !spawn.isConfigured()) {
            missing.add("spawn");
        }
        if (spectator == null || !spectator.isConfigured()) {
            missing.add("spectator");
        }
        if (worldName == null || worldName.isBlank()) {
            missing.add("world");
        }
        return missing;
    }

    /**
     * Marks the arena playable when all required points are configured.
     * Does not require the world to be currently loaded (locations are stored by name).
     */
    public boolean tryMarkReady() {
        List<String> missing = missingRequirements();
        if (!missing.isEmpty()) {
            setupComplete = false;
            return false;
        }
        if (center == null && spawn != null) {
            center = spawn;
        }
        setupComplete = true;
        enabled = true;
        return true;
    }

    public boolean isReady() {
        if (!missingRequirements().isEmpty()) {
            return false;
        }
        // Auto-heal legacy arenas that were saved without finish flag
        if (!setupComplete) {
            setupComplete = true;
        }
        return enabled;
    }

    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("display-name", displayName);
        map.put("lobby", lobby == null ? Map.of() : lobby.serialize());
        map.put("spawn", spawn == null ? Map.of() : spawn.serialize());
        map.put("spectator", spectator == null ? Map.of() : spectator.serialize());
        map.put("center", center == null ? Map.of() : center.serialize());
        map.put("victory-altar", victoryAltar == null ? Map.of() : victoryAltar.serialize());
        map.put("world", worldName);
        map.put("template-world", templateWorldName);
        map.put("min-players", minPlayers);
        map.put("max-players", maxPlayers);
        map.put("countdown-seconds", countdownSeconds);
        map.put("deathmatch-after-seconds", deathmatchAfterSeconds);
        map.put("border-size", borderSize);
        map.put("border-speed", borderSpeed);
        map.put("border-damage", borderDamage);
        map.put("death-height", deathHeight);
        map.put("respawn-delay-ticks", respawnDelayTicks);
        map.put("auto-reset", autoReset);
        map.put("enabled", enabled);
        map.put("setup-complete", setupComplete);
        map.put("allow-multiple-modes", allowMultipleModes);
        List<String> modeNames = new ArrayList<>();
        for (GameModeType mode : getAvailableModes()) {
            modeNames.add(mode.name());
        }
        map.put("available-modes", modeNames);
        return map;
    }

    public static @NotNull Arena deserialize(@NotNull String name, @NotNull ConfigurationSection section) {
        Arena arena = new Arena(name);
        arena.setDisplayName(section.getString("display-name", name));
        arena.lobby = StoredLocation.deserialize(section.getConfigurationSection("lobby"));
        arena.spawn = StoredLocation.deserialize(section.getConfigurationSection("spawn"));
        arena.spectator = StoredLocation.deserialize(section.getConfigurationSection("spectator"));
        arena.center = StoredLocation.deserialize(section.getConfigurationSection("center"));
        arena.victoryAltar = StoredLocation.deserialize(section.getConfigurationSection("victory-altar"));
        arena.setWorldName(section.getString("world"));
        if (arena.worldName == null && arena.spawn != null) {
            arena.worldName = arena.spawn.worldName();
        }
        arena.setTemplateWorldName(section.getString("template-world", arena.getWorldName()));
        arena.setMinPlayers(section.getInt("min-players", 2));
        arena.setMaxPlayers(section.getInt("max-players", 24));
        arena.setCountdownSeconds(section.getInt("countdown-seconds", 30));
        arena.setDeathmatchAfterSeconds(section.getInt("deathmatch-after-seconds", 600));
        arena.setBorderSize(section.getDouble("border-size", 200.0));
        arena.setBorderSpeed(section.getDouble("border-speed", 1.0));
        arena.setBorderDamage(section.getDouble("border-damage", 1.0));
        arena.setDeathHeight(section.getInt("death-height", -64));
        arena.setRespawnDelayTicks(section.getInt("respawn-delay-ticks", 0));
        arena.setAutoReset(section.getBoolean("auto-reset", true));
        arena.setEnabled(section.getBoolean("enabled", true));
        arena.setSetupComplete(section.getBoolean("setup-complete", false));
        arena.setAllowMultipleModes(section.getBoolean("allow-multiple-modes", false));
        List<String> modes = section.getStringList("available-modes");
        if (modes.isEmpty()) {
            arena.setAvailableModes(List.of(GameModeType.SOLO, GameModeType.TEAMS,
                    GameModeType.MEGA, GameModeType.SOLO_SURVIVAL));
        } else {
            arena.setAvailableModes(GameModeType.parseList(modes));
        }
        // Heal ready state for arenas that already have all required points
        if (!arena.missingRequirements().isEmpty()) {
            arena.setupComplete = false;
        } else {
            arena.setupComplete = true;
        }
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
