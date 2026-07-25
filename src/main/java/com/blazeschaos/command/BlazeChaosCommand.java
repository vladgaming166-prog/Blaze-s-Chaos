package com.blazeschaos.command;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.game.GameModeType;
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
            case "eventsdifficulty", "difficulty" -> handleDifficulty(sender, args);
            case "chestlootrarity", "lootrarity" -> handleLootRarity(sender, args);
            case "enablemultimodesforsinglemap" -> handleEnableMultiModes(sender, args);
            case "setvictoryaltar" -> handleSetVictoryAltar(sender, args);
            case "solosurvivalwintime", "survivalwintime", "wintime" -> handleSoloSurvivalWinTime(sender, args);
            case "troll" -> handleTroll(sender, args);
            case "npc" -> plugin.npcCommands().handle(sender, args);
            case "version" -> plugin.lang().send(sender, "general.version",
                    Map.of("version", plugin.getPluginMeta().getVersion()));
            case "createarena" -> plugin.lang().send(sender, "general.unknown-command");
            default -> plugin.lang().send(sender, "general.unknown-command");
        }
        return true;
    }

    private void handleSoloSurvivalWinTime(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("blazechaos.admin")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.lang().send(sender, "solo-survival.win-time-usage", Map.of(
                    "time", String.valueOf(plugin.survivalObjective().winTimeSeconds())
            ));
            return;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            plugin.lang().send(sender, "general.invalid-number");
            return;
        }
        if (seconds < 1) {
            plugin.lang().send(sender, "general.invalid-number");
            return;
        }
        plugin.survivalObjective().setWinTimeSeconds(seconds);
        plugin.reloadPlugin();
        plugin.lang().send(sender, "solo-survival.win-time-set", Map.of("time", String.valueOf(seconds)));
    }

    private void handleTroll(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("blazechaos.admin")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return;
        }
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game == null || !game.getState().isActive() || game.isSpectator(player.getUniqueId())) {
            plugin.lang().send(player, "troll.must-be-in-match");
            return;
        }
        if (args.length < 2) {
            plugin.lang().send(player, "troll.usage");
            return;
        }
        String eventArg = args[1].toLowerCase(Locale.ROOT);
        if (eventArg.equals("stop")) {
            game.stopTrollEvents();
            plugin.lang().send(player, "troll.stopped");
            return;
        }
        if (args.length < 3) {
            plugin.lang().send(player, "troll.usage");
            return;
        }
        int duration;
        try {
            duration = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            plugin.lang().send(player, "general.invalid-number");
            return;
        }
        if (duration < 1) {
            plugin.lang().send(player, "general.invalid-number");
            return;
        }
        ChaosEvent event = resolveTrollEvent(eventArg);
        if (event == null) {
            plugin.lang().send(player, "troll.unknown-event", Map.of("event", args[1]));
            return;
        }
        game.startTrollEvent(event, duration);
        plugin.lang().send(player, "troll.started", Map.of(
                "event", event.getId(),
                "time", String.valueOf(duration)
        ));
    }

    private @Nullable ChaosEvent resolveTrollEvent(@NotNull String raw) {
        String key = raw.toLowerCase(Locale.ROOT).replace('_', '-');
        if (key.equals("random")) {
            return plugin.eventManager().pickRandom(List.of());
        }
        String mapped = switch (key) {
            case "lava" -> "lava-rising";
            case "flood" -> "flood";
            case "meteor" -> "meteor-shower";
            case "lightning" -> "lightning-storm";
            case "tornado" -> "tornado";
            case "explosionrain", "explosion-rain", "explosions" -> "tnt-rain";
            default -> key;
        };
        ChaosEvent event = plugin.eventManager().get(mapped);
        if (event != null) {
            return event;
        }
        return plugin.eventManager().get(key);
    }

    private void handleEnableMultiModes(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("blazechaos.admin") && !sender.hasPermission("blazechaos.setup")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        if (args.length >= 2) {
            Arena arena = plugin.arenaManager().get(args[1]);
            if (arena == null) {
                plugin.lang().send(sender, "arena.not-found");
                return;
            }
            arena.setAllowMultipleModes(true);
            if (arena.getAvailableModes().size() <= 1) {
                arena.setAvailableModes(List.of(
                        GameModeType.SOLO, GameModeType.TEAMS, GameModeType.MEGA, GameModeType.SOLO_SURVIVAL
                ));
            }
            plugin.arenaManager().saveArena(arena);
            plugin.getConfig().set("modes.allow-multiple-modes", true);
            plugin.saveConfig();
            plugin.lang().send(sender, "modes.enabled-arena", Map.of("arena", arena.getDisplayName()));
            return;
        }
        plugin.getConfig().set("modes.allow-multiple-modes", true);
        plugin.saveConfig();
        for (Arena arena : plugin.arenaManager().all()) {
            arena.setAllowMultipleModes(true);
            if (arena.getAvailableModes().size() <= 1) {
                arena.setAvailableModes(List.of(
                        GameModeType.SOLO, GameModeType.TEAMS, GameModeType.MEGA, GameModeType.SOLO_SURVIVAL
                ));
            }
            plugin.arenaManager().saveArena(arena);
        }
        plugin.lang().send(sender, "modes.enabled-global");
    }

    private void handleSetVictoryAltar(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return;
        }
        if (!player.hasPermission("blazechaos.admin") && !player.hasPermission("blazechaos.setup")) {
            plugin.lang().send(player, "general.no-permission");
            return;
        }
        Arena arena = null;
        if (args.length >= 2) {
            arena = plugin.arenaManager().get(args[1]);
        } else if (plugin.setupMode().isInSetup(player)) {
            // use selected setup arena if present via join location world match
            for (Arena candidate : plugin.arenaManager().all()) {
                if (candidate.getWorldName() != null
                        && candidate.getWorldName().equals(player.getWorld().getName())) {
                    arena = candidate;
                    break;
                }
            }
        }
        if (arena == null) {
            GameInstance game = plugin.gameManager().getByPlayer(player);
            if (game != null) {
                arena = game.getArena();
            }
        }
        if (arena == null && args.length < 2) {
            plugin.lang().send(player, "modes.victory-altar-usage");
            return;
        }
        if (arena == null) {
            plugin.lang().send(player, "arena.not-found");
            return;
        }
        var target = player.getTargetBlockExact(6);
        org.bukkit.Location loc = target != null ? target.getLocation() : player.getLocation();
        arena.setVictoryAltar(loc);
        plugin.arenaManager().saveArena(arena);
        plugin.lang().send(player, "modes.victory-altar-set", Map.of("arena", arena.getDisplayName()));
    }

    private void handleDifficulty(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("blazechaos.admin") && !sender.hasPermission("blazechaos.setup")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.lang().send(sender, "difficulty.current", Map.of(
                    "difficulty", plugin.difficultyManager().get().name().toLowerCase(Locale.ROOT)
            ));
            plugin.lang().send(sender, "difficulty.usage");
            return;
        }
        if (!plugin.difficultyManager().set(args[1])) {
            plugin.lang().send(sender, "difficulty.usage");
            return;
        }
        plugin.lang().send(sender, "difficulty.changed", Map.of(
                "difficulty", plugin.difficultyManager().get().name().toLowerCase(Locale.ROOT)
        ));
    }

    private void handleLootRarity(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("blazechaos.admin") && !sender.hasPermission("blazechaos.setup")) {
            plugin.lang().send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.lang().send(sender, "loot.rarity-current", Map.of(
                    "rarity", plugin.lootRarityManager().get().name().toLowerCase(Locale.ROOT)
            ));
            plugin.lang().send(sender, "loot.rarity-usage");
            return;
        }
        if (!plugin.lootRarityManager().set(args[1])) {
            plugin.lang().send(sender, "loot.rarity-usage");
            return;
        }
        plugin.lang().send(sender, "loot.rarity-changed", Map.of(
                "rarity", plugin.lootRarityManager().get().name().toLowerCase(Locale.ROOT)
        ));
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
        GameModeType mode = GameModeType.SOLO;
        if (args.length >= 2) {
            arena = plugin.arenaManager().get(args[1]);
            if (arena == null) {
                plugin.lang().send(player, "arena.not-found");
                return;
            }
        }
        if (args.length >= 3) {
            try {
                mode = GameModeType.parse(args[2]);
            } catch (IllegalArgumentException ex) {
                plugin.lang().send(player, "modes.not-available", Map.of("mode", args[2]));
                return;
            }
        }
        if (mode.isSoloSurvival()) {
            plugin.survivalObjectiveGui().open(player, arena);
            return;
        }
        plugin.gameManager().join(player, arena, mode);
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
        plugin.spawnConfirm().beginConfirm(player);
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
                    "coins", "balance", "shop", "language", "enablerandomchestloot",
                    "eventsdifficulty", "chestlootrarity", "npc",
                    "enablemultimodesforsinglemap", "setvictoryaltar",
                    "solosurvivalwintime", "troll"
            ));
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("npc")) {
            return plugin.npcCommands().tabComplete(args);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("join") || sub.equals("setup") || sub.equals("deletearena")
                    || sub.equals("enablemultimodesforsinglemap") || sub.equals("setvictoryaltar")) {
                return filter(args[1], plugin.arenaManager().all().stream().map(Arena::getName).collect(Collectors.toList()));
            }
            if (sub.equals("language") || sub.equals("lang")) {
                return filter(args[1], Arrays.asList("english", "romanian", "en", "ro"));
            }
            if (sub.equals("eventsdifficulty") || sub.equals("difficulty")) {
                return filter(args[1], Arrays.asList("easy", "normal", "hard", "hardcore", "extreme", "impossible"));
            }
            if (sub.equals("chestlootrarity") || sub.equals("lootrarity")) {
                return filter(args[1], Arrays.asList("common", "uncommon", "normal", "mythic", "legendary", "extreme"));
            }
            if (sub.equals("solosurvivalwintime") || sub.equals("survivalwintime") || sub.equals("wintime")) {
                return filter(args[1], Arrays.asList("300", "600", "900", "1200", "1800"));
            }
            if (sub.equals("troll")) {
                return filter(args[1], trollEventSuggestions());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("join")) {
            return filter(args[2], Arrays.asList("solo", "teams", "mega", "solo_survival"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("troll")
                && !args[1].equalsIgnoreCase("stop")) {
            return filter(args[2], Arrays.asList("15", "30", "45", "60", "90", "120"));
        }
        return List.of();
    }

    private @NotNull List<String> trollEventSuggestions() {
        List<String> out = new ArrayList<>(Arrays.asList(
                "stop", "lava", "flood", "meteor", "lightning", "tornado", "explosionrain", "random"
        ));
        for (ChaosEvent event : plugin.eventManager().all()) {
            out.add(event.getId());
        }
        return out;
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
