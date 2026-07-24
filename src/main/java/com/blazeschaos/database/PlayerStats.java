package com.blazeschaos.database;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record PlayerStats(
        @NotNull UUID uuid,
        @NotNull String name,
        int wins,
        int games,
        int kills,
        int deaths,
        int coins,
        @NotNull Set<String> ownedCosmetics,
        @NotNull Set<String> selectedCosmetics
) {
    public PlayerStats(@NotNull UUID uuid, @NotNull String name, int wins, int games, int kills, int deaths) {
        this(uuid, name, wins, games, kills, deaths, 0, new HashSet<>(), new HashSet<>());
    }

    public @NotNull PlayerStats withWins(int wins) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths, coins, ownedCosmetics, selectedCosmetics);
    }

    public @NotNull PlayerStats withGames(int games) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths, coins, ownedCosmetics, selectedCosmetics);
    }

    public @NotNull PlayerStats withKills(int kills) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths, coins, ownedCosmetics, selectedCosmetics);
    }

    public @NotNull PlayerStats withDeaths(int deaths) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths, coins, ownedCosmetics, selectedCosmetics);
    }

    public @NotNull PlayerStats withCoins(int coins) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths, Math.max(0, coins), ownedCosmetics, selectedCosmetics);
    }

    public @NotNull PlayerStats withName(@NotNull String name) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths, coins, ownedCosmetics, selectedCosmetics);
    }

    public @NotNull PlayerStats withOwned(@NotNull Set<String> owned) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths, coins, new HashSet<>(owned), selectedCosmetics);
    }

    public @NotNull PlayerStats withSelected(@NotNull Set<String> selected) {
        return new PlayerStats(uuid, name, wins, games, kills, deaths, coins, ownedCosmetics, new HashSet<>(selected));
    }
}
