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
    private FileConfiguration worldReset;
    private FileConfiguration permissions;
    private LanguageManager languageManager;

    public ConfigManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();

        events = loadYaml("events.yml");
        arenas = loadYaml("arenas.yml");
        scoreboard = loadYaml("scoreboardconfig.yml");
        tablist = loadYaml("tablist.yml");
        worldReset = loadYaml("worldreset.yml");
        permissions = loadYaml("permissions.yml");
        // Keep messages.yml for backwards compatibility but language files are authoritative
        loadYaml("messages.yml");

        languageManager = new LanguageManager(plugin);
        languageManager.load();
    }

    public void reloadAll() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        events = reloadYaml("events.yml");
        arenas = reloadYaml("arenas.yml");
        scoreboard = reloadYaml("scoreboardconfig.yml");
        tablist = reloadYaml("tablist.yml");
        worldReset = reloadYaml("worldreset.yml");
        permissions = reloadYaml("permissions.yml");
        if (languageManager == null) {
            languageManager = new LanguageManager(plugin);
        }
        languageManager.load();
    }

    private @NotNull FileConfiguration loadYaml(@NotNull String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        mergeDefaults(yaml, name);
        return yaml;
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

    public @NotNull FileConfiguration gui() {
        return scoreboard; // GUI removed; keep method for compatibility
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
