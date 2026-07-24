package com.blazeschaos.event;

import com.blazeschaos.game.GameInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ChaosEvent {

    private final String id;
    private final String defaultDisplayName;
    private boolean enabled = true;
    private int durationSeconds = 30;
    private int chance = 10;
    private int cooldownSeconds = 60;
    private long lastEndedAt;
    private @Nullable ConfigurationSection settings;

    protected ChaosEvent(@NotNull String id, @NotNull String defaultDisplayName) {
        this.id = id;
        this.defaultDisplayName = defaultDisplayName;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = Math.max(1, durationSeconds);
    }

    public int getChance() {
        return chance;
    }

    public void setChance(int chance) {
        this.chance = Math.max(0, chance);
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    public boolean isOnCooldown() {
        if (cooldownSeconds <= 0 || lastEndedAt <= 0) {
            return false;
        }
        return (System.currentTimeMillis() - lastEndedAt) < (cooldownSeconds * 1000L);
    }

    public void markEnded() {
        this.lastEndedAt = System.currentTimeMillis();
    }

    public void applyConfig(@NotNull ConfigurationSection root) {
        this.enabled = root.getBoolean("enabled-events." + id, true);
        this.durationSeconds = root.getInt("duration." + id, durationSeconds);
        this.chance = root.getInt("chance." + id, chance);
        this.cooldownSeconds = root.getInt("cooldown." + id, cooldownSeconds);
        this.settings = root.getConfigurationSection("settings." + id);
    }

    public @Nullable ConfigurationSection settings() {
        return settings;
    }

    public int settingInt(@NotNull String path, int def) {
        return settings == null ? def : settings.getInt(path, def);
    }

    public double settingDouble(@NotNull String path, double def) {
        return settings == null ? def : settings.getDouble(path, def);
    }

    public boolean settingBool(@NotNull String path, boolean def) {
        return settings == null ? def : settings.getBoolean(path, def);
    }

    public abstract void start(@NotNull GameInstance game);

    public abstract void tick(@NotNull GameInstance game, int tick);

    public abstract void end(@NotNull GameInstance game);

    public void onPlayerDamage(@NotNull GameInstance game, @NotNull Player victim, @NotNull org.bukkit.event.entity.EntityDamageEvent event) {
        // optional override
    }
}
