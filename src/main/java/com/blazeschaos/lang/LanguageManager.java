package com.blazeschaos.lang;

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
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class LanguageManager {

    private final BlazesChaosPlugin plugin;
    private FileConfiguration lang;
    private String languageCode = "en";

    public LanguageManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        languageCode = plugin.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        String fileName = resolveFileName(languageCode);
        ensureLangFiles();
        File file = new File(plugin.getDataFolder(), "lang/" + fileName);
        if (!file.exists()) {
            plugin.saveResource("lang/" + fileName, false);
        }
        lang = YamlConfiguration.loadConfiguration(file);
        mergeDefaults(lang, "lang/" + fileName);
        // Always merge English defaults so missing keys still resolve
        if (!fileName.equals("english.yml")) {
            mergeDefaults(lang, "lang/english.yml");
        }
    }

    private void ensureLangFiles() {
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create lang folder.");
        }
        for (String resource : List.of("lang/english.yml", "lang/romanian.yml")) {
            File out = new File(plugin.getDataFolder(), resource);
            if (!out.exists()) {
                plugin.saveResource(resource, false);
            }
        }
    }

    private @NotNull String resolveFileName(@NotNull String code) {
        return switch (code) {
            case "ro", "romanian", "ro_ro" -> "romanian.yml";
            default -> "english.yml";
        };
    }

    private void mergeDefaults(@NotNull FileConfiguration yaml, @NotNull String resourcePath) {
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            yaml.setDefaults(defaults);
            yaml.options().copyDefaults(true);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to merge language defaults for " + resourcePath, exception);
        }
    }

    public @NotNull String languageCode() {
        return languageCode;
    }

    public boolean setLanguage(@NotNull String code) {
        String normalized = code.toLowerCase(Locale.ROOT);
        if (normalized.equals("english")) {
            normalized = "en";
        } else if (normalized.equals("romanian")) {
            normalized = "ro";
        }
        if (!normalized.equals("en") && !normalized.equals("ro")
                && !normalized.equals("english") && !normalized.equals("romanian")) {
            return false;
        }
        plugin.getConfig().set("language", normalized.equals("ro") || normalized.equals("romanian") ? "ro" : "en");
        plugin.saveConfig();
        load();
        return true;
    }

    public @NotNull String raw(@NotNull String path) {
        String value = lang.getString(path);
        if (value == null) {
            return "<red>Missing message: " + path + "</red>";
        }
        return value;
    }

    public @NotNull String raw(@NotNull String path, @NotNull Map<String, String> placeholders) {
        String text = raw(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
        }
        return text;
    }

    public void send(@NotNull CommandSender sender, @NotNull String path) {
        sender.sendMessage(parseFor(sender, prefix() + raw(path)));
    }

    public void send(@NotNull CommandSender sender, @NotNull String path, @NotNull Map<String, String> placeholders) {
        sender.sendMessage(parseFor(sender, prefix() + raw(path, placeholders)));
    }

    public @NotNull Component component(@NotNull String path) {
        return ColorUtil.parse(prefix() + raw(path));
    }

    public @NotNull Component component(@NotNull String path, @NotNull Map<String, String> placeholders) {
        return ColorUtil.parse(prefix() + raw(path, placeholders));
    }

    public @NotNull Component componentNoPrefix(@NotNull String path) {
        return ColorUtil.parse(raw(path));
    }

    public @NotNull Component componentNoPrefix(@NotNull String path, @NotNull Map<String, String> placeholders) {
        return ColorUtil.parse(raw(path, placeholders));
    }

    private @NotNull Component parseFor(@NotNull CommandSender sender, @NotNull String text) {
        if (sender instanceof org.bukkit.entity.Player player) {
            try {
                return ColorUtil.parse(plugin.placeholders().apply(player, text));
            } catch (Throwable ignored) {
                return ColorUtil.parse(text);
            }
        }
        return ColorUtil.parse(text);
    }

    public @NotNull List<String> list(@NotNull String path) {
        List<String> lines = lang.getStringList(path);
        return lines == null ? Collections.emptyList() : lines;
    }

    public @NotNull String prefix() {
        return raw("prefix");
    }

    public @NotNull FileConfiguration yaml() {
        return lang;
    }
}
