package com.blazeschaos.npc;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

/**
 * NPC click + visibility listeners (join / teleport / world change).
 */
public final class NpcListener implements Listener {

    private final BlazesChaosPlugin plugin;

    public NpcListener(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(@NotNull PlayerInteractEntityEvent event) {
        handleClick(event.getPlayer(), event.getRightClicked(), event.getHand(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAt(@NotNull PlayerInteractAtEntityEvent event) {
        handleClick(event.getPlayer(), event.getRightClicked(), event.getHand(), () -> event.setCancelled(true));
    }

    private void handleClick(@NotNull Player player, @NotNull Entity entity,
                             @NotNull EquipmentSlot hand, @NotNull Runnable cancel) {
        if (hand != EquipmentSlot.HAND) {
            return;
        }
        NpcInstance instance = plugin.npcManager().byEntity(entity);
        if (instance == null) {
            return;
        }
        cancel.run();
        if (plugin.gameManager().getByPlayer(player) != null) {
            return;
        }
        plugin.npcManager().handleClick(player, instance);
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Delay until client is ready to receive player-info + spawn packets
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.npcManager().showNearbyFor(player);
            }
        }, 20L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.npcManager().showNearbyFor(player);
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        plugin.npcManager().hideFrom(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(@NotNull PlayerTeleportEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.npcManager().showNearbyFor(player);
            }
        }, 5L);
    }

    @EventHandler
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.npcManager().hideFrom(player);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.npcManager().showNearbyFor(player);
            }
        }, 10L);
    }

    @EventHandler
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        // Respawn packet/Citizens NPCs whose chunks just loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.npcManager().onChunkLoad(event.getChunk()), 2L);
    }

    @EventHandler
    public void onPluginEnable(@NotNull PluginEnableEvent event) {
        // Citizens (re)load — refresh skins so NPCs never stay Steve
        if (!event.getPlugin().getName().equalsIgnoreCase("Citizens")) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.npcManager().refreshAllSkins();
        }, 20L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.npcManager().refreshAllSkins();
        }, 60L);
    }
}
