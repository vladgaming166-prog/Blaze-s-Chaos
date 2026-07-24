package com.blazeschaos.npc;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum NpcMode {
    SOLO,
    DUOS,
    TRIOS,
    SQUADS,
    RANDOM;

    public static @NotNull NpcMode parse(@NotNull String raw) {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    public @NotNull String display() {
        return switch (this) {
            case SOLO -> "Solo";
            case DUOS -> "Duos";
            case TRIOS -> "Trios";
            case SQUADS -> "Squads";
            case RANDOM -> "Random Queue";
        };
    }

    public @NotNull String colorName() {
        return switch (this) {
            case SOLO -> "<aqua>Solo</aqua>";
            case DUOS -> "<green>Duos</green>";
            case TRIOS -> "<yellow>Trios</yellow>";
            case SQUADS -> "<light_purple>Squads</light_purple>";
            case RANDOM -> "<gold>Random Queue</gold>";
        };
    }
}
