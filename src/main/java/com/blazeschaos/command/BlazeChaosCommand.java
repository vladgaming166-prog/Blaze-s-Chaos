package com.blazeschaos.command;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class BlazeChaosCommand implements CommandExecutor, TabCompleter {

    private final BlazesChaosPlugin plugin;

    public BlazeChaosCommand(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "join" -> handleJoin(sender, args);
            case "leave" -> handleLeave(sender);
            case "lobby" -> handleLobby(sender);
            case "setlobby" -> handleSetLobby(sender);
            case "list" -> handleList(sender);
            case "deletearena" -> handleDeleteArena(sender, args);
            case "setup" -> handleSetup(sender, args);
            case "reload" -> handleReload(sender);
            case "forcestart" -> handleForceStart(sender);
            case "stop" -> handleStop(sender);
            case "next" -> handleNext(sender);
            case "debug" -> handleDebug(sender);
            case "info" -> handleInfo(sender);
            case "coins", "balance" -> handleCoins(sender);
            case "shop" -> handleShop(sender);
            case "language", "lang" -> handleLanguage(sender, args);
            case "enablerandomchestloot" -> handleLootToggle(sender);
            case "version" -> plugin.lang().send(sender, "general.version",
                    Map.of("version", plugin.getPluginMeta().getVersion()));
            case "createarena" -> plugin.lang().send(sender, "general.unknown-command");
            default -> plugin.lang().send(sender, "general.unknown-command");
        }
        return true;
    }

    private void handleCoins(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return;
        }
        plugin.lang().send(player, "coins.balance", Map.of(
                "balance", String.valueOf(plugin.coinsManager().balance(player))
        ));
    }

    private void handleShop(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return;
        }
        plugin.shopManager().open(player);
    }

    private void handleLanguage(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("blazechaos.language")
                && !sender.hasPermission("blazechaos.admin")
                && !sender.hasPermission("blazechaos.reload")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.lang().send(sender, "language.invalid");
            return;
        }
        if (!plugin.lang().setLanguage(args[1])) {
            plugin.lang().send(sender, "language.invalid");
            return;
        }
        plugin.lang().send(sender, "language.changed", Map.of("language", args[1].toLowerCase(Locale.ROOT)));
    }

    private void handleLootToggle(@NotNull CommandSender sender) {
        if (!sender.hasPermission("blazechaos.admin") && !sender.hasPermission("blazechaos.setup")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        boolean next = !plugin.lootManager().isEnabled();
        plugin.lootManager().setEnabled(next);
        plugin.lang().send(sender, next ? "loot.enabled" : "loot.disabled");
    }

    private void sendHelp(@NotNull CommandSender sender) {
        for (String line : plugin.lang().list("help")) {
            sender.sendMessage(ColorUtil.parse(line));
        }
    }

    private void handleJoin(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return;
        }
        if (!player.hasPermission("blazechaos.play")) {
            plugin.lang().send(player, "general.no-permission");
            return;
        }
        Arena arena = null;
        if (args.length >= 2) {
            arena = plugin.arenaManager().get(args[1]);
            if (arena == null) {
                plugin.lang().send(player, "arena.not-found");
                return;
            }
        }
        plugin.gameManager().join(player, arena);
    }

    private void handleLeave(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return;
        }
        plugin.gameManager().leave(player);
    }

    private void handleLobby(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return;
        }
        if (plugin.gameManager().getByPlayer(player) != null) {
            plugin.gameManager().leave(player);
        }
        plugin.lobbyManager().teleport(player);
        plugin.scoreboardManager().applyLobby(player);
        plugin.tablistManager().apply(player);
    }

    private void handleSetLobby(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return;
        }
        if (!player.hasPermission("blazechaos.setup") && !player.hasPermission("blazechaos.admin")) {
            plugin.lang().send(player, "general.no-permission");
            return;
        }
        plugin.lobbyManager().setLobby(player.getLocation());
        plugin.lang().send(player, "lobby.set");
    }

    private void handleList(@NotNull CommandSender sender) {
        plugin.lang().send(sender, "arena.list-header");
        for (Arena arena : plugin.arenaManager().all()) {
            GameInstance game = plugin.gameManager().get(arena.getName());
            String state = game == null ? (arena.isReady() ? "Ready" : "Setup") : game.getState().display();
            int players = game == null ? 0 : game.playerCount();
            plugin.lang().send(sender, "arena.list-entry", Map.of(
                    "arena", arena.getDisplayName(),
                    "state", state,
                    "players", String.valueOf(players),
                    "max", String.valueOf(arena.getMaxPlayers())
            ));
        }
    }

    private void handleDeleteArena(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("blazechaos.setup") && !sender.hasPermission("blazechaos.admin")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse(plugin.lang().prefix() + "<red>/bc deletearena <name></red>"));
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        GameInstance game = plugin.gameManager().get(name);
        if (game != null && (game.getState().isActive() || game.playerCount() > 0)) {
            plugin.lang().send(sender, "arena.in-use");
            return;
        }
        if (!plugin.arenaManager().delete(name)) {
            plugin.lang().send(sender, "arena.not-found");
            return;
        }
        plugin.lang().send(sender, "arena.deleted", Map.of("arena", name));
    }

    private void handleSetup(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return;
        }
        if (!player.hasPermission("blazechaos.setup") && !player.hasPermission("blazechaos.admin")) {
            plugin.lang().send(player, "general.no-permission");
            return;
        }
        if (plugin.setupMode().isInSetup(player)) {
            plugin.setupMode().exit(player, true);
            return;
        }
        if (args.length >= 2) {
            Arena arena = plugin.arenaManager().get(args[1]);
            if (arena == null) {
                plugin.lang().send(player, "arena.not-found");
                return;
            }
            plugin.setupMode().enter(player, arena);
            return;
        }
        plugin.setupMode().enter(player);
    }

    private void handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission("blazechaos.reload") && !sender.hasPermission("blazechaos.admin")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        plugin.reloadPlugin();
        plugin.lang().send(sender, "general.reload-success");
    }

    private void handleForceStart(@NotNull CommandSender sender) {
        if (!sender.hasPermission("blazechaos.forcestart") && !sender.hasPermission("blazechaos.admin")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        GameInstance game = resolveGame(sender);
        if (game == null) {
            plugin.lang().send(sender, "game.not-in");
            return;
        }
        game.forceStart();
        plugin.lang().send(sender, "game.force-started");
    }

    private void handleStop(@NotNull CommandSender sender) {
        if (!sender.hasPermission("blazechaos.stop") && !sender.hasPermission("blazechaos.admin")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        GameInstance game = resolveGame(sender);
        if (game == null) {
            plugin.lang().send(sender, "game.not-in");
            return;
        }
        game.stop("game.stopped");
    }

    private void handleNext(@NotNull CommandSender sender) {
        if (!sender.hasPermission("blazechaos.next") && !sender.hasPermission("blazechaos.admin")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        GameInstance game = resolveGame(sender);
        if (game == null) {
            plugin.lang().send(sender, "game.not-in");
            return;
        }
        game.forceNextEvent();
        plugin.lang().send(sender, "event.forced");
    }

    private void handleDebug(@NotNull CommandSender sender) {
        if (!sender.hasPermission("blazechaos.debug") && !sender.hasPermission("blazechaos.admin")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        boolean next = !plugin.configs().debug();
        plugin.configs().setDebug(next);
        plugin.lang().send(sender, next ? "admin.debug-on" : "admin.debug-off");
    }

    private void handleInfo(@NotNull CommandSender sender) {
        GameInstance game = resolveGame(sender);
        if (game == null) {
            plugin.lang().send(sender, "game.not-in");
            return;
        }
        String event = game.getActiveEvent() == null ? "None" : game.getActiveEvent().getId();
        plugin.lang().send(sender, "admin.info", Map.of(
                "arena", game.getArena().getName(),
                "state", game.getState().display(),
                "players", String.valueOf(game.playerCount()),
                "event", event
        ));
    }

    private @Nullable GameInstance resolveGame(@NotNull CommandSender sender) {
        if (sender instanceof Player player) {
            GameInstance game = plugin.gameManager().getByPlayer(player);
            if (game != null) {
                return game;
            }
        }
        return plugin.gameManager().all().stream().findFirst().orElse(null);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList(
                    "help", "join", "leave", "lobby", "setlobby", "list", "deletearena",
                    "setup", "reload", "forcestart", "stop", "next", "debug", "info", "version",
                    "coins", "balance", "shop", "language", "enablerandomchestloot"
            ));
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("join") || sub.equals("setup") || sub.equals("deletearena")) {
                return filter(args[1], plugin.arenaManager().all().stream().map(Arena::getName).collect(Collectors.toList()));
            }
            if (sub.equals("language") || sub.equals("lang")) {
                return filter(args[1], Arrays.asList("english", "romanian", "en", "ro"));
            }
        }
        return List.of();
    }

    private @NotNull List<String> filter(@NotNull String input, @NotNull List<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
