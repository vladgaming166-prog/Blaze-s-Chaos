package com.blazeschaos.tablist;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TablistManager {

    private final BlazesChaosPlugin plugin;
    private int frame;
    private @Nullable BukkitTask task;

    public TablistManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.configs().tablist().getBoolean("enabled", true)) {
            return;
        }
        int interval = Math.max(1, plugin.configs().tablist().getInt("update-interval", 40));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            frame++;
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    apply(player);
                } catch (Throwable ex) {
                    if (plugin.configs().debug()) {
                        plugin.getLogger().warning("Tablist apply failed for "
                                + player.getName() + ": " + ex.getMessage());
                    }
                }
            }
        }, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void apply(@NotNull Player player) {
        FileConfiguration config = plugin.configs().tablist();
        if (!config.getBoolean("enabled", true)) {
            return;
        }
        if (plugin.setupMode().isInSetup(player)) {
            return;
        }
        GameInstance game = plugin.gameManager().getByPlayer(player);
        String section = resolveSection(player, game);

        List<String> headerLines = resolveAnimatedOrStatic(config, section, "header");
        List<String> footerLines = resolveAnimatedOrStatic(config, section, "footer");

        Component header = joinLines(player, game, headerLines);
        Component footer = joinLines(player, game, footerLines);
        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    private @NotNull String resolveSection(@NotNull Player player, @Nullable GameInstance game) {
        if (game == null) {
            return "server-lobby";
        }
        if (game.isSpectator(player.getUniqueId())) {
            return "spectator";
        }
        return switch (game.getState()) {
            case LOBBY, WAITING -> "waiting";
            case STARTING -> "starting";
            case PLAYING, CHAOS_EVENT, DEATHMATCH -> "playing";
            case ENDING, RESETTING -> "ending";
        };
    }

    private @NotNull List<String> resolveAnimatedOrStatic(@NotNull FileConfiguration config,
                                                          @NotNull String section,
                                                          @NotNull String type) {
        if (config.getBoolean("animations.enabled", true)) {
            List<String> frames = config.getStringList("animations." + type + "-frames");
            if (!frames.isEmpty() && section.equals("server-lobby")) {
                int frameInterval = Math.max(1, config.getInt("animations.frame-interval", 1));
                return List.of(frames.get(Math.floorMod(frame / frameInterval, frames.size())));
            }
        }
        return config.getStringList(section + "." + type);
    }

    private @NotNull Component joinLines(@NotNull Player player, @Nullable GameInstance game, @NotNull List<String> lines) {
        List<Component> components = new ArrayList<>();
        for (String line : lines) {
            String text = plugin.animationManager().resolve(line);
            text = plugin.placeholders().apply(player, game, text);
            components.add(ColorUtil.parse(text));
        }
        if (components.isEmpty()) {
            return Component.empty();
        }
        return Component.join(JoinConfiguration.newlines(), components);
    }
}
