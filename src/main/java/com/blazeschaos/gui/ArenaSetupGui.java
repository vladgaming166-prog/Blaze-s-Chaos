package com.blazeschaos.gui;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.util.ColorUtil;
import com.blazeschaos.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArenaSetupGui implements Listener {

    private final BlazesChaosPlugin plugin;
    private final Map<UUID, String> editing = new HashMap<>();
    private final Map<UUID, BukkitTask> animations = new HashMap<>();
    private int animFrame;

    public ArenaSetupGui(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(@NotNull Player player, @NotNull Arena arena) {
        editing.put(player.getUniqueId(), arena.getName());
        Inventory inventory = Bukkit.createInventory(new SetupHolder(arena.getName()),
                plugin.configs().gui().getInt("setup.size", 54),
                ColorUtil.parse(plugin.configs().gui().getString("setup.title", "<gold>Arena Setup</gold>")));
        draw(inventory, arena);
        player.openInventory(inventory);
        startAnimation(player, inventory, arena);
    }

    private void draw(@NotNull Inventory inventory, @NotNull Arena arena) {
        inventory.clear();
        FileConfiguration gui = plugin.configs().gui();
        Material border = Material.matchMaterial(gui.getString("setup.border-material", "ORANGE_STAINED_GLASS_PANE"));
        if (border == null) {
            border = Material.ORANGE_STAINED_GLASS_PANE;
        }
        ItemStack borderItem = new ItemBuilder(border).name(" ").build();
        int size = inventory.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == (size / 9) - 1 || col == 0 || col == 8) {
                inventory.setItem(i, borderItem);
            }
        }

        ConfigurationSection items = gui.getConfigurationSection("setup.items");
        if (items == null) {
            return;
        }
        putConfigured(inventory, items, "lobby", arena);
        putConfigured(inventory, items, "spawn", arena);
        putConfigured(inventory, items, "spectator", arena);
        putConfigured(inventory, items, "world", arena, Map.of("%world%", arena.getWorldName() == null ? "none" : arena.getWorldName()));
        putConfigured(inventory, items, "min-players", arena, Map.of("%min%", String.valueOf(arena.getMinPlayers())));
        putConfigured(inventory, items, "max-players", arena, Map.of("%max%", String.valueOf(arena.getMaxPlayers())));
        putConfigured(inventory, items, "border", arena, Map.of("%border%", String.valueOf((int) arena.getBorderSize())));
        putConfigured(inventory, items, "border-speed", arena, Map.of("%speed%", String.valueOf(arena.getBorderSpeed())));
        putConfigured(inventory, items, "border-damage", arena, Map.of("%damage%", String.valueOf(arena.getBorderDamage())));
        putConfigured(inventory, items, "death-height", arena, Map.of("%deathheight%", String.valueOf(arena.getDeathHeight())));
        putConfigured(inventory, items, "save", arena);
        putConfigured(inventory, items, "finish", arena);
    }

    private void putConfigured(@NotNull Inventory inventory, @NotNull ConfigurationSection items,
                               @NotNull String key, @NotNull Arena arena) {
        putConfigured(inventory, items, key, arena, Map.of());
    }

    private void putConfigured(@NotNull Inventory inventory, @NotNull ConfigurationSection items,
                               @NotNull String key, @NotNull Arena arena, @NotNull Map<String, String> placeholders) {
        ConfigurationSection section = items.getConfigurationSection(key);
        if (section == null) {
            return;
        }
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        String name = section.getString("name", key);
        List<String> lore = section.getStringList("lore");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            name = name.replace(entry.getKey(), entry.getValue());
            lore = lore.stream().map(line -> line.replace(entry.getKey(), entry.getValue())).toList();
        }
        ItemStack item = new ItemBuilder(material).name(name).lore(lore).build();
        inventory.setItem(section.getInt("slot", 0), item);
    }

    private void startAnimation(@NotNull Player player, @NotNull Inventory inventory, @NotNull Arena arena) {
        stopAnimation(player.getUniqueId());
        if (!plugin.configs().gui().getBoolean("setup.animation.enabled", true)) {
            return;
        }
        List<String> mats = plugin.configs().gui().getStringList("setup.animation.materials");
        int interval = plugin.configs().gui().getInt("setup.animation.interval-ticks", 10);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() instanceof SetupHolder holder
                    && holder.arenaName().equals(arena.getName())) {
                animFrame++;
                if (!mats.isEmpty()) {
                    Material material = Material.matchMaterial(mats.get(Math.floorMod(animFrame, mats.size())));
                    if (material != null) {
                        ItemStack borderItem = new ItemBuilder(material).name(" ").build();
                        int size = inventory.getSize();
                        for (int i = 0; i < size; i++) {
                            int row = i / 9;
                            int col = i % 9;
                            if (row == 0 || row == (size / 9) - 1 || col == 0 || col == 8) {
                                inventory.setItem(i, borderItem);
                            }
                        }
                    }
                }
            } else {
                stopAnimation(player.getUniqueId());
            }
        }, interval, interval);
        animations.put(player.getUniqueId(), task);
    }

    private void stopAnimation(@NotNull UUID uuid) {
        BukkitTask task = animations.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof SetupHolder holder)) {
            return;
        }
        event.setCancelled(true);
        Arena arena = plugin.arenaManager().get(holder.arenaName());
        if (arena == null) {
            player.closeInventory();
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        int slot = event.getRawSlot();
        ConfigurationSection items = plugin.configs().gui().getConfigurationSection("setup.items");
        if (items == null) {
            return;
        }
        boolean right = event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT;
        if (slot == items.getInt("lobby.slot")) {
            arena.setLobby(player.getLocation());
            plugin.configs().send(player, "arena.saved");
        } else if (slot == items.getInt("spawn.slot")) {
            arena.setSpawn(player.getLocation());
            plugin.configs().send(player, "arena.saved");
        } else if (slot == items.getInt("spectator.slot")) {
            arena.setSpectator(player.getLocation());
            plugin.configs().send(player, "arena.saved");
        } else if (slot == items.getInt("world.slot")) {
            arena.bindWorld(player.getWorld());
            plugin.worldResetManager().ensureTemplate(arena);
            plugin.configs().send(player, "arena.saved");
        } else if (slot == items.getInt("min-players.slot")) {
            arena.setMinPlayers(arena.getMinPlayers() + (right ? -1 : 1));
        } else if (slot == items.getInt("max-players.slot")) {
            arena.setMaxPlayers(arena.getMaxPlayers() + (right ? -1 : 1));
        } else if (slot == items.getInt("border.slot")) {
            arena.setBorderSize(arena.getBorderSize() + (right ? -10 : 10));
        } else if (slot == items.getInt("border-speed.slot")) {
            arena.setBorderSpeed(arena.getBorderSpeed() + (right ? -0.5 : 0.5));
        } else if (slot == items.getInt("border-damage.slot")) {
            arena.setBorderDamage(arena.getBorderDamage() + (right ? -0.5 : 0.5));
        } else if (slot == items.getInt("death-height.slot")) {
            arena.setDeathHeight(arena.getDeathHeight() + (right ? -5 : 5));
        } else if (slot == items.getInt("save.slot")) {
            plugin.arenaManager().save();
            plugin.configs().send(player, "arena.saved");
        } else if (slot == items.getInt("finish.slot")) {
            arena.setSetupComplete(true);
            arena.setEnabled(true);
            plugin.worldResetManager().ensureTemplate(arena);
            plugin.arenaManager().save();
            plugin.configs().send(player, "arena.setup-finished", Map.of("arena", arena.getName()));
            player.closeInventory();
            return;
        }
        plugin.arenaManager().save();
        draw(event.getInventory(), arena);
    }

    @EventHandler
    public void onClose(@NotNull InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            editing.remove(player.getUniqueId());
            stopAnimation(player.getUniqueId());
        }
    }

    public void openJoinMenu(@NotNull Player player) {
        Inventory inventory = Bukkit.createInventory(new JoinHolder(),
                plugin.configs().gui().getInt("join-menu.size", 27),
                ColorUtil.parse(plugin.configs().gui().getString("join-menu.title", "<gold>Select Arena</gold>")));
        Material border = Material.matchMaterial(plugin.configs().gui().getString("join-menu.border-material", "GRAY_STAINED_GLASS_PANE"));
        if (border == null) {
            border = Material.GRAY_STAINED_GLASS_PANE;
        }
        ItemStack borderItem = new ItemBuilder(border).name(" ").build();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, borderItem);
        }
        int slot = 10;
        for (Arena arena : plugin.arenaManager().all()) {
            if (!arena.isReady()) {
                continue;
            }
            var game = plugin.gameManager().get(arena.getName());
            int players = game == null ? 0 : game.playerCount();
            String state = game == null ? "Waiting" : game.getState().display();
            ItemStack icon = new ItemBuilder(Material.BLAZE_POWDER)
                    .name("<gradient:#FF4500:#FFD700>" + arena.getName() + "</gradient>")
                    .lore(List.of(
                            "<gray>Players: <yellow>" + players + "/" + arena.getMaxPlayers() + "</yellow>",
                            "<gray>State: <aqua>" + state + "</aqua>",
                            "",
                            "<yellow>Click to join!</yellow>"
                    ))
                    .glow(true)
                    .build();
            inventory.setItem(slot++, icon);
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= inventory.getSize()) {
                break;
            }
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onJoinClick(@NotNull InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof JoinHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() != Material.BLAZE_POWDER || !current.hasItemMeta()) {
            return;
        }
        Component name = current.getItemMeta().displayName();
        if (name == null) {
            return;
        }
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
        Arena arena = plugin.arenaManager().get(plain);
        if (arena != null) {
            player.closeInventory();
            plugin.gameManager().join(player, arena);
        }
    }

    public record SetupHolder(@NotNull String arenaName) implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return Bukkit.createInventory(this, 9);
        }
    }

    public record JoinHolder() implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return Bukkit.createInventory(this, 9);
        }
    }
}
