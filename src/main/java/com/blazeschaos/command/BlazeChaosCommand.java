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
            case "createarena" -> handleCreateArena(sender, args);
            case "deletearena" -> handleDeleteArena(sender, args);
            case "setup" -> handleSetup(sender, args);
            case "reload" -> handleReload(sender);
            case "forcestart" -> handleForceStart(sender);
            case "stop" -> handleStop(sender);
            case "next" -> handleNext(sender);
            case "debug" -> handleDebug(sender);
            case "info" -> handleInfo(sender);
            case "version" -> plugin.configs().send(sender, "general.version",
                    Map.of("version", plugin.getPluginMeta().getVersion()));
            default -> plugin.configs().send(sender, "general.unknown-command");
        }
        return true;
    }

    private void sendHelp(@NotNull CommandSender sender) {
        for (String line : plugin.configs().helpLines()) {
            sender.sendMessage(ColorUtil.parse(line));
        }
    }

    private void handleJoin(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.configs().send(sender, "general.player-only");
            return;
        }
        if (!player.hasPermission("blazechaos.play")) {
            plugin.configs().send(player, "general.no-permission");
            return;
        }
        Arena arena = null;
        if (args.length >= 2) {
            arena = plugin.arenaManager().get(args[1]);
            if (arena == null) {
                plugin.configs().send(player, "arena.not-found");
                return;
            }
        } else {
            plugin.setupGui().openJoinMenu(player);
            return;
        }
        plugin.gameManager().join(player, arena);
    }

    private void handleLeave(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.configs().send(sender, "general.player-only");
            return;
        }
        plugin.gameManager().leave(player);
    }

    private void handleLobby(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.configs().send(sender, "general.player-only");
            return;
        }
        if (plugin.gameManager().getByPlayer(player) != null) {
            plugin.gameManager().leave(player);
        }
        plugin.lobbyManager().teleport(player);
        plugin.lobbyManager().setupLobbyScoreboard(player);
    }

    private void handleSetLobby(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.configs().send(sender, "general.player-only");
            return;
        }
        if (!player.hasPermission("blazechaos.setup") && !player.hasPermission("blazechaos.admin")) {
            plugin.configs().send(player, "general.no-permission");
            return;
        }
        plugin.lobbyManager().setLobby(player.getLocation());
        plugin.configs().send(player, "lobby.set");
    }

    private void handleList(@NotNull CommandSender sender) {
        plugin.configs().send(sender, "arena.list-header");
        for (Arena arena : plugin.arenaManager().all()) {
            GameInstance game = plugin.gameManager().get(arena.getName());
            String state = game == null ? (arena.isReady() ? "Ready" : "Setup") : game.getState().display();
            int players = game == null ? 0 : game.playerCount();
            plugin.configs().send(sender, "arena.list-entry", Map.of(
                    "arena", arena.getName(),
                    "state", state,
                    "players", String.valueOf(players),
                    "max", String.valueOf(arena.getMaxPlayers())
            ));
        }
    }

    private void handleCreateArena(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("blazechaos.setup") && !sender.hasPermission("blazechaos.admin")) {
            plugin.configs().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse(plugin.configs().prefix() + "<red>Usage: /bc createarena <name></red>"));
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (plugin.arenaManager().exists(name)) {
            plugin.configs().send(sender, "arena.already-exists");
            return;
        }
        plugin.arenaManager().create(name);
        plugin.configs().send(sender, "arena.created", Map.of("arena", name));
        if (sender instanceof Player player) {
            Arena arena = plugin.arenaManager().get(name);
            if (arena != null) {
                plugin.setupGui().open(player, arena);
            }
        }
    }

    private void handleDeleteArena(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("blazechaos.setup") && !sender.hasPermission("blazechaos.admin")) {
            plugin.configs().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.parse(plugin.configs().prefix() + "<red>Usage: /bc deletearena <name></red>"));
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        GameInstance game = plugin.gameManager().get(name);
        if (game != null && (game.getState().isActive() || game.playerCount() > 0)) {
            plugin.configs().send(sender, "arena.in-use");
            return;
        }
        if (!plugin.arenaManager().delete(name)) {
            plugin.configs().send(sender, "arena.not-found");
            return;
        }
        plugin.configs().send(sender, "arena.deleted", Map.of("arena", name));
    }

    private void handleSetup(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.configs().send(sender, "general.player-only");
            return;
        }
        if (!player.hasPermission("blazechaos.setup") && !player.hasPermission("blazechaos.admin")) {
            plugin.configs().send(player, "general.no-permission");
            return;
        }
        Arena arena;
        if (args.length >= 2) {
            arena = plugin.arenaManager().get(args[1]);
        } else if (plugin.arenaManager().all().size() == 1) {
            arena = plugin.arenaManager().all().iterator().next();
        } else {
            sender.sendMessage(ColorUtil.parse(plugin.configs().prefix() + "<red>Usage: /bc setup <arena></red>"));
            return;
        }
        if (arena == null) {
            plugin.configs().send(player, "arena.not-found");
            return;
        }
        plugin.setupGui().open(player, arena);
    }

    private void handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission("blazechaos.reload") && !sender.hasPermission("blazechaos.admin")) {
            plugin.configs().send(sender, "general.no-permission");
            return;
        }
        plugin.reloadPlugin();
        plugin.configs().send(sender, "general.reload-success");
    }

    private void handleForceStart(@NotNull CommandSender sender) {
        if (!sender.hasPermission("blazechaos.forcestart") && !sender.hasPermission("blazechaos.admin")) {
            plugin.configs().send(sender, "general.no-permission");
            return;
        }
        GameInstance game = resolveGame(sender);
        if (game == null) {
            return;
        }
        game.forceStart();
        plugin.configs().send(sender, "game.force-started");
    }

    private void handleStop(@NotNull CommandSender sender) {
        if (!sender.hasPermission("blazechaos.stop") && !sender.hasPermission("blazechaos.admin")) {
            plugin.configs().send(sender, "general.no-permission");
            return;
        }
        GameInstance game = resolveGame(sender);
        if (game == null) {
            return;
        }
        game.stop("game.stopped");
    }

    private void handleNext(@NotNull CommandSender sender) {
        if (!sender.hasPermission("blazechaos.next") && !sender.hasPermission("blazechaos.admin")) {
            plugin.configs().send(sender, "general.no-permission");
            return;
        }
        GameInstance game = resolveGame(sender);
        if (game == null) {
            return;
        }
        game.forceNextEvent();
        plugin.configs().send(sender, "event.forced");
    }

    private void handleDebug(@NotNull CommandSender sender) {
        if (!sender.hasPermission("blazechaos.debug") && !sender.hasPermission("blazechaos.admin")) {
            plugin.configs().send(sender, "general.no-permission");
            return;
        }
        boolean next = !plugin.configs().debug();
        plugin.configs().setDebug(next);
        plugin.configs().send(sender, next ? "admin.debug-on" : "admin.debug-off");
    }

    private void handleInfo(@NotNull CommandSender sender) {
        GameInstance game = resolveGame(sender);
        if (game == null) {
            plugin.configs().send(sender, "game.not-in");
            return;
        }
        String event = game.getActiveEvent() == null ? "None" : game.getActiveEvent().getId();
        plugin.configs().send(sender, "admin.info", Map.of(
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
                    "help", "join", "leave", "lobby", "setlobby", "list", "createarena", "deletearena",
                    "setup", "reload", "forcestart", "stop", "next", "debug", "info", "version"
            ));
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("join") || sub.equals("setup") || sub.equals("deletearena")) {
                return filter(args[1], plugin.arenaManager().all().stream().map(Arena::getName).collect(Collectors.toList()));
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
