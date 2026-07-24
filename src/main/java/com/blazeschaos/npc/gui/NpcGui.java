package com.blazeschaos.npc.gui;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.game.GameState;
import com.blazeschaos.npc.NpcMode;
import com.blazeschaos.util.ColorUtil;
import com.blazeschaos.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcGui implements Listener {

    private final BlazesChaosPlugin plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey arenaKey;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastArena = new ConcurrentHashMap<>();

    public NpcGui(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "npc_gui_action");
        this.arenaKey = new NamespacedKey(plugin, "npc_gui_arena");
    }

    public static void openMain(@NotNull BlazesChaosPlugin plugin, @NotNull Player player, @NotNull NpcMode preferred) {
        plugin.npcGui().showMain(player, preferred);
    }

    public void showMain(@NotNull Player player, @NotNull NpcMode preferred) {
        MainHolder holder = new MainHolder(preferred);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                ColorUtil.parse("<gold><bold>Blaze's Chaos</bold></gold>"));
        holder.bind(inventory);

        fillBorder(inventory);

        inventory.setItem(11, modeItem(NpcMode.SOLO, Material.IRON_SWORD, preferred));
        inventory.setItem(12, modeItem(NpcMode.DUOS, Material.GOLDEN_SWORD, preferred));
        inventory.setItem(13, modeItem(NpcMode.TRIOS, Material.DIAMOND_SWORD, preferred));
        inventory.setItem(14, modeItem(NpcMode.SQUADS, Material.NETHERITE_SWORD, preferred));
        inventory.setItem(15, modeItem(NpcMode.RANDOM, Material.NETHER_STAR, preferred));

        inventory.setItem(29, actionItem("maps", Material.MAP, "<aqua><bold>Map Selector</bold></aqua>",
                List.of("<gray>Browse arenas and join a map</gray>", "<yellow>Click to open</yellow>")));
        inventory.setItem(30, actionItem("stats", Material.BOOK, "<green><bold>Statistics</bold></green>",
                List.of(
                        "<gray>Wins: <white>%blazechaos_wins%</white></gray>",
                        "<gray>Kills: <white>%blazechaos_kills%</white></gray>",
                        "<gray>Deaths: <white>%blazechaos_deaths%</white></gray>",
                        "<gray>Games: <white>%blazechaos_games%</white></gray>",
                        "<gray>Coins: <gold>%blazechaos_coins%</gold></gray>"
                ), player));
        inventory.setItem(31, actionItem("achievements", Material.GOLDEN_APPLE, "<gold><bold>Achievements</bold></gold>",
                List.of("<gray>Coming soon</gray>")));
        inventory.setItem(32, actionItem("leaderboards", Material.GOLD_BLOCK, "<yellow><bold>Leaderboards</bold></yellow>",
                List.of("<gray>Coming soon</gray>")));
        inventory.setItem(33, actionItem("info", Material.PAPER, "<white><bold>Information</bold></white>",
                List.of("<gray>Survive chaotic events.</gray>", "<gray>Last player standing wins!</gray>")));

        inventory.setItem(48, actionItem("back", Material.ARROW, "<red>Close</red>", List.of("<gray>Close this menu</gray>")));
        inventory.setItem(49, actionItem("quick", Material.COMPASS, "<gold><bold>Quick Join</bold></gold>",
                List.of("<gray>Join the best available arena</gray>", "<yellow>Click to queue</yellow>")));
        inventory.setItem(50, actionItem("shop", Material.EMERALD, "<green>Shop</green>",
                List.of("<gray>Spend your coins</gray>")));

        player.openInventory(inventory);
    }

    public void showMaps(@NotNull Player player) {
        MapsHolder holder = new MapsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54,
                ColorUtil.parse("<aqua><bold>Map Selector</bold></aqua>"));
        holder.bind(inventory);
        fillBorder(inventory);

        int slot = 10;
        for (Arena arena : plugin.arenaManager().all()) {
            if (slot == 17 || slot == 26 || slot == 35) {
                slot += 2;
            }
            if (slot >= 44) {
                break;
            }
            inventory.setItem(slot++, mapItem(player, arena));
        }

        inventory.setItem(48, actionItem("main", Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to menu</gray>")));
        inventory.setItem(49, actionItem("quick", Material.COMPASS, "<gold>Quick Join</gold>",
                List.of("<gray>Best available arena</gray>")));
        player.openInventory(inventory);
    }

    private @NotNull ItemStack mapItem(@NotNull Player player, @NotNull Arena arena) {
        GameInstance game = plugin.gameManager().get(arena.getName());
        String status;
        Material icon;
        if (!arena.isReady()) {
            status = "<red>Setup</red>";
            icon = Material.BARRIER;
        } else if (game == null || game.getState() == GameState.WAITING || game.getState() == GameState.LOBBY) {
            status = "<green>Waiting</green>";
            icon = Material.LIME_CONCRETE;
        } else if (game.getState() == GameState.STARTING) {
            status = "<gold>Starting</gold>";
            icon = Material.YELLOW_CONCRETE;
        } else if (game.getState().isActive()) {
            status = "<red>Playing</red>";
            icon = Material.RED_CONCRETE;
        } else {
            status = "<gray>" + game.getState().display() + "</gray>";
            icon = Material.GRAY_CONCRETE;
        }
        int players = game == null ? 0 : game.playerCount();
        boolean fav = plugin.npcManager().isFavorite(player, arena.getName());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Status: " + status);
        lore.add("<gray>Players: <aqua>" + players + "</aqua><gray>/</gray><aqua>" + arena.getMaxPlayers() + "</aqua>");
        lore.add("<gray>Difficulty: <white>" + plugin.difficultyManager().get().name().toLowerCase() + "</white>");
        lore.add(fav ? "<gold>★ Favorite</gold>" : "<dark_gray>☆ Not favorited</dark_gray>");
        lore.add("");
        lore.add("<yellow>Double-click to join</yellow>");
        lore.add("<gray>Shift-click to favorite</gray>");
        ItemStack item = new ItemBuilder(icon)
                .name((fav ? "<gold>★ </gold>" : "") + "<white>" + arena.getDisplayName() + "</white>")
                .lore(lore)
                .glow(fav)
                .build();
        return tag(item, "join_map", arena.getName());
    }

    private @NotNull ItemStack modeItem(@NotNull NpcMode mode, @NotNull Material material, @NotNull NpcMode preferred) {
        int queue = plugin.npcManager().queueCount(mode);
        List<String> lore = List.of(
                "<gray>Queue: <aqua>" + queue + "</aqua>",
                "<gray>Mode: " + mode.colorName(),
                "",
                "<yellow>Click to Quick Join</yellow>"
        );
        ItemStack item = new ItemBuilder(material)
                .name(mode.colorName())
                .lore(lore)
                .glow(mode == preferred)
                .build();
        return tag(item, "mode_" + mode.name().toLowerCase(), null);
    }

    private @NotNull ItemStack actionItem(@NotNull String action, @NotNull Material material,
                                          @NotNull String name, @NotNull List<String> lore) {
        return tag(new ItemBuilder(material).name(name).lore(lore).build(), action, null);
    }

    private @NotNull ItemStack actionItem(@NotNull String action, @NotNull Material material,
                                          @NotNull String name, @NotNull List<String> lore, @NotNull Player player) {
        List<String> resolved = new ArrayList<>();
        for (String line : lore) {
            resolved.add(plugin.placeholders().apply(player, line));
        }
        return tag(new ItemBuilder(material).name(name).lore(resolved).build(), action, null);
    }

    private @NotNull ItemStack tag(@NotNull ItemStack item, @NotNull String action, @Nullable String arena) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            if (arena != null) {
                meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arena);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorder(@NotNull Inventory inventory) {
        ItemStack pane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < inventory.getSize(); i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                inventory.setItem(i, pane);
            }
        }
    }

    @EventHandler
    public void onClick(@NotNull InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MainHolder) && !(holder instanceof MapsHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || !current.hasItemMeta()) {
            return;
        }
        String action = current.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        String arenaName = current.getItemMeta().getPersistentDataContainer().get(arenaKey, PersistentDataType.STRING);

        if (action.equals("join_map") && arenaName != null) {
            if (event.isShiftClick()) {
                plugin.npcManager().toggleFavorite(player, arenaName);
                showMaps(player);
                return;
            }
            long now = System.currentTimeMillis();
            Long last = lastClick.get(player.getUniqueId());
            String prev = lastArena.get(player.getUniqueId());
            lastClick.put(player.getUniqueId(), now);
            lastArena.put(player.getUniqueId(), arenaName);
            if (last != null && prev != null && prev.equals(arenaName) && now - last < 600) {
                player.closeInventory();
                Arena arena = plugin.arenaManager().get(arenaName);
                if (arena == null) {
                    plugin.lang().send(player, "arena.not-found");
                    return;
                }
                plugin.gameManager().join(player, arena);
            } else {
                plugin.lang().send(player, "npc.double-click");
            }
            return;
        }

        switch (action) {
            case "maps" -> showMaps(player);
            case "main" -> showMain(player, NpcMode.SOLO);
            case "quick", "mode_solo", "mode_duos", "mode_trios", "mode_squads", "mode_random" -> {
                player.closeInventory();
                plugin.npcManager().quickJoin(player);
            }
            case "shop" -> {
                player.closeInventory();
                plugin.shopManager().open(player);
            }
            case "stats", "achievements", "leaderboards", "info" -> {
                // informational items — no-op beyond view
            }
            case "back" -> player.closeInventory();
            default -> {
            }
        }
    }

    public void clearPlayer(@NotNull Player player) {
        UUID id = player.getUniqueId();
        lastClick.remove(id);
        lastArena.remove(id);
    }

    public static final class MainHolder implements InventoryHolder {
        private final NpcMode mode;
        private @Nullable Inventory inventory;

        public MainHolder(@NotNull NpcMode mode) {
            this.mode = mode;
        }

        public @NotNull NpcMode mode() {
            return mode;
        }

        public void bind(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory != null) {
                return inventory;
            }
            return Bukkit.createInventory(this, 54);
        }
    }

    public static final class MapsHolder implements InventoryHolder {
        private @Nullable Inventory inventory;

        public void bind(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory != null) {
                return inventory;
            }
            return Bukkit.createInventory(this, 54);
        }
    }
}
