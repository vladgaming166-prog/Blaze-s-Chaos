package com.blazeschaos.loot;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class LootRarityManager {

    public enum Rarity {
        COMMON, UNCOMMON, NORMAL, MYTHIC, LEGENDARY, EXTREME
    }

    private final BlazesChaosPlugin plugin;
    private Rarity rarity = Rarity.NORMAL;

    public LootRarityManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        rarity = parse(plugin.configs().loot().getString("rarity", "normal"));
    }

    public @NotNull Rarity get() {
        return rarity;
    }

    public boolean set(@NotNull String raw) {
        Rarity parsed;
        try {
            parsed = Rarity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return false;
        }
        this.rarity = parsed;
        plugin.configs().loot().set("rarity", rarity.name().toLowerCase(Locale.ROOT));
        plugin.configs().save(plugin.configs().loot(), "loot.yml");
        return true;
    }

    public static @NotNull Rarity parse(@NotNull String raw) {
        try {
            return Rarity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Rarity.NORMAL;
        }
    }

    public @NotNull String poolPath() {
        return "rarities." + rarity.name().toLowerCase(Locale.ROOT) + ".pool";
    }

    public int itemsMin() {
        return section().getInt("items-per-chest.min", 4);
    }

    public int itemsMax() {
        return section().getInt("items-per-chest.max", 8);
    }

    private @NotNull ConfigurationSection section() {
        FileConfiguration loot = plugin.configs().loot();
        ConfigurationSection section = loot.getConfigurationSection("rarities." + rarity.name().toLowerCase(Locale.ROOT));
        if (section == null) {
            section = loot.createSection("rarities." + rarity.name().toLowerCase(Locale.ROOT));
        }
        return section;
    }
}
