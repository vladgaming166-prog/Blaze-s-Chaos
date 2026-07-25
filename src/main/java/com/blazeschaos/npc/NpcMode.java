package com.blazeschaos.npc;

import com.blazeschaos.game.GameModeType;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum NpcMode {
    ALL,
    SOLO,
    DUOS,
    TRIOS,
    SQUADS,
    RANDOM,
    TEAMS,
    MEGA,
    SOLO_SURVIVAL;

    public static @NotNull NpcMode parse(@NotNull String raw) {
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (key) {
            case "SOLOSURVIVAL", "SURVIVAL" -> SOLO_SURVIVAL;
            case "TEAM" -> TEAMS;
            case "ANY", "EVERY", "SELECTOR", "MODES" -> ALL;
            default -> valueOf(key);
        };
    }

    public @NotNull GameModeType toGameMode() {
        return switch (this) {
            case SOLO, ALL -> GameModeType.SOLO;
            case DUOS, TRIOS, SQUADS, TEAMS -> GameModeType.TEAMS;
            case MEGA -> GameModeType.MEGA;
            case SOLO_SURVIVAL -> GameModeType.SOLO_SURVIVAL;
            case RANDOM -> GameModeType.SOLO;
        };
    }

    public @NotNull String display() {
        return switch (this) {
            case ALL -> "All Modes";
            case SOLO -> "Solo";
            case DUOS -> "Duos";
            case TRIOS -> "Trios";
            case SQUADS -> "Squads";
            case RANDOM -> "Random Mode";
            case TEAMS -> "Teams";
            case MEGA -> "Mega";
            case SOLO_SURVIVAL -> "Solo Survival";
        };
    }

    public @NotNull String colorName() {
        return switch (this) {
            case ALL -> "<white>All Modes</white>";
            case SOLO -> "<aqua>Solo</aqua>";
            case DUOS -> "<green>Duos</green>";
            case TRIOS -> "<yellow>Trios</yellow>";
            case SQUADS -> "<light_purple>Squads</light_purple>";
            case RANDOM -> "<gold>Random Mode</gold>";
            case TEAMS -> "<green>Teams</green>";
            case MEGA -> "<gold>Mega</gold>";
            case SOLO_SURVIVAL -> "<light_purple>Solo Survival</light_purple>";
        };
    }

    public boolean isAll() {
        return this == ALL;
    }
}
