package com.blazeschaos.config;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.util.ColorUtil;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ConfigManager {

    private final BlazesChaosPlugin plugin;

    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration events;
    private FileConfiguration arenas;
    private FileConfiguration scoreboard;
    private FileConfiguration gui;
    private FileConfiguration worldReset;
    private FileConfiguration permissions;

    public ConfigManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();

        messages = loadYaml("messages.yml");
        events = loadYaml("events.yml");
        arenas = loadYaml("arenas.yml");
        scoreboard = loadYaml("scoreboardconfig.yml");
        gui = loadYaml("gui.yml");
        worldReset = loadYaml("worldreset.yml");
        permissions = loadYaml("permissions.yml");
    }

    public void reloadAll() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        messages = reloadYaml("messages.yml");
        events = reloadYaml("events.yml");
        arenas = reloadYaml("arenas.yml");
        scoreboard = reloadYaml("scoreboardconfig.yml");
        gui = reloadYaml("gui.yml");
        worldReset = reloadYaml("worldreset.yml");
        permissions = reloadYaml("permissions.yml");
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
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        mergeDefaults(yaml, name);
        return yaml;
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

    public @NotNull String rawMessage(@NotNull String path) {
        return messages.getString(path, "<red>Missing message: " + path + "</red>");
    }

    public @NotNull Component message(@NotNull String path) {
        return ColorUtil.parse(prefix() + rawMessage(path));
    }

    public @NotNull Component message(@NotNull String path, @NotNull Map<String, String> placeholders) {
        String text = prefix() + rawMessage(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return ColorUtil.parse(text);
    }

    public void send(@NotNull CommandSender sender, @NotNull String path) {
        sender.sendMessage(message(path));
    }

    public void send(@NotNull CommandSender sender, @NotNull String path, @NotNull Map<String, String> placeholders) {
        sender.sendMessage(message(path, placeholders));
    }

    public @NotNull List<String> helpLines() {
        return messages.getStringList("help");
    }

    public @NotNull String prefix() {
        return messages.getString("prefix", config.getString("settings.prefix", ""));
    }

    public @NotNull FileConfiguration config() {
        return config;
    }

    public @NotNull FileConfiguration messages() {
        return messages;
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

    public @NotNull FileConfiguration gui() {
        return gui;
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
