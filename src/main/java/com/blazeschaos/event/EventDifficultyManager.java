package com.blazeschaos.event;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class EventDifficultyManager {

    public enum Difficulty {
        EASY, NORMAL, HARD, HARDCORE, EXTREME, IMPOSSIBLE
    }

    private final BlazesChaosPlugin plugin;
    private Difficulty difficulty = Difficulty.NORMAL;

    public EventDifficultyManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String raw = plugin.configs().config().getString("events-difficulty", "normal");
        difficulty = parse(raw);
    }

    public @NotNull Difficulty get() {
        return difficulty;
    }

    public boolean set(@NotNull String raw) {
        Difficulty parsed;
        try {
            parsed = Difficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return false;
        }
        this.difficulty = parsed;
        plugin.configs().config().set("events-difficulty", parsed.name().toLowerCase(Locale.ROOT));
        plugin.saveConfig();
        return true;
    }

    public static @NotNull Difficulty parse(@NotNull String raw) {
        try {
            return Difficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Difficulty.NORMAL;
        }
    }

    private @NotNull ConfigurationSection section() {
        FileConfiguration config = plugin.configs().config();
        ConfigurationSection section = config.getConfigurationSection("difficulty." + difficulty.name().toLowerCase(Locale.ROOT));
        if (section == null) {
            section = config.createSection("difficulty." + difficulty.name().toLowerCase(Locale.ROOT));
        }
        return section;
    }

    public double frequencyMultiplier() {
        return section().getDouble("frequency-multiplier", defaultFrequency());
    }

    public double intensityMultiplier() {
        return section().getDouble("intensity-multiplier", defaultIntensity());
    }

    public double damageMultiplier() {
        return section().getDouble("damage-multiplier", defaultDamage());
    }

    public double riseSpeedMultiplier() {
        return section().getDouble("rise-speed-multiplier", defaultIntensity());
    }

    public double meteorMultiplier() {
        return section().getDouble("meteor-multiplier", defaultIntensity());
    }

    public double lightningMultiplier() {
        return section().getDouble("lightning-multiplier", defaultIntensity());
    }

    public double mobMultiplier() {
        return section().getDouble("mob-multiplier", defaultIntensity());
    }

    public double explosionMultiplier() {
        return section().getDouble("explosion-multiplier", defaultIntensity());
    }

    public double knockbackMultiplier() {
        return section().getDouble("knockback-multiplier", defaultIntensity());
    }

    public int scaledIntervalSeconds(int base) {
        double freq = Math.max(0.25, frequencyMultiplier());
        return Math.max(5, (int) Math.round(base / freq));
    }

    public int scaledDuration(int base) {
        return Math.max(5, (int) Math.round(base * Math.max(0.5, intensityMultiplier() * 0.85 + 0.15)));
    }

    public int scaleCount(int base) {
        return Math.max(1, (int) Math.round(base * intensityMultiplier()));
    }

    public int scaleTicksFaster(int intervalTicks) {
        return Math.max(5, (int) Math.round(intervalTicks / Math.max(0.5, riseSpeedMultiplier())));
    }

    private double defaultFrequency() {
        return switch (difficulty) {
            case EASY -> 0.7;
            case NORMAL -> 1.0;
            case HARD -> 1.35;
            case HARDCORE -> 1.7;
            case EXTREME -> 2.2;
            case IMPOSSIBLE -> 3.0;
        };
    }

    private double defaultIntensity() {
        return switch (difficulty) {
            case EASY -> 0.7;
            case NORMAL -> 1.0;
            case HARD -> 1.4;
            case HARDCORE -> 1.8;
            case EXTREME -> 2.4;
            case IMPOSSIBLE -> 3.2;
        };
    }

    private double defaultDamage() {
        return switch (difficulty) {
            case EASY -> 0.75;
            case NORMAL -> 1.0;
            case HARD -> 1.35;
            case HARDCORE -> 1.75;
            case EXTREME -> 2.25;
            case IMPOSSIBLE -> 3.0;
        };
    }
}
