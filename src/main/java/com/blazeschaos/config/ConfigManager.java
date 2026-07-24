package com.blazeschaos.config;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ConfigManager {

    private final BlazesChaosPlugin plugin;

    private FileConfiguration config;
    private FileConfiguration events;
    private FileConfiguration arenas;
    private FileConfiguration scoreboard;
    private FileConfiguration tablist;
    private FileConfiguration animations;
    private FileConfiguration worldReset;
    private FileConfiguration permissions;
    private FileConfiguration shop;
    private FileConfiguration loot;
    private LanguageManager languageManager;

    public ConfigManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        ensureMainConfig();
        config = plugin.getConfig();
        migrateMainConfig();

        events = loadYaml("events.yml");
        arenas = loadYaml("arenas.yml");
        scoreboard = loadYaml("scoreboardconfig.yml");
        tablist = loadYaml("tablist.yml");
        animations = loadYaml("scoreboardanimations.yml");
        worldReset = loadYaml("worldreset.yml");
        permissions = loadYaml("permissions.yml");
        shop = loadYaml("shop.yml");
        loot = loadYaml("loot.yml");
        loadYaml("messages.yml");

        languageManager = new LanguageManager(plugin);
        languageManager.load();
        validateCore();
        softSaveMerged("events.yml", events);
        softSaveMerged("scoreboardconfig.yml", scoreboard);
        softSaveMerged("tablist.yml", tablist);
        softSaveMerged("scoreboardanimations.yml", animations);
        softSaveMerged("loot.yml", loot);
        softSaveMerged("shop.yml", shop);
        softSaveMerged("worldreset.yml", worldReset);
    }

    public void reloadAll() {
        ensureMainConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        migrateMainConfig();
        events = reloadYaml("events.yml");
        arenas = reloadYaml("arenas.yml");
        scoreboard = reloadYaml("scoreboardconfig.yml");
        tablist = reloadYaml("tablist.yml");
        animations = reloadYaml("scoreboardanimations.yml");
        worldReset = reloadYaml("worldreset.yml");
        permissions = reloadYaml("permissions.yml");
        shop = reloadYaml("shop.yml");
        loot = reloadYaml("loot.yml");
        if (languageManager == null) {
            languageManager = new LanguageManager(plugin);
        }
        languageManager.load();
        validateCore();
    }

    private void migrateMainConfig() {
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !config.contains(key)) {
                    config.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                plugin.saveConfig();
                plugin.getLogger().info("Migrated config.yml with new default keys (existing values preserved).");
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to migrate config.yml", exception);
        }
    }

    private void softSaveMerged(@NotNull String name, @NotNull FileConfiguration yaml) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            save(yaml, name);
            return;
        }
        int jarVersion = 1;
        try (InputStream stream = plugin.getResource(name)) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                jarVersion = defaults.getInt("config-version", 1);
            }
        } catch (IOException ignored) {
        }
        int diskVersion = yaml.getInt("config-version", 0);
        if (diskVersion < jarVersion) {
            yaml.set("config-version", jarVersion);
            save(yaml, name);
            plugin.getLogger().info("Updated " + name + " to config-version " + jarVersion
                    + " (user values preserved via defaults merge).");
        }
    }

    private void ensureMainConfig() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveDefaultConfig();
            return;
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        if (file.length() > 20 && loaded.getKeys(false).isEmpty()) {
            regenerate("config.yml", file);
            plugin.reloadConfig();
        }
    }

    private void validateCore() {
        if (config.getConfigurationSection("settings") == null) {
            plugin.getLogger().warning("config.yml missing settings section — defaults will be migrated.");
        }
        if (events.getConfigurationSection("enabled-events") == null) {
            plugin.getLogger().warning("events.yml looks incomplete — defaults will be merged from jar.");
        }
        if (config.getInt("settings.min-players", 2) < 1) {
            config.set("settings.min-players", 1);
        }
        if (config.getInt("settings.chaos-interval-seconds", 60) < 5) {
            config.set("settings.chaos-interval-seconds", 5);
        }
        if (events.getInt("interval-seconds", 60) < 5) {
            events.set("interval-seconds", 5);
        }
        if (events.getInt("max-concurrent-events", 2) < 1) {
            events.set("max-concurrent-events", 1);
        }
    }

    private @NotNull FileConfiguration loadYaml(@NotNull String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (file.length() > 20 && yaml.getKeys(false).isEmpty()) {
            regenerate(name, file);
            yaml = YamlConfiguration.loadConfiguration(file);
        }
        mergeDefaults(yaml, name);
        return yaml;
    }

    private void regenerate(@NotNull String name, @NotNull File file) {
        try {
            File backup = new File(plugin.getDataFolder(), name + ".corrupt-" + System.currentTimeMillis() + ".bak");
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().warning("Corrupted " + name + " detected — backed up to " + backup.getName()
                    + " and regenerated from defaults.");
            plugin.saveResource(name, true);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to regenerate " + name, exception);
        }
    }

    private @NotNull FileConfiguration reloadYaml(@NotNull String name) {
        return loadYaml(name);
    }

    private void mergeDefaults(@NotNull FileConfiguration yaml, @NotNull String name) {
        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            yaml.setDefaults(defaults);
            yaml.options().copyDefaults(true);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to merge defaults for " + name, exception);
        }
    }

    public void saveArenas() {
        // Automatic backup before overwrite
        File arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        if (arenasFile.exists()) {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                plugin.getLogger().warning("Could not create backups folder.");
            } else {
                File backup = new File(backupDir, "arenas-" + System.currentTimeMillis() + ".yml");
                try {
                    Files.copy(arenasFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException exception) {
                    plugin.getLogger().log(Level.WARNING, "Failed to backup arenas.yml", exception);
                }
            }
        }
        save(arenas, "arenas.yml");
    }

    public void save(@NotNull FileConfiguration configuration, @NotNull String name) {
        try {
            configuration.save(new File(plugin.getDataFolder(), name));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + name, exception);
        }
    }

    public @NotNull LanguageManager lang() {
        return languageManager;
    }

    public @NotNull String rawMessage(@NotNull String path) {
        return languageManager.raw(path);
    }

    public @NotNull Component message(@NotNull String path) {
        return languageManager.component(path);
    }

    public @NotNull Component message(@NotNull String path, @NotNull Map<String, String> placeholders) {
        return languageManager.component(path, placeholders);
    }

    public void send(@NotNull CommandSender sender, @NotNull String path) {
        languageManager.send(sender, path);
    }

    public void send(@NotNull CommandSender sender, @NotNull String path, @NotNull Map<String, String> placeholders) {
        languageManager.send(sender, path, placeholders);
    }

    public @NotNull List<String> helpLines() {
        return languageManager.list("help");
    }

    public @NotNull String prefix() {
        return languageManager.prefix();
    }

    public @NotNull FileConfiguration config() {
        return config;
    }

    public @NotNull FileConfiguration messages() {
        return languageManager.yaml();
    }

    public @NotNull FileConfiguration events() {
        return events;
    }

    public @NotNull FileConfiguration arenas() {
        return arenas;
    }

    public @NotNull FileConfiguration scoreboard() {
        return scoreboard;
    }

    public @NotNull FileConfiguration tablist() {
        return tablist;
    }

    public @NotNull FileConfiguration animations() {
        return animations;
    }

    public @NotNull FileConfiguration shop() {
        return shop;
    }

    public @NotNull FileConfiguration loot() {
        return loot;
    }

    public @NotNull FileConfiguration gui() {
        return scoreboard;
    }

    public @NotNull FileConfiguration worldReset() {
        return worldReset;
    }

    public @NotNull FileConfiguration permissions() {
        return permissions;
    }

    public boolean debug() {
        return config.getBoolean("debug", false);
    }

    public void setDebug(boolean value) {
        config.set("debug", value);
        plugin.saveConfig();
    }
}
