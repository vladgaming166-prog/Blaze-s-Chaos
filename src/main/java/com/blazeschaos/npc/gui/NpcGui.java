package com.blazeschaos.npc.gui;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.game.GameModeType;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcGui implements Listener {

    private final BlazesChaosPlugin plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey arenaKey;
    private final NamespacedKey modeKey;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastArena = new ConcurrentHashMap<>();

    public NpcGui(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "npc_gui_action");
        this.arenaKey = new NamespacedKey(plugin, "npc_gui_arena");
        this.modeKey = new NamespacedKey(plugin, "npc_gui_mode");
    }

    public static void openMain(@NotNull BlazesChaosPlugin plugin, @NotNull Player player, @NotNull NpcMode preferred) {
        plugin.npcGui().showMain(player, preferred);
    }

    /**
     * Single Choose Mode GUI for ALL NPCs — Solo / Teams / Mega / Solo Survival / Random / Back.
     */
    public void showChooseMode(@NotNull Player player) {
        ChooseModeHolder holder = new ChooseModeHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27,
                ColorUtil.parse("<gradient:#FF4500:#FFD700><bold>Blaze's Chaos</bold></gradient>"));
        holder.bind(inventory);
        fillBorderSmall(inventory);

        inventory.setItem(4, new ItemBuilder(Material.PAPER)
                .name("<white><bold>Choose Mode</bold></white>")
                .lore(List.of("<gray>Select a game mode to join</gray>"))
                .build());

        inventory.setItem(10, chooseModeItem(NpcMode.SOLO, Material.IRON_SWORD));
        inventory.setItem(11, chooseModeItem(NpcMode.TEAMS, Material.GOLDEN_SWORD));
        inventory.setItem(12, chooseModeItem(NpcMode.MEGA, Material.DIAMOND_SWORD));
        inventory.setItem(13, chooseModeItem(NpcMode.SOLO_SURVIVAL, Material.AMETHYST_SHARD));
        inventory.setItem(14, chooseModeItem(NpcMode.RANDOM, Material.NETHER_STAR));

        inventory.setItem(22, actionItem("choose_back", Material.ARROW, "<red>Back</red>",
                List.of("<gray>Close this menu</gray>")));

        player.openInventory(inventory);
    }

    private @NotNull ItemStack chooseModeItem(@NotNull NpcMode mode, @NotNull Material material) {
        List<String> lore = List.of(
                "<gray>Mode: " + mode.colorName(),
                "",
                mode == NpcMode.RANDOM
                        ? "<yellow>Click for a random mode</yellow>"
                        : "<yellow>Click to join</yellow>"
        );
        ItemStack item = new ItemBuilder(material)
                .name(mode.colorName())
                .lore(lore)
                .build();
        return tag(item, "choose_" + mode.name().toLowerCase(Locale.ROOT), null, mode.name());
    }

    public void showMain(@NotNull Player player, @NotNull NpcMode preferred) {
        MainHolder holder = new MainHolder(preferred);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                ColorUtil.parse("<gradient:#FF4500:#FFD700><bold>Blaze's Chaos</bold></gradient>"));
        holder.bind(inventory);

        fillBorder(inventory);

        inventory.setItem(11, modeItem(NpcMode.SOLO, Material.IRON_SWORD, preferred));
        inventory.setItem(12, modeItem(NpcMode.TEAMS, Material.GOLDEN_SWORD, preferred));
        inventory.setItem(13, modeItem(NpcMode.MEGA, Material.DIAMOND_SWORD, preferred));
        inventory.setItem(14, modeItem(NpcMode.SOLO_SURVIVAL, Material.AMETHYST_SHARD, preferred));
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
                List.of("<gray>Survive chaotic events.</gray>", "<gray>Last player standing — or Solo Survival.</gray>")));

        inventory.setItem(48, actionItem("back", Material.ARROW, "<red>Close</red>", List.of("<gray>Close this menu</gray>")));
        inventory.setItem(49, actionItem("quick", Material.COMPASS, "<gradient:#FF4500:#FFD700><bold>Quick Join</bold></gradient>",
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

    public void showArenaModes(@NotNull Player player, @NotNull Arena arena) {
        ModeHolder holder = new ModeHolder(arena.getName());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                ColorUtil.parse("<white>" + arena.getDisplayName() + "</white> <gray>Modes</gray>"));
        holder.bind(inventory);
        fillBorderSmall(inventory);

        List<GameModeType> modes = plugin.gameManager().modesFor(arena);
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int i = 0;
        for (GameModeType mode : modes) {
            if (i >= slots.length) {
                break;
            }
            inventory.setItem(slots[i++], arenaModeItem(arena, mode));
        }
        inventory.setItem(22, actionItem("maps", Material.ARROW, "<yellow>Back</yellow>",
                List.of("<gray>Return to maps</gray>")));
        player.openInventory(inventory);
    }

    private @NotNull ItemStack arenaModeItem(@NotNull Arena arena, @NotNull GameModeType mode) {
        GameInstance game = plugin.gameManager().get(arena, mode);
        String status = "<green>Waiting</green>";
        if (game != null) {
            if (game.getState().isActive()) {
                status = "<red>Playing</red>";
            } else if (game.getState() == GameState.STARTING) {
                status = "<gold>Starting</gold>";
            }
        }
        int players = game == null ? 0 : game.playerCount();
        int max = game == null ? modeMax(mode, arena) : game.effectiveMaxPlayers();
        List<String> lore = List.of(
                "<gray>Status: " + status,
                "<gray>Players: <aqua>" + players + "</aqua>/<aqua>" + max + "</aqua>",
                "",
                "<yellow>Click to join " + mode.display() + "</yellow>"
        );
        Material icon = switch (mode) {
            case SOLO -> Material.IRON_SWORD;
            case TEAMS -> Material.GOLDEN_SWORD;
            case MEGA -> Material.DIAMOND_SWORD;
            case SOLO_SURVIVAL -> Material.AMETHYST_SHARD;
        };
        ItemStack item = new ItemBuilder(icon)
                .name(mode.colorName())
                .lore(lore)
                .build();
        return tag(item, "join_mode", arena.getName(), mode.name());
    }

    private int modeMax(@NotNull GameModeType mode, @NotNull Arena arena) {
        int configured = plugin.getConfig().getInt("modes.mode-settings." + mode.name() + ".max-players", -1);
        if (configured > 0) {
            return configured;
        }
        return mode.isSoloSurvival() ? 1 : arena.getMaxPlayers();
    }

    private @NotNull ItemStack mapItem(@NotNull Player player, @NotNull Arena arena) {
        boolean multi = plugin.gameManager().multiModeEnabled(arena);
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
        if (multi) {
            lore.add("<gray>Modes:</gray>");
            for (GameModeType mode : plugin.gameManager().modesFor(arena)) {
                lore.add("<dark_gray>▶</dark_gray> " + mode.colorName());
            }
            lore.add("");
            lore.add("<yellow>Click to choose a mode</yellow>");
        } else {
            lore.add("<gray>Difficulty: <white>" + plugin.difficultyManager().get().name().toLowerCase(Locale.ROOT) + "</white>");
            lore.add("");
            lore.add("<yellow>Double-click to join</yellow>");
        }
        lore.add(fav ? "<gold>★ Favorite</gold>" : "<dark_gray>☆ Not favorited</dark_gray>");
        lore.add("<gray>Shift-click to favorite</gray>");
        ItemStack item = new ItemBuilder(icon)
                .name((fav ? "<gold>★ </gold>" : "") + "<white>" + arena.getDisplayName() + "</white>")
                .lore(lore)
                .glow(fav)
                .build();
        return tag(item, multi ? "open_modes" : "join_map", arena.getName(), null);
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
        return tag(item, "mode_" + mode.name().toLowerCase(Locale.ROOT), null, mode.name());
    }

    private @NotNull ItemStack actionItem(@NotNull String action, @NotNull Material material,
                                          @NotNull String name, @NotNull List<String> lore) {
        return tag(new ItemBuilder(material).name(name).lore(lore).build(), action, null, null);
    }

    private @NotNull ItemStack actionItem(@NotNull String action, @NotNull Material material,
                                          @NotNull String name, @NotNull List<String> lore, @NotNull Player player) {
        List<String> resolved = new ArrayList<>();
        for (String line : lore) {
            resolved.add(plugin.placeholders().apply(player, line));
        }
        return tag(new ItemBuilder(material).name(name).lore(resolved).build(), action, null, null);
    }

    private @NotNull ItemStack tag(@NotNull ItemStack item, @NotNull String action,
                                   @Nullable String arena, @Nullable String mode) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            if (arena != null) {
                meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arena);
            }
            if (mode != null) {
                meta.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode);
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
            if (row == 0 || row == inventory.getSize() / 9 - 1 || col == 0 || col == 8) {
                inventory.setItem(i, pane);
            }
        }
    }

    private void fillBorderSmall(@NotNull Inventory inventory) {
        ItemStack pane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < inventory.getSize(); i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == 2 || col == 0 || col == 8) {
                inventory.setItem(i, pane);
            }
        }
    }

    @EventHandler
    public void onClick(@NotNull InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MainHolder) && !(holder instanceof MapsHolder)
                && !(holder instanceof ModeHolder) && !(holder instanceof ChooseModeHolder)) {
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
        String modeName = current.getItemMeta().getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);

        if (holder instanceof ChooseModeHolder) {
            handleChooseModeClick(player, action);
            return;
        }

        if (action.equals("open_modes") && arenaName != null) {
            if (event.isShiftClick()) {
                plugin.npcManager().toggleFavorite(player, arenaName);
                showMaps(player);
                return;
            }
            Arena arena = plugin.arenaManager().get(arenaName);
            if (arena == null) {
                plugin.lang().send(player, "arena.not-found");
                return;
            }
            showArenaModes(player, arena);
            return;
        }

        if (action.equals("join_mode") && arenaName != null && modeName != null) {
            Arena arena = plugin.arenaManager().get(arenaName);
            if (arena == null) {
                plugin.lang().send(player, "arena.not-found");
                return;
            }
            try {
                GameModeType mode = GameModeType.parse(modeName);
                if (mode.isSoloSurvival()) {
                    player.closeInventory();
                    plugin.survivalObjectiveGui().open(player, arena);
                } else {
                    player.closeInventory();
                    plugin.gameManager().join(player, arena, mode);
                }
            } catch (IllegalArgumentException ex) {
                plugin.lang().send(player, "modes.not-available", Map.of("mode", modeName));
            }
            return;
        }

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
                plugin.gameManager().join(player, arena, GameModeType.SOLO);
            } else {
                plugin.lang().send(player, "npc.double-click");
            }
            return;
        }

        switch (action) {
            case "maps" -> showMaps(player);
            case "main" -> showMain(player, NpcMode.SOLO);
            case "quick" -> {
                player.closeInventory();
                plugin.npcManager().quickJoin(player);
            }
            case "mode_solo" -> quickMode(player, GameModeType.SOLO);
            case "mode_teams", "mode_duos", "mode_trios", "mode_squads" -> quickMode(player, GameModeType.TEAMS);
            case "mode_mega" -> quickMode(player, GameModeType.MEGA);
            case "mode_solo_survival" -> {
                player.closeInventory();
                plugin.survivalObjectiveGui().open(player, null);
            }
            case "mode_random" -> {
                player.closeInventory();
                plugin.npcManager().quickJoinRandom(player);
            }
            case "shop" -> {
                player.closeInventory();
                plugin.shopManager().open(player);
            }
            case "stats", "achievements", "leaderboards", "info" -> {
            }
            case "back" -> player.closeInventory();
            default -> {
            }
        }
    }

    private void quickMode(@NotNull Player player, @NotNull GameModeType mode) {
        player.closeInventory();
        Arena arena = plugin.arenaManager().findJoinable();
        if (arena == null) {
            plugin.lang().send(player, "game.no-arenas");
            return;
        }
        plugin.gameManager().join(player, arena, mode);
    }

    private void handleChooseModeClick(@NotNull Player player, @NotNull String action) {
        switch (action) {
            case "choose_back" -> player.closeInventory();
            case "choose_solo" -> quickMode(player, GameModeType.SOLO);
            case "choose_teams", "choose_duos", "choose_trios", "choose_squads" ->
                    quickMode(player, GameModeType.TEAMS);
            case "choose_mega" -> quickMode(player, GameModeType.MEGA);
            case "choose_solo_survival" -> {
                player.closeInventory();
                plugin.survivalObjectiveGui().open(player, null);
            }
            case "choose_random" -> {
                player.closeInventory();
                plugin.npcManager().quickJoinRandom(player);
            }
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

    public static final class ModeHolder implements InventoryHolder {
        private final String arenaName;
        private @Nullable Inventory inventory;

        public ModeHolder(@NotNull String arenaName) {
            this.arenaName = arenaName;
        }

        public @NotNull String arenaName() {
            return arenaName;
        }

        public void bind(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory != null) {
                return inventory;
            }
            return Bukkit.createInventory(this, 27);
        }
    }

    public static final class ChooseModeHolder implements InventoryHolder {
        private @Nullable Inventory inventory;

        public void bind(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory != null) {
                return inventory;
            }
            return Bukkit.createInventory(this, 27);
        }
    }
}
