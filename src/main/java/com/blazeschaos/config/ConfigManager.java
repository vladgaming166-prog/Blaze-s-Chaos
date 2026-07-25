package com.blazeschaos.config;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
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
    private FileConfiguration npcs;
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
        animations = loadYaml("animations.yml");
        migrateLegacyScoreboardAnimations();
        npcs = loadYaml("npcs.yml");
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
        softSaveMerged("animations.yml", animations);
        softSaveMerged("npcs.yml", npcs);
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
        animations = reloadYaml("animations.yml");
        migrateLegacyScoreboardAnimations();
        npcs = reloadYaml("npcs.yml");
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

    /**
     * Migrates legacy scoreboardanimations.yml into animations.yml.
     * Never overwrites existing animation names. Never deletes user data
     * (legacy file is renamed to a .bak).
     */
    private void migrateLegacyScoreboardAnimations() {
        File legacy = new File(plugin.getDataFolder(), "scoreboardanimations.yml");
        if (!legacy.exists()) {
            return;
        }
        try {
            YamlConfiguration legacyCfg = YamlConfiguration.loadConfiguration(legacy);
            int imported = 0;
            for (String key : legacyCfg.getKeys(false)) {
                if (key.equalsIgnoreCase("config-version")) {
                    continue;
                }
                ConfigurationSection section = legacyCfg.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                if (animations.contains(key) && animations.getConfigurationSection(key) != null) {
                    // Preserve existing animations.yml entry
                    continue;
                }
                animations.createSection(key, section.getValues(true));
                imported++;
            }
            if (imported > 0) {
                save(animations, "animations.yml");
                plugin.getLogger().info("Migrated " + imported
                        + " animation(s) from scoreboardanimations.yml into animations.yml.");
            }
            File backup = new File(plugin.getDataFolder(),
                    "scoreboardanimations.yml.migrated-" + System.currentTimeMillis() + ".bak");
            Files.move(legacy.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Archived legacy scoreboardanimations.yml as " + backup.getName()
                    + " (animations now live in animations.yml only).");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to migrate scoreboardanimations.yml", ex);
        }
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
            final String exactPrefix =
                    "<gradient:#FF4500:#FFD700><bold>Blaze's Chaos</bold></gradient> <gray>»</gray> ";
            for (String key : List.of(
                    "settings.prefix",
                    "prefix",
                    "lobby-items.join.name",
                    "lobby-items.quick-join.name",
                    "lobby.items.join.name",
                    "lobby.items.quick-join.name"
            )) {
                String value = config.getString(key);
                if (value == null) {
                    continue;
                }
                if (key.endsWith("prefix")
                        && (value.contains("%blazechaosanimation_prefix%")
                        || value.contains("<gold><bold>Blaze"))
                        && !value.equals(exactPrefix)) {
                    config.set(key, exactPrefix);
                    changed = true;
                } else if (value.contains("<gold>") && (value.contains("Join Blaze") || value.contains("Quick Join"))) {
                    String restored = value
                            .replace("<gold><bold>", "<gradient:#FF4500:#FFD700><bold>")
                            .replace("</bold></gold>", "</bold></gradient>")
                            .replace("<gold>", "<gradient:#FF4500:#FFD700>")
                            .replace("</gold>", "</gradient>");
                    if (!restored.equals(value)) {
                        config.set(key, restored);
                        changed = true;
                    }
                }
            }
            // Migrate legacy gold/amethyst Chaos Shard recipe → Iron/Coal/Egg pattern
            List<String> shape = config.getStringList("solo-survival.crafting.shape");
            String ingredientG = config.getString("solo-survival.crafting.ingredients.G", "");
            String ingredientA = config.getString("solo-survival.crafting.ingredients.A", "");
            boolean legacyRecipe = shape.equals(List.of("GGG", "GAG", "GGG"))
                    || (ingredientG.equalsIgnoreCase("GOLD_INGOT")
                    && ingredientA.equalsIgnoreCase("AMETHYST_SHARD"));
            if (legacyRecipe) {
                config.set("solo-survival.crafting.shape", List.of("ICI", "CEC", "ICI"));
                config.set("solo-survival.crafting.ingredients", null);
                config.set("solo-survival.crafting.ingredients.I", "IRON_INGOT");
                config.set("solo-survival.crafting.ingredients.C", "COAL");
                config.set("solo-survival.crafting.ingredients.E", "EGG");
                changed = true;
            }
            if (!config.contains("solo-survival.win-time-seconds")) {
                int legacy = config.getInt("solo-survival.survive-seconds", 1200);
                config.set("solo-survival.win-time-seconds", Math.max(1, legacy));
                changed = true;
            }
            if (changed) {
                plugin.saveConfig();
                plugin.getLogger().info("Migrated config.yml defaults (branding / Solo Survival).");
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
        YamlConfiguration jarDefaults = null;
        try (InputStream stream = plugin.getResource(name)) {
            if (stream != null) {
                jarDefaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                jarVersion = jarDefaults.getInt("config-version", 1);
            }
        } catch (IOException ignored) {
        }
        int diskVersion = yaml.getInt("config-version", 0);
        if (diskVersion >= jarVersion) {
            return;
        }

        // animations.yml: add missing defaults only — never overwrite existing animations
        if (name.equals("animations.yml") && jarDefaults != null) {
            try {
                File backup = new File(plugin.getDataFolder(),
                        name + ".v" + diskVersion + "-" + System.currentTimeMillis() + ".bak");
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                int added = 0;
                for (String key : jarDefaults.getKeys(false)) {
                    if (key.equalsIgnoreCase("config-version")) {
                        continue;
                    }
                    if (yaml.contains(key) && yaml.getConfigurationSection(key) != null) {
                        continue;
                    }
                    ConfigurationSection section = jarDefaults.getConfigurationSection(key);
                    if (section != null) {
                        yaml.createSection(key, section.getValues(true));
                    } else {
                        yaml.set(key, jarDefaults.get(key));
                    }
                    added++;
                }
                yaml.set("config-version", jarVersion);
                save(yaml, name);
                animations = yaml;
                plugin.getLogger().info("Upgraded animations.yml to config-version " + jarVersion
                        + " (added " + added + " missing animation(s); existing preserved; backup: "
                        + backup.getName() + ").");
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to upgrade animations.yml", ex);
            }
            return;
        }

        // scoreboard / tablist: refresh jar layout with backup
        if (name.equals("scoreboardconfig.yml") || name.equals("tablist.yml")) {
            try {
                File backup = new File(plugin.getDataFolder(),
                        name + ".v" + diskVersion + "-" + System.currentTimeMillis() + ".bak");
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.saveResource(name, true);
                plugin.getLogger().info("Upgraded " + name + " to config-version " + jarVersion
                        + " (backup: " + backup.getName() + ").");
                File refreshed = new File(plugin.getDataFolder(), name);
                if (name.equals("scoreboardconfig.yml")) {
                    scoreboard = YamlConfiguration.loadConfiguration(refreshed);
                    mergeDefaults(scoreboard, name);
                } else {
                    tablist = YamlConfiguration.loadConfiguration(refreshed);
                    mergeDefaults(tablist, name);
                }
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to upgrade " + name, ex);
            }
            return;
        }

        yaml.set("config-version", jarVersion);
        save(yaml, name);
        plugin.getLogger().info("Updated " + name + " to config-version " + jarVersion
                + " (user values preserved via defaults merge).");
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
            // For animations: only fill missing top-level keys (never clobber user animations)
            if (name.equals("animations.yml")) {
                for (String key : defaults.getKeys(false)) {
                    if (key.equalsIgnoreCase("config-version")) {
                        continue;
                    }
                    if (!yaml.contains(key)) {
                        ConfigurationSection section = defaults.getConfigurationSection(key);
                        if (section != null) {
                            yaml.createSection(key, section.getValues(true));
                        } else {
                            yaml.set(key, defaults.get(key));
                        }
                    }
                }
                if (!yaml.contains("config-version")) {
                    yaml.set("config-version", defaults.getInt("config-version", 1));
                }
                return;
            }
            yaml.setDefaults(defaults);
            yaml.options().copyDefaults(true);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to merge defaults for " + name, exception);
        }
    }

    public void saveArenas() {
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

    /** Unified animations.yml (single animation source). */
    public @NotNull FileConfiguration animations() {
        return animations;
    }

    /** @deprecated use {@link #animations()} */
    public @NotNull FileConfiguration scoreboardAnimations() {
        return animations;
    }

    /** @deprecated use {@link #animations()} */
    public @NotNull FileConfiguration globalAnimations() {
        return animations;
    }

    public @NotNull FileConfiguration npcs() {
        return npcs;
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
