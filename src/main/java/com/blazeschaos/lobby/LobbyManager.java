package com.blazeschaos.lobby;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.util.ColorUtil;
import com.blazeschaos.util.ItemBuilder;
import com.blazeschaos.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class LobbyManager {

    private final BlazesChaosPlugin plugin;
    private @Nullable Location lobbyLocation;
    private @Nullable Location npcLocation;

    public LobbyManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        lobbyLocation = LocationUtil.deserialize(config.getConfigurationSection("lobby.location"));
        npcLocation = LocationUtil.deserialize(config.getConfigurationSection("lobby.npc"));
    }

    public void setLobby(@NotNull Location location) {
        this.lobbyLocation = location.clone();
        plugin.getConfig().createSection("lobby.location", LocationUtil.serialize(location));
        plugin.saveConfig();
    }

    public void setNpc(@NotNull Location location) {
        this.npcLocation = location.clone();
        plugin.getConfig().createSection("lobby.npc", LocationUtil.serialize(location));
        plugin.saveConfig();
    }

    public @Nullable Location getLobbyLocation() {
        return lobbyLocation == null ? null : lobbyLocation.clone();
    }

    public @Nullable Location getNpcLocation() {
        return npcLocation == null ? null : npcLocation.clone();
    }

    public boolean teleport(@NotNull Player player) {
        if (lobbyLocation == null) {
            plugin.configs().send(player, "lobby.not-set");
            return false;
        }
        player.teleport(lobbyLocation);
        giveJoinItem(player);
        plugin.configs().send(player, "lobby.teleported");
        return true;
    }

    public void giveJoinItem(@NotNull Player player) {
        FileConfiguration config = plugin.configs().config();
        if (!config.getBoolean("settings.join-item.enabled", true)) {
            return;
        }
        Material material = Material.matchMaterial(config.getString("settings.join-item.material", "BLAZE_POWDER"));
        if (material == null) {
            material = Material.BLAZE_POWDER;
        }
        ItemStack item = new ItemBuilder(material)
                .name(config.getString("settings.join-item.name", "<gold>Join Blaze's Chaos</gold>"))
                .lore(config.getStringList("settings.join-item.lore"))
                .glow(true)
                .build();
        int slot = config.getInt("settings.join-item.slot", 0);
        player.getInventory().setItem(slot, item);
    }

    public void giveLeaveItem(@NotNull Player player) {
        FileConfiguration config = plugin.configs().config();
        if (!config.getBoolean("settings.leave-item.enabled", true)) {
            return;
        }
        Material material = Material.matchMaterial(config.getString("settings.leave-item.material", "RED_BED"));
        if (material == null) {
            material = Material.RED_BED;
        }
        ItemStack item = new ItemBuilder(material)
                .name(config.getString("settings.leave-item.name", "<red>Leave Game</red>"))
                .lore(config.getStringList("settings.leave-item.lore"))
                .build();
        int slot = config.getInt("settings.leave-item.slot", 8);
        player.getInventory().setItem(slot, item);
    }

    public boolean isJoinItem(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null) {
            return false;
        }
        String configured = plugin.configs().config().getString("settings.join-item.name", "");
        return item.getItemMeta().displayName().equals(ColorUtil.parse(configured));
    }

    public boolean isLeaveItem(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null) {
            return false;
        }
        String configured = plugin.configs().config().getString("settings.leave-item.name", "");
        return item.getItemMeta().displayName().equals(ColorUtil.parse(configured));
    }

    public void setupLobbyScoreboard(@NotNull Player player) {
        plugin.scoreboardManager().applyLobby(player);
    }
}
