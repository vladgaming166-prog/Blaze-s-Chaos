package com.blazeschaos.game;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum SurvivalObjective {
    SURVIVE,
    CHAOS_SHARD;

    public static @NotNull SurvivalObjective parse(@NotNull String raw) {
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (key) {
            case "SHARD", "CHAOS", "CHAOS_SHARD" -> CHAOS_SHARD;
            default -> SURVIVE;
        };
    }

    public @NotNull String display() {
        return switch (this) {
            case SURVIVE -> "Survive";
            case CHAOS_SHARD -> "Chaos Shard";
        };
    }

    public @NotNull String colorName() {
        return switch (this) {
            case SURVIVE -> "<green>Survive</green>";
            case CHAOS_SHARD -> "<light_purple>Chaos Shard</light_purple>";
        };
    }
}
