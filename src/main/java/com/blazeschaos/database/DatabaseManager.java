package com.blazeschaos.database;

import com.blazeschaos.BlazesChaosPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class DatabaseManager {

    private final BlazesChaosPlugin plugin;
    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private Connection connection;

    public DatabaseManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create plugin data folder.");
            }
            File dbFile = new File(plugin.getDataFolder(), "stats.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_stats (
                            uuid TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            wins INTEGER NOT NULL DEFAULT 0,
                            games INTEGER NOT NULL DEFAULT 0,
                            kills INTEGER NOT NULL DEFAULT 0,
                            deaths INTEGER NOT NULL DEFAULT 0
                        )
                        """);
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to SQLite", exception);
        }
    }

    public void disconnect() {
        cache.clear();
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to close SQLite connection", exception);
        }
    }

    public @NotNull PlayerStats getStats(@NotNull UUID uuid, @NotNull String name) {
        return cache.computeIfAbsent(uuid, id -> loadOrCreate(id, name));
    }

    private @NotNull PlayerStats loadOrCreate(@NotNull UUID uuid, @NotNull String name) {
        if (connection == null) {
            return new PlayerStats(uuid, name, 0, 0, 0, 0);
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT wins, games, kills, deaths FROM player_stats WHERE uuid = ?")) {
            select.setString(1, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    return new PlayerStats(uuid, name,
                            rs.getInt("wins"),
                            rs.getInt("games"),
                            rs.getInt("kills"),
                            rs.getInt("deaths"));
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_stats(uuid, name, wins, games, kills, deaths) VALUES (?, ?, 0, 0, 0, 0)")) {
                insert.setString(1, uuid.toString());
                insert.setString(2, name);
                insert.executeUpdate();
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to load stats for " + uuid, exception);
        }
        return new PlayerStats(uuid, name, 0, 0, 0, 0);
    }

    public void saveStats(@NotNull PlayerStats stats) {
        cache.put(stats.uuid(), stats);
        if (connection == null) {
            return;
        }
        try (PreparedStatement upsert = connection.prepareStatement("""
                INSERT INTO player_stats(uuid, name, wins, games, kills, deaths)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    name = excluded.name,
                    wins = excluded.wins,
                    games = excluded.games,
                    kills = excluded.kills,
                    deaths = excluded.deaths
                """)) {
            upsert.setString(1, stats.uuid().toString());
            upsert.setString(2, stats.name());
            upsert.setInt(3, stats.wins());
            upsert.setInt(4, stats.games());
            upsert.setInt(5, stats.kills());
            upsert.setInt(6, stats.deaths());
            upsert.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to save stats for " + stats.uuid(), exception);
        }
    }

    public void addWin(@NotNull UUID uuid, @NotNull String name) {
        PlayerStats stats = getStats(uuid, name);
        stats = stats.withWins(stats.wins() + 1).withGames(stats.games() + 1);
        saveStats(stats);
    }

    public void addGame(@NotNull UUID uuid, @NotNull String name) {
        PlayerStats stats = getStats(uuid, name);
        saveStats(stats.withGames(stats.games() + 1));
    }

    public void addKill(@NotNull UUID uuid, @NotNull String name) {
        PlayerStats stats = getStats(uuid, name);
        saveStats(stats.withKills(stats.kills() + 1));
    }

    public void addDeath(@NotNull UUID uuid, @NotNull String name) {
        PlayerStats stats = getStats(uuid, name);
        saveStats(stats.withDeaths(stats.deaths() + 1));
    }
}
