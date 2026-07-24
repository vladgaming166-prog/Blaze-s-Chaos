package com.blazeschaos.database;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PlayerStats(
        @NotNull UUID uuid,
        @NotNull String name,
        int wins,
        int games,
        int kills,
        int deaths
) {
    public @NotNull PlayerStats withWins(int wins) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths);
    }

    public @NotNull PlayerStats withGames(int games) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths);
    }

    public @NotNull PlayerStats withKills(int kills) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths);
    }

    public @NotNull PlayerStats withDeaths(int deaths) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths);
    }

    public @NotNull PlayerStats withName(@NotNull String name) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths);
    }
}
