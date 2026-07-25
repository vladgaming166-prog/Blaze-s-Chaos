package com.blazeschaos.setup;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.util.ColorUtil;
import com.blazeschaos.util.ItemBuilder;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SetupModeManager implements Listener {

    public enum ChatPrompt {
        ARENA_NAME,
        DISPLAY_NAME,
        MIN_PLAYERS,
        MAX_PLAYERS,
        COUNTDOWN,
        DEATHMATCH,
        BORDER_SIZE,
        BORDER_SPEED,
        BORDER_DAMAGE,
        RESPAWN_DELAY
    }

    private final BlazesChaosPlugin plugin;
    private final Map<UUID, SetupSession> sessions = new ConcurrentHashMap<>();

    public SetupModeManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isInSetup(@NotNull Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public @Nullable SetupSession session(@NotNull Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void enter(@NotNull Player player) {
        if (isInSetup(player)) {
            exit(player, false);
        }
        SetupSession session = new SetupSession();
        List<Arena> arenas = new ArrayList<>(plugin.arenaManager().all());
        if (arenas.size() == 1) {
            session.arenaName = arenas.getFirst().getName();
        } else if (!arenas.isEmpty()) {
            session.arenaName = arenas.getFirst().getName();
        }
        sessions.put(player.getUniqueId(), session);
        giveItems(player);
        if (session.arenaName != null) {
            plugin.lang().send(player, "setup.entered", Map.of("arena", session.arenaName));
        } else {
            plugin.lang().send(player, "setup.entered-select");
            session.prompt = ChatPrompt.ARENA_NAME;
            plugin.lang().send(player, "arena.prompt-name");
        }
        plugin.lang().send(player, "setup.items-given");
    }

    public void enter(@NotNull Player player, @NotNull Arena arena) {
        if (isInSetup(player)) {
            exit(player, false);
        }
        SetupSession session = new SetupSession();
        session.arenaName = arena.getName();
        sessions.put(player.getUniqueId(), session);
        giveItems(player);
        plugin.lang().send(player, "setup.entered", Map.of("arena", arena.getName()));
        plugin.lang().send(player, "setup.items-given");
    }

    public void exit(@NotNull Player player, boolean announce) {
        SetupSession removed = sessions.remove(player.getUniqueId());
        if (removed == null) {
            return;
        }
        player.getInventory().clear();
        if (announce) {
            plugin.lang().send(player, "setup.exited");
        }
    }

    private void giveItems(@NotNull Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setItem(0, item("select-arena", Material.COMPASS));
        inv.setItem(1, item("create-arena", Material.NETHER_STAR));
        inv.setItem(2, item("set-lobby", Material.STICK));
        inv.setItem(3, item("set-spawn", Material.DIAMOND_BLOCK));
        inv.setItem(4, item("set-spectator", Material.ENDER_EYE));
        inv.setItem(5, item("set-center", Material.GOLD_BLOCK));
        inv.setItem(6, item("set-death-height", Material.REDSTONE));
        inv.setItem(7, item("configure-border", Material.BEACON));
        inv.setItem(8, item("save-arena", Material.EMERALD));
        // Extra tools in inventory rows
        inv.setItem(9, item("configure-loot", Material.CHEST));
        inv.setItem(10, item("arena-info", Material.BOOK));
        inv.setItem(11, item("arena-settings", Material.PAPER));
        inv.setItem(17, item("cancel", Material.BARRIER));
        player.setGameMode(GameMode.CREATIVE);
    }

    private @NotNull ItemStack item(@NotNull String key, @NotNull Material material) {
        String name = plugin.lang().raw("setup-items." + key + ".name");
        List<String> lore = plugin.lang().list("setup-items." + key + ".lore");
        return new ItemBuilder(material).name(name).lore(lore).glow(true).build();
    }

    private boolean matches(@Nullable ItemStack stack, @NotNull String key) {
        if (stack == null || !stack.hasItemMeta() || stack.getItemMeta().displayName() == null) {
            return false;
        }
        return stack.getItemMeta().displayName().equals(ColorUtil.parse(plugin.lang().raw("setup-items." + key + ".name")));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        SetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        event.setCancelled(true);

        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean shift = player.isSneaking();

        if (matches(item, "cancel")) {
            exit(player, true);
            return;
        }
        if (matches(item, "select-arena")) {
            cycleArena(player, session);
            return;
        }
        if (matches(item, "create-arena")) {
            session.prompt = ChatPrompt.ARENA_NAME;
            plugin.lang().send(player, "arena.prompt-name");
            return;
        }
        if (matches(item, "save-arena")) {
            saveArena(player, session);
            return;
        }

        Arena arena = currentArena(session);
        if (arena == null) {
            plugin.lang().send(player, "setup.no-arena");
            return;
        }

        if (matches(item, "set-lobby")) {
            arena.setLobby(player.getLocation());
            arena.bindWorld(player.getWorld());
            plugin.arenaManager().saveArena(arena);
            plugin.lang().send(player, "arena.lobby-set");
        } else if (matches(item, "set-spawn")) {
            arena.setSpawn(player.getLocation());
            arena.bindWorld(player.getWorld());
            plugin.arenaManager().saveArena(arena);
            plugin.lang().send(player, "arena.spawn-set");
        } else if (matches(item, "set-spectator")) {
            arena.setSpectator(player.getLocation());
            arena.bindWorld(player.getWorld());
            plugin.arenaManager().saveArena(arena);
            plugin.lang().send(player, "arena.spectator-set");
        } else if (matches(item, "set-center")) {
            arena.setCenter(player.getLocation());
            arena.bindWorld(player.getWorld());
            plugin.arenaManager().saveArena(arena);
            plugin.lang().send(player, "arena.center-set");
            // Shift + set-center also sets the Solo Survival Victory Altar
            if (shift) {
                var target = player.getTargetBlockExact(6);
                arena.setVictoryAltar(target != null ? target.getLocation() : player.getLocation());
                plugin.arenaManager().saveArena(arena);
                plugin.lang().send(player, "modes.victory-altar-set", Map.of("arena", arena.getDisplayName()));
            }
        } else if (matches(item, "set-death-height")) {
            int y = player.getLocation().getBlockY();
            arena.setDeathHeight(y);
            plugin.arenaManager().saveArena(arena);
            plugin.lang().send(player, "arena.death-height-set", Map.of("value", String.valueOf(y)));
        } else if (matches(item, "configure-border")) {
            if (shift && left) {
                arena.setBorderSpeed(arena.getBorderSpeed() + 0.5);
            } else if (shift) {
                arena.setBorderDamage(arena.getBorderDamage() + 0.5);
            } else if (left) {
                arena.setBorderSize(arena.getBorderSize() + 10);
            } else {
                arena.setBorderSize(arena.getBorderSize() - 10);
            }
            plugin.arenaManager().saveArena(arena);
            plugin.lang().send(player, "arena.border-configured", Map.of(
                    "size", String.valueOf((int) arena.getBorderSize()),
                    "speed", String.valueOf(arena.getBorderSpeed()),
                    "damage", String.valueOf(arena.getBorderDamage())
            ));
        } else if (matches(item, "configure-loot")) {
            plugin.lang().send(player, "arena.loot-hint");
        } else if (matches(item, "arena-info")) {
            sendInfo(player, arena);
        } else if (matches(item, "arena-settings")) {
            cycleSettings(player, session, arena);
        }
    }

    private void cycleArena(@NotNull Player player, @NotNull SetupSession session) {
        List<Arena> arenas = new ArrayList<>(plugin.arenaManager().all());
        if (arenas.isEmpty()) {
            plugin.lang().send(player, "arena.none-available");
            session.prompt = ChatPrompt.ARENA_NAME;
            plugin.lang().send(player, "arena.prompt-name");
            return;
        }
        int index = 0;
        if (session.arenaName != null) {
            for (int i = 0; i < arenas.size(); i++) {
                if (arenas.get(i).getName().equalsIgnoreCase(session.arenaName)) {
                    index = (i + 1) % arenas.size();
                    break;
                }
            }
        }
        Arena selected = arenas.get(index);
        session.arenaName = selected.getName();
        plugin.lang().send(player, "arena.cycle", Map.of(
                "arena", selected.getDisplayName(),
                "index", String.valueOf(index + 1),
                "total", String.valueOf(arenas.size())
        ));
        plugin.lang().send(player, "arena.selected", Map.of("arena", selected.getName()));
    }

    private void saveArena(@NotNull Player player, @NotNull SetupSession session) {
        Arena arena = currentArena(session);
        if (arena == null) {
            plugin.lang().send(player, "setup.no-arena");
            return;
        }
        if (arena.getWorldName() == null) {
            arena.bindWorld(player.getWorld());
        }
        plugin.worldResetManager().ensureTemplate(arena);
        boolean ready = arena.tryMarkReady();
        plugin.arenaManager().saveArena(arena);
        if (ready) {
            plugin.lang().send(player, "arena.saved", Map.of("arena", arena.getName()));
            plugin.lang().send(player, "arena.setup-finished", Map.of("arena", arena.getName()));
        } else {
            plugin.lang().send(player, "arena.saved-incomplete", Map.of(
                    "missing", String.join(", ", arena.missingRequirements())
            ));
        }
    }

    private void sendInfo(@NotNull Player player, @NotNull Arena arena) {
        plugin.lang().send(player, "arena.info-header", Map.of("arena", arena.getDisplayName()));
        Map<String, String> lines = Map.ofEntries(
                Map.entry("Name", arena.getName()),
                Map.entry("Display", arena.getDisplayName()),
                Map.entry("World", arena.getWorldName() == null ? "-" : arena.getWorldName()),
                Map.entry("Min", String.valueOf(arena.getMinPlayers())),
                Map.entry("Max", String.valueOf(arena.getMaxPlayers())),
                Map.entry("Countdown", String.valueOf(arena.getCountdownSeconds())),
                Map.entry("Deathmatch", String.valueOf(arena.getDeathmatchAfterSeconds())),
                Map.entry("Border", String.valueOf((int) arena.getBorderSize())),
                Map.entry("Death Height", String.valueOf(arena.getDeathHeight())),
                Map.entry("Ready", String.valueOf(arena.isReady())),
                Map.entry("Missing", arena.missingRequirements().isEmpty() ? "none" : String.join(", ", arena.missingRequirements()))
        );
        for (Map.Entry<String, String> entry : lines.entrySet()) {
            plugin.lang().send(player, "arena.info-line", Map.of("key", entry.getKey(), "value", entry.getValue()));
        }
    }

    private void cycleSettings(@NotNull Player player, @NotNull SetupSession session, @NotNull Arena arena) {
        ChatPrompt[] order = {
                ChatPrompt.DISPLAY_NAME,
                ChatPrompt.MIN_PLAYERS,
                ChatPrompt.MAX_PLAYERS,
                ChatPrompt.COUNTDOWN,
                ChatPrompt.DEATHMATCH,
                ChatPrompt.BORDER_SIZE,
                ChatPrompt.BORDER_SPEED,
                ChatPrompt.BORDER_DAMAGE,
                ChatPrompt.RESPAWN_DELAY
        };
        int next = 0;
        if (session.prompt != null) {
            for (int i = 0; i < order.length; i++) {
                if (order[i] == session.prompt) {
                    next = (i + 1) % order.length;
                    break;
                }
            }
        }
        session.prompt = order[next];
        plugin.lang().send(player, "arena.prompt-settings");
        switch (session.prompt) {
            case DISPLAY_NAME -> plugin.lang().send(player, "arena.prompt-display-name");
            case MIN_PLAYERS -> plugin.lang().send(player, "arena.prompt-min-players");
            case MAX_PLAYERS -> plugin.lang().send(player, "arena.prompt-max-players");
            case COUNTDOWN -> plugin.lang().send(player, "arena.prompt-countdown");
            case DEATHMATCH -> plugin.lang().send(player, "arena.prompt-deathmatch");
            case BORDER_SIZE -> plugin.lang().send(player, "arena.prompt-border-size");
            case BORDER_SPEED -> plugin.lang().send(player, "arena.prompt-border-speed");
            case BORDER_DAMAGE -> plugin.lang().send(player, "arena.prompt-border-damage");
            case RESPAWN_DELAY -> plugin.lang().send(player, "arena.prompt-respawn-delay");
            default -> {
            }
        }
        // Toggle auto-reset when shift-clicking paper repeatedly past end via sneak + paper
        if (player.isSneaking() && session.prompt == ChatPrompt.DISPLAY_NAME) {
            arena.setAutoReset(!arena.isAutoReset());
            plugin.arenaManager().saveArena(arena);
            plugin.lang().send(player, arena.isAutoReset() ? "arena.auto-reset-on" : "arena.auto-reset-off");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(@NotNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        SetupSession session = sessions.get(player.getUniqueId());
        if (session == null || session.prompt == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        ChatPrompt prompt = session.prompt;
        plugin.getServer().getScheduler().runTask(plugin, () -> handlePrompt(player, session, prompt, message));
    }

    private void handlePrompt(@NotNull Player player, @NotNull SetupSession session,
                              @NotNull ChatPrompt prompt, @NotNull String message) {
        if (message.equalsIgnoreCase("cancel")) {
            session.prompt = null;
            plugin.lang().send(player, "general.cancelled");
            return;
        }
        if (prompt == ChatPrompt.ARENA_NAME) {
            String name = message.toLowerCase(Locale.ROOT).replace(' ', '_');
            if (!name.matches("[a-z0-9_\\-]{2,32}")) {
                plugin.lang().send(player, "general.invalid-input");
                return;
            }
            if (plugin.arenaManager().exists(name)) {
                plugin.lang().send(player, "arena.already-exists");
                return;
            }
            Arena arena = plugin.arenaManager().create(name);
            arena.bindWorld(player.getWorld());
            plugin.arenaManager().saveArena(arena);
            session.arenaName = arena.getName();
            session.prompt = null;
            plugin.lang().send(player, "arena.created", Map.of("arena", arena.getName()));
            return;
        }

        Arena arena = currentArena(session);
        if (arena == null) {
            plugin.lang().send(player, "setup.no-arena");
            session.prompt = null;
            return;
        }

        try {
            switch (prompt) {
                case DISPLAY_NAME -> {
                    arena.setDisplayName(message);
                    plugin.lang().send(player, "arena.setting-updated", Map.of("setting", "Display Name", "value", message));
                }
                case MIN_PLAYERS -> {
                    arena.setMinPlayers(Integer.parseInt(message));
                    plugin.lang().send(player, "arena.setting-updated",
                            Map.of("setting", "Min Players", "value", String.valueOf(arena.getMinPlayers())));
                }
                case MAX_PLAYERS -> {
                    arena.setMaxPlayers(Integer.parseInt(message));
                    plugin.lang().send(player, "arena.setting-updated",
                            Map.of("setting", "Max Players", "value", String.valueOf(arena.getMaxPlayers())));
                }
                case COUNTDOWN -> {
                    arena.setCountdownSeconds(Integer.parseInt(message));
                    plugin.lang().send(player, "arena.setting-updated",
                            Map.of("setting", "Countdown", "value", String.valueOf(arena.getCountdownSeconds())));
                }
                case DEATHMATCH -> {
                    arena.setDeathmatchAfterSeconds(Integer.parseInt(message));
                    plugin.lang().send(player, "arena.setting-updated",
                            Map.of("setting", "Deathmatch", "value", String.valueOf(arena.getDeathmatchAfterSeconds())));
                }
                case BORDER_SIZE -> {
                    arena.setBorderSize(Double.parseDouble(message));
                    plugin.lang().send(player, "arena.setting-updated",
                            Map.of("setting", "Border Size", "value", String.valueOf(arena.getBorderSize())));
                }
                case BORDER_SPEED -> {
                    arena.setBorderSpeed(Double.parseDouble(message));
                    plugin.lang().send(player, "arena.setting-updated",
                            Map.of("setting", "Border Speed", "value", String.valueOf(arena.getBorderSpeed())));
                }
                case BORDER_DAMAGE -> {
                    arena.setBorderDamage(Double.parseDouble(message));
                    plugin.lang().send(player, "arena.setting-updated",
                            Map.of("setting", "Border Damage", "value", String.valueOf(arena.getBorderDamage())));
                }
                case RESPAWN_DELAY -> {
                    arena.setRespawnDelayTicks(Integer.parseInt(message));
                    plugin.lang().send(player, "arena.setting-updated",
                            Map.of("setting", "Respawn Delay", "value", String.valueOf(arena.getRespawnDelayTicks())));
                }
                default -> {
                }
            }
            plugin.arenaManager().saveArena(arena);
            session.prompt = null;
        } catch (NumberFormatException ex) {
            plugin.lang().send(player, "general.invalid-number");
        }
    }

    private @Nullable Arena currentArena(@NotNull SetupSession session) {
        if (session.arenaName == null) {
            return null;
        }
        return plugin.arenaManager().get(session.arenaName);
    }

    @EventHandler
    public void onDrop(@NotNull PlayerDropItemEvent event) {
        if (isInSetup(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    public static final class SetupSession {
        private @Nullable String arenaName;
        private @Nullable ChatPrompt prompt;
    }
}
