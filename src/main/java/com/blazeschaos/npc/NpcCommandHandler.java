package com.blazeschaos.npc;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.util.ColorUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class NpcCommandHandler {

    private final BlazesChaosPlugin plugin;

    public NpcCommandHandler(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("blazechaos.npc") && !sender.hasPermission("blazechaos.admin")
                && !sender.hasPermission("blazechaos.setup")) {
            plugin.lang().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> create(sender, args);
            case "remove" -> remove(sender);
            case "edit" -> edit(sender);
            case "move" -> move(sender);
            case "skin" -> skin(sender, args);
            case "hologram" -> hologram(sender, args);
            case "animation" -> animation(sender, args);
            case "gui" -> gui(sender);
            case "info" -> info(sender);
            case "reload" -> reload(sender);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean create(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return true;
        }
        if (args.length < 3) {
            plugin.lang().send(sender, "npc.usage-create");
            return true;
        }
        NpcMode mode;
        try {
            mode = NpcMode.parse(args[2]);
        } catch (Exception ex) {
            plugin.lang().send(sender, "npc.usage-create");
            return true;
        }
        NpcDefinition def = plugin.npcManager().create(player, mode);
        plugin.lang().send(player, "npc.created", Map.of(
                "id", def.getId(),
                "mode", mode.display()
        ));
        return true;
    }

    private boolean remove(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return true;
        }
        NpcDefinition def = plugin.npcManager().selected(player);
        if (def == null) {
            def = plugin.npcManager().nearest(player, 5.0);
        }
        if (def == null) {
            plugin.lang().send(player, "npc.none-selected");
            return true;
        }
        String id = def.getId();
        plugin.npcManager().remove(id);
        plugin.lang().send(player, "npc.removed", Map.of("id", id));
        return true;
    }

    private boolean edit(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return true;
        }
        NpcDefinition def = plugin.npcManager().nearest(player, 5.0);
        if (def == null) {
            plugin.lang().send(player, "npc.none-nearby");
            return true;
        }
        plugin.npcManager().select(player, def.getId());
        plugin.lang().send(player, "npc.selected", Map.of("id", def.getId()));
        return true;
    }

    private boolean move(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return true;
        }
        NpcDefinition def = requireSelected(player);
        if (def == null) {
            return true;
        }
        NpcInstance instance = plugin.npcManager().getInstance(def.getId());
        if (instance != null) {
            instance.moveTo(player.getLocation());
        } else {
            def.setLocation(player.getLocation());
        }
        plugin.npcManager().save();
        plugin.lang().send(player, "npc.moved", Map.of("id", def.getId()));
        return true;
    }

    private boolean skin(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return true;
        }
        NpcDefinition def = requireSelected(player);
        if (def == null) {
            return true;
        }
        if (args.length < 4) {
            plugin.lang().send(player, "npc.usage-skin");
            return true;
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        if (type.equals("player")) {
            def.getSkin().setType(SkinData.Type.PLAYER);
            def.getSkin().setValue(args[3]);
            def.getSkin().setTexture(null);
            def.getSkin().setSignature(null);
            plugin.npcManager().skins().resolveAsync(def.getSkin(), () -> {
                NpcInstance instance = plugin.npcManager().getInstance(def.getId());
                if (instance != null) {
                    if (instance.isSpawned()) {
                        instance.refreshSkin();
                    } else {
                        instance.spawn();
                    }
                }
                plugin.npcManager().save();
                plugin.lang().send(player, "npc.skin-set", Map.of("skin", args[3]));
            });
            return true;
        }
        if (type.equals("url")) {
            String url = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            def.getSkin().setType(SkinData.Type.URL);
            def.getSkin().setValue(url);
            def.getSkin().setTexture(null);
            def.getSkin().setSignature(null);
            plugin.npcManager().skins().resolveAsync(def.getSkin(), () -> {
                NpcInstance instance = plugin.npcManager().getInstance(def.getId());
                if (instance != null) {
                    if (instance.isSpawned()) {
                        instance.refreshSkin();
                    } else {
                        instance.spawn();
                    }
                }
                plugin.npcManager().save();
                plugin.lang().send(player, "npc.skin-set", Map.of("skin", "url"));
            });
            return true;
        }
        plugin.lang().send(player, "npc.usage-skin");
        return true;
    }

    private boolean hologram(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return true;
        }
        NpcDefinition def = requireSelected(player);
        if (def == null) {
            return true;
        }
        if (args.length == 2) {
            plugin.lang().send(player, "npc.hologram-header", Map.of("id", def.getId()));
            int i = 0;
            for (String line : def.getHologramLines()) {
                player.sendMessage(ColorUtil.parse("<gray>" + i + ":</gray> " + line));
                i++;
            }
            plugin.lang().send(player, "npc.usage-hologram");
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("clear")) {
            def.getHologramLines().clear();
            refreshHolo(def);
            plugin.npcManager().save();
            plugin.lang().send(player, "npc.hologram-cleared");
            return true;
        }
        if (action.equals("add") && args.length > 3) {
            String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            def.getHologramLines().add(text);
            refreshHolo(def);
            plugin.npcManager().save();
            plugin.lang().send(player, "npc.hologram-added");
            return true;
        }
        if (action.equals("remove") && args.length > 3) {
            try {
                int index = Integer.parseInt(args[3]);
                if (index >= 0 && index < def.getHologramLines().size()) {
                    def.getHologramLines().remove(index);
                    refreshHolo(def);
                    plugin.npcManager().save();
                    plugin.lang().send(player, "npc.hologram-removed");
                }
            } catch (NumberFormatException ex) {
                plugin.lang().send(player, "general.invalid-number");
            }
            return true;
        }
        if (action.equals("reset")) {
            def.setHologramLines(NpcDefinition.defaultHologram(def.getMode()));
            refreshHolo(def);
            plugin.npcManager().save();
            plugin.lang().send(player, "npc.hologram-reset");
            return true;
        }
        plugin.lang().send(player, "npc.usage-hologram");
        return true;
    }

    private void refreshHolo(@NotNull NpcDefinition def) {
        NpcInstance instance = plugin.npcManager().getInstance(def.getId());
        if (instance != null && instance.isSpawned()) {
            instance.spawn();
        }
    }

    private boolean animation(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return true;
        }
        NpcDefinition def = requireSelected(player);
        if (def == null) {
            return true;
        }
        if (args.length >= 3) {
            try {
                def.setAnimation(NpcAnimationType.parse(args[2]));
            } catch (Exception ex) {
                plugin.lang().send(player, "npc.usage-animation");
                return true;
            }
        } else {
            def.setAnimation(def.getAnimation().next());
        }
        plugin.npcManager().save();
        plugin.lang().send(player, "npc.animation-set", Map.of(
                "animation", def.getAnimation().name().toLowerCase(Locale.ROOT)
        ));
        return true;
    }

    private boolean gui(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return true;
        }
        NpcDefinition def = plugin.npcManager().selected(player);
        NpcMode mode = def == null ? NpcMode.SOLO : def.getMode();
        plugin.npcGui().showMain(player, mode);
        return true;
    }

    private boolean info(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.player-only");
            return true;
        }
        NpcDefinition def = plugin.npcManager().selected(player);
        if (def == null) {
            def = plugin.npcManager().nearest(player, 8.0);
        }
        if (def == null) {
            plugin.lang().send(player, "npc.none-nearby");
            return true;
        }
        plugin.lang().send(player, "npc.info", Map.of(
                "id", def.getId(),
                "mode", def.getMode().display(),
                "animation", def.getAnimation().name().toLowerCase(Locale.ROOT),
                "skin", def.getSkin().type().name().toLowerCase(Locale.ROOT)
                        + (def.getSkin().value() == null ? "" : ":" + def.getSkin().value())
        ));
        return true;
    }

    private boolean reload(@NotNull CommandSender sender) {
        plugin.npcManager().reload();
        plugin.lang().send(sender, "npc.reloaded");
        return true;
    }

    private @Nullable NpcDefinition requireSelected(@NotNull Player player) {
        NpcDefinition def = plugin.npcManager().selected(player);
        if (def == null) {
            def = plugin.npcManager().nearest(player, 5.0);
            if (def != null) {
                plugin.npcManager().select(player, def.getId());
            }
        }
        if (def == null) {
            plugin.lang().send(player, "npc.none-selected");
        }
        return def;
    }

    private void sendUsage(@NotNull CommandSender sender) {
        for (String line : plugin.lang().list("npc.help")) {
            sender.sendMessage(ColorUtil.parse(plugin.lang().prefix() + line));
        }
    }

    public @NotNull List<String> tabComplete(@NotNull String[] args) {
        if (args.length == 2) {
            return filter(args[1], List.of(
                    "create", "remove", "edit", "move", "skin", "hologram",
                    "animation", "gui", "info", "reload"
            ));
        }
        if (args.length == 3) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "create" -> filter(args[2], List.of(
                        "all", "solo", "teams", "mega", "solo_survival", "duos", "trios", "squads", "random"));
                case "skin" -> filter(args[2], List.of("player", "url"));
                case "hologram" -> filter(args[2], List.of("add", "remove", "clear", "reset"));
                case "animation" -> filter(args[2], Arrays.stream(NpcAnimationType.values())
                        .map(a -> a.name().toLowerCase(Locale.ROOT)).collect(Collectors.toList()));
                default -> List.of();
            };
        }
        return List.of();
    }

    private @NotNull List<String> filter(@NotNull String input, @NotNull List<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
