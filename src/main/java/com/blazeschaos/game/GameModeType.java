package com.blazeschaos.game;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Playable game modes. Arenas can expose one or more of these when multi-mode is enabled.
 */
public enum GameModeType {
    SOLO,
    TEAMS,
    MEGA,
    SOLO_SURVIVAL;

    public static @NotNull GameModeType parse(@NotNull String raw) {
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (key) {
            case "DUOS", "TRIOS", "SQUADS", "TEAM" -> TEAMS;
            case "SOLO_SURVIVAL", "SOLOSURVIVAL", "SURVIVAL" -> SOLO_SURVIVAL;
            case "MEGA", "MEGABATTLE" -> MEGA;
            default -> valueOf(key);
        };
    }

    public static @NotNull List<GameModeType> parseList(@NotNull List<String> raw) {
        List<GameModeType> out = new ArrayList<>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            try {
                GameModeType mode = parse(entry);
                if (!out.contains(mode)) {
                    out.add(mode);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (out.isEmpty()) {
            out.add(SOLO);
        }
        return out;
    }

    public @NotNull String display() {
        return switch (this) {
            case SOLO -> "Solo";
            case TEAMS -> "Teams";
            case MEGA -> "Mega";
            case SOLO_SURVIVAL -> "Solo Survival";
        };
    }

    public @NotNull String colorName() {
        return switch (this) {
            case SOLO -> "<aqua>Solo</aqua>";
            case TEAMS -> "<green>Teams</green>";
            case MEGA -> "<gold>Mega</gold>";
            case SOLO_SURVIVAL -> "<light_purple>Solo Survival</light_purple>";
        };
    }

    public boolean isSoloSurvival() {
        return this == SOLO_SURVIVAL;
    }
}
