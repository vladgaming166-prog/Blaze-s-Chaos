package com.blazeschaos.lobby;

import com.blazeschaos.BlazesChaosPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the setlobby "Make this location the server spawn?" true/false chat prompt.
 */
public final class SpawnConfirmListener implements Listener {

    private final BlazesChaosPlugin plugin;
    private final Set<UUID> awaiting = ConcurrentHashMap.newKeySet();

    public SpawnConfirmListener(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    public void beginConfirm(@NotNull Player player) {
        awaiting.add(player.getUniqueId());
        plugin.lang().send(player, "lobby.spawn-prompt");
        plugin.lang().send(player, "lobby.spawn-prompt-hint");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (awaiting.remove(player.getUniqueId()) && player.isOnline()) {
                plugin.lang().send(player, "lobby.spawn-prompt-timeout");
            }
        }, 20L * 30);
    }

    public boolean isAwaiting(@NotNull Player player) {
        return awaiting.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!awaiting.contains(player.getUniqueId())) {
            return;
        }
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message()).trim().toLowerCase(Locale.ROOT);
        if (!msg.equals("true") && !msg.equals("false") && !msg.equals("yes") && !msg.equals("no")
                && !msg.equals("y") && !msg.equals("n")) {
            return;
        }
        event.setCancelled(true);
        boolean accept = msg.equals("true") || msg.equals("yes") || msg.equals("y");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!awaiting.remove(player.getUniqueId())) {
                return;
            }
            Location lobby = plugin.lobbyManager().getLobbyLocation();
            if (lobby == null) {
                plugin.lang().send(player, "lobby.not-set");
                return;
            }
            plugin.lobbyManager().setUseAsServerSpawn(accept, lobby);
            if (accept) {
                plugin.lang().send(player, "lobby.spawn-enabled");
            } else {
                plugin.lang().send(player, "lobby.spawn-disabled");
            }
        });
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        awaiting.remove(event.getPlayer().getUniqueId());
    }
}
