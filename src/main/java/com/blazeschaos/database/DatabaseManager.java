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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
                            deaths INTEGER NOT NULL DEFAULT 0,
                            coins INTEGER NOT NULL DEFAULT 0,
                            owned TEXT NOT NULL DEFAULT '',
                            selected TEXT NOT NULL DEFAULT ''
                        )
                        """);
                migrate(statement);
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to SQLite", exception);
        }
    }

    private void migrate(@NotNull Statement statement) {
        addColumnIfMissing(statement, "coins", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(statement, "owned", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(statement, "selected", "TEXT NOT NULL DEFAULT ''");
    }

    private void addColumnIfMissing(@NotNull Statement statement, @NotNull String column, @NotNull String definition) {
        try {
            statement.executeUpdate("ALTER TABLE player_stats ADD COLUMN " + column + " " + definition);
        } catch (SQLException ignored) {
            // column already exists
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
                "SELECT wins, games, kills, deaths, coins, owned, selected FROM player_stats WHERE uuid = ?")) {
            select.setString(1, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    return new PlayerStats(uuid, name,
                            rs.getInt("wins"),
                            rs.getInt("games"),
                            rs.getInt("kills"),
                            rs.getInt("deaths"),
                            rs.getInt("coins"),
                            parseSet(rs.getString("owned")),
                            parseSet(rs.getString("selected")));
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_stats(uuid, name) VALUES (?, ?)")) {
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
                INSERT INTO player_stats(uuid, name, wins, games, kills, deaths, coins, owned, selected)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    name = excluded.name,
                    wins = excluded.wins,
                    games = excluded.games,
                    kills = excluded.kills,
                    deaths = excluded.deaths,
                    coins = excluded.coins,
                    owned = excluded.owned,
                    selected = excluded.selected
                """)) {
            upsert.setString(1, stats.uuid().toString());
            upsert.setString(2, stats.name());
            upsert.setInt(3, stats.wins());
            upsert.setInt(4, stats.games());
            upsert.setInt(5, stats.kills());
            upsert.setInt(6, stats.deaths());
            upsert.setInt(7, stats.coins());
            upsert.setString(8, joinSet(stats.ownedCosmetics()));
            upsert.setString(9, joinSet(stats.selectedCosmetics()));
            upsert.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to save stats for " + stats.uuid(), exception);
        }
    }

    public void addWin(@NotNull UUID uuid, @NotNull String name) {
        PlayerStats stats = getStats(uuid, name);
        saveStats(stats.withWins(stats.wins() + 1));
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

    public int getCoins(@NotNull UUID uuid, @NotNull String name) {
        return getStats(uuid, name).coins();
    }

    public void addCoins(@NotNull UUID uuid, @NotNull String name, int amount) {
        if (amount == 0) {
            return;
        }
        PlayerStats stats = getStats(uuid, name);
        saveStats(stats.withCoins(stats.coins() + amount));
    }

    public boolean removeCoins(@NotNull UUID uuid, @NotNull String name, int amount) {
        PlayerStats stats = getStats(uuid, name);
        if (stats.coins() < amount) {
            return false;
        }
        saveStats(stats.withCoins(stats.coins() - amount));
        return true;
    }

    public boolean ownsCosmetic(@NotNull UUID uuid, @NotNull String name, @NotNull String id) {
        return getStats(uuid, name).ownedCosmetics().contains(id.toLowerCase());
    }

    public void unlockCosmetic(@NotNull UUID uuid, @NotNull String name, @NotNull String id) {
        PlayerStats stats = getStats(uuid, name);
        Set<String> owned = new HashSet<>(stats.ownedCosmetics());
        owned.add(id.toLowerCase());
        saveStats(stats.withOwned(owned));
    }

    public void selectCosmetic(@NotNull UUID uuid, @NotNull String name, @NotNull String category, @NotNull String id) {
        PlayerStats stats = getStats(uuid, name);
        Set<String> selected = new HashSet<>(stats.selectedCosmetics());
        selected.removeIf(entry -> entry.startsWith(category.toLowerCase() + ":"));
        selected.add(category.toLowerCase() + ":" + id.toLowerCase());
        saveStats(stats.withSelected(selected));
    }

    public void deselectCosmetic(@NotNull UUID uuid, @NotNull String name, @NotNull String category) {
        PlayerStats stats = getStats(uuid, name);
        Set<String> selected = new HashSet<>(stats.selectedCosmetics());
        selected.removeIf(entry -> entry.startsWith(category.toLowerCase() + ":"));
        saveStats(stats.withSelected(selected));
    }

    public boolean isCosmeticSelected(@NotNull UUID uuid, @NotNull String name,
                                      @NotNull String category, @NotNull String id) {
        String key = category.toLowerCase() + ":" + id.toLowerCase();
        return getStats(uuid, name).selectedCosmetics().contains(key);
    }

    private static @NotNull Set<String> parseSet(@NotNull String raw) {
        if (raw == null || raw.isBlank()) {
            return new HashSet<>();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static @NotNull String joinSet(@NotNull Set<String> set) {
        return String.join(",", set);
    }
}
