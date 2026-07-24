package com.blazeschaos.arena;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaManager {

    private final BlazesChaosPlugin plugin;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();

    public ArenaManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        arenas.clear();
        FileConfiguration config = plugin.configs().arenas();
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection arenaSection = section.getConfigurationSection(key);
            if (arenaSection == null) {
                continue;
            }
            arenas.put(key.toLowerCase(Locale.ROOT), Arena.deserialize(key, arenaSection));
        }
    }

    public void save() {
        FileConfiguration config = plugin.configs().arenas();
        config.set("arenas", null);
        for (Arena arena : arenas.values()) {
            for (Map.Entry<String, Object> entry : arena.serialize().entrySet()) {
                config.set("arenas." + arena.getName() + "." + entry.getKey(), entry.getValue());
            }
        }
        plugin.configs().saveArenas();
    }

    public void saveArena(@NotNull Arena arena) {
        arenas.put(arena.getName(), arena);
        save();
    }

    public @NotNull Arena create(@NotNull String name) {
        Arena arena = new Arena(name);
        arena.setDisplayName(name);
        arena.setMinPlayers(plugin.configs().config().getInt("settings.min-players", 2));
        arena.setMaxPlayers(plugin.configs().config().getInt("settings.max-players", 24));
        arena.setCountdownSeconds(plugin.configs().config().getInt("settings.countdown-seconds", 30));
        arena.setDeathmatchAfterSeconds(plugin.configs().config().getInt("settings.deathmatch-after-seconds", 600));
        arenas.put(arena.getName(), arena);
        save();
        return arena;
    }

    public boolean delete(@NotNull String name) {
        Arena removed = arenas.remove(name.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        save();
        return true;
    }

    public @Nullable Arena get(@NotNull String name) {
        return arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean exists(@NotNull String name) {
        return arenas.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public @NotNull Collection<Arena> all() {
        return arenas.values();
    }

    public @NotNull List<Arena> readyArenas() {
        List<Arena> ready = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (arena.isReady()) {
                ready.add(arena);
            }
        }
        return ready;
    }

    public @Nullable Arena findJoinable() {
        for (Arena arena : arenas.values()) {
            if (arena.isReady()) {
                return arena;
            }
        }
        return null;
    }
}
