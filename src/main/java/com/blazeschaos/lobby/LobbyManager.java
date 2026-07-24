package com.blazeschaos.lobby;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.util.ColorUtil;
import com.blazeschaos.util.ItemBuilder;
import com.blazeschaos.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class LobbyManager {

    public static final String KEY_BROWSER = "browser";
    public static final String KEY_QUICK_JOIN = "quick_join";
    public static final String KEY_INFO = "info";
    public static final String KEY_SHOP = "shop";
    public static final String KEY_LEAVE = "leave";
    public static final String KEY_LEAVE_GAME = "leave_game";

    private final BlazesChaosPlugin plugin;
    private final NamespacedKey itemKey;
    private @Nullable Location lobbyLocation;
    private @Nullable Location npcLocation;

    public LobbyManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "lobby_item");
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

    public @Nullable World getLobbyWorld() {
        return lobbyLocation == null ? null : lobbyLocation.getWorld();
    }

    public @Nullable Location getNpcLocation() {
        return npcLocation == null ? null : npcLocation.clone();
    }

    public boolean isLobbyWorld(@Nullable World world) {
        World lobbyWorld = getLobbyWorld();
        return world != null && lobbyWorld != null && world.equals(lobbyWorld);
    }

    public boolean teleport(@NotNull Player player) {
        if (lobbyLocation == null) {
            plugin.lang().send(player, "lobby.not-set");
            return false;
        }
        player.teleport(lobbyLocation);
        giveLobbyItems(player);
        plugin.lang().send(player, "lobby.teleported");
        return true;
    }

    public void giveLobbyItems(@NotNull Player player) {
        if (plugin.gameManager().getByPlayer(player) != null) {
            return;
        }
        if (plugin.setupMode().isInSetup(player)) {
            return;
        }
        if (!isLobbyWorld(player.getWorld())) {
            removeLobbyItems(player);
            return;
        }
        FileConfiguration config = plugin.configs().config();
        if (!config.getBoolean("lobby-items.enabled", true)) {
            giveJoinItemLegacy(player);
            return;
        }
        setLobbyItem(player, 0, KEY_BROWSER, Material.COMPASS, "lobby-items.browser");
        setLobbyItem(player, 1, KEY_QUICK_JOIN, Material.NETHER_STAR, "lobby-items.quick-join");
        setLobbyItem(player, 4, KEY_INFO, Material.BOOK, "lobby-items.info");
        setLobbyItem(player, 7, KEY_SHOP, Material.EMERALD, "lobby-items.shop");
        setLobbyItem(player, 8, KEY_LEAVE, Material.RED_BED, "lobby-items.leave");
    }

    public void removeLobbyItems(@NotNull Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isLobbyItem(stack)) {
                player.getInventory().setItem(slot, null);
            }
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isLobbyItem(off)) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    private void giveJoinItemLegacy(@NotNull Player player) {
        FileConfiguration config = plugin.configs().config();
        Material material = Material.matchMaterial(config.getString("settings.join-item.material", "BLAZE_POWDER"));
        if (material == null) {
            material = Material.BLAZE_POWDER;
        }
        ItemStack item = tagged(new ItemBuilder(material)
                .name(config.getString("settings.join-item.name", "<gold>Join</gold>"))
                .lore(config.getStringList("settings.join-item.lore"))
                .glow(true)
                .build(), KEY_QUICK_JOIN);
        player.getInventory().setItem(config.getInt("settings.join-item.slot", 0), item);
    }

    private void setLobbyItem(@NotNull Player player, int slot, @NotNull String id,
                              @NotNull Material fallback, @NotNull String path) {
        FileConfiguration config = plugin.configs().config();
        ConfigurationSection section = config.getConfigurationSection(path);
        Material material = fallback;
        String name = "<white>" + id + "</white>";
        List<String> lore = List.of();
        if (section != null) {
            Material matched = Material.matchMaterial(section.getString("material", fallback.name()));
            if (matched != null) {
                material = matched;
            }
            name = section.getString("name", name);
            lore = section.getStringList("lore");
            slot = section.getInt("slot", slot);
        }
        ItemStack item = tagged(new ItemBuilder(material).name(name).lore(lore).glow(true).build(), id);
        player.getInventory().setItem(slot, item);
    }

    private @NotNull ItemStack tagged(@NotNull ItemStack stack, @NotNull String id) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public @Nullable String lobbyItemId(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public boolean isLobbyItem(@Nullable ItemStack item) {
        return lobbyItemId(item) != null;
    }

    public void giveJoinItem(@NotNull Player player) {
        giveLobbyItems(player);
    }

    public void giveLeaveItem(@NotNull Player player) {
        ItemStack item = tagged(new ItemBuilder(Material.RED_BED)
                .name(plugin.lang().raw("lobby-items.leave-game.name"))
                .lore(plugin.lang().list("lobby-items.leave-game.lore"))
                .build(), KEY_LEAVE_GAME);
        player.getInventory().setItem(8, item);
    }

    public boolean isJoinItem(@Nullable ItemStack item) {
        String id = lobbyItemId(item);
        return KEY_QUICK_JOIN.equals(id) || KEY_BROWSER.equals(id) || isLegacyJoin(item);
    }

    public boolean isLeaveItem(@Nullable ItemStack item) {
        String id = lobbyItemId(item);
        return KEY_LEAVE.equals(id) || KEY_LEAVE_GAME.equals(id) || isLegacyLeave(item);
    }

    private boolean isLegacyJoin(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null) {
            return false;
        }
        String configured = plugin.configs().config().getString("settings.join-item.name", "");
        return item.getItemMeta().displayName().equals(ColorUtil.parse(configured));
    }

    private boolean isLegacyLeave(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null) {
            return false;
        }
        String configured = plugin.configs().config().getString("settings.leave-item.name", "");
        return item.getItemMeta().displayName().equals(ColorUtil.parse(configured));
    }

    public void handleLobbyItem(@NotNull Player player, @NotNull String id) {
        switch (id) {
            case KEY_BROWSER -> openGameBrowser(player);
            case KEY_QUICK_JOIN -> plugin.gameManager().join(player, null);
            case KEY_INFO -> sendInfo(player);
            case KEY_SHOP -> plugin.shopManager().open(player);
            case KEY_LEAVE, KEY_LEAVE_GAME -> {
                if (plugin.gameManager().getByPlayer(player) != null) {
                    plugin.gameManager().leave(player);
                } else if (lobbyLocation != null) {
                    player.teleport(lobbyLocation);
                } else if (player.getWorld() != null) {
                    player.teleport(player.getWorld().getSpawnLocation());
                }
            }
            default -> {
            }
        }
    }

    public void openGameBrowser(@NotNull Player player) {
        plugin.lang().send(player, "browser.header");
        boolean any = false;
        for (var arena : plugin.arenaManager().all()) {
            var game = plugin.gameManager().get(arena.getName());
            String state = game == null ? (arena.isReady() ? "Waiting" : "Setup") : game.getState().display();
            int players = game == null ? 0 : game.playerCount();
            plugin.lang().send(player, "browser.entry", java.util.Map.of(
                    "arena", arena.getDisplayName(),
                    "state", state,
                    "players", String.valueOf(players),
                    "max", String.valueOf(arena.getMaxPlayers())
            ));
            any = true;
        }
        if (!any) {
            plugin.lang().send(player, "browser.empty");
        }
        plugin.lang().send(player, "browser.footer");
    }

    private void sendInfo(@NotNull Player player) {
        for (String line : plugin.lang().list("info.lines")) {
            player.sendMessage(ColorUtil.parse(line
                    .replace("%player%", player.getName())
                    .replace("%coins%", String.valueOf(plugin.coinsManager().balance(player)))));
        }
    }

    public void setupLobbyScoreboard(@NotNull Player player) {
        plugin.scoreboardManager().applyLobby(player);
    }
}
