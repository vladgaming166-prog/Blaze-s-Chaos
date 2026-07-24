package com.blazeschaos.listener;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

public final class GameListener implements Listener {

    private final BlazesChaosPlugin plugin;

    public GameListener(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.gameManager().getByPlayer(player) != null) {
            return;
        }
        plugin.lobbyManager().giveJoinItem(player);
        plugin.scoreboardManager().applyLobby(player);
        plugin.tablistManager().apply(player);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game != null) {
            if (game.getState().isActive() && game.isAlive(player.getUniqueId())) {
                game.eliminate(player, null);
            }
            game.leave(player, false);
            plugin.gameManager().untrack(player);
        }
        plugin.scoreboardManager().remove(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game == null || !game.getState().isActive()) {
            return;
        }
        event.setKeepInventory(false);
        Player killer = player.getKiller();
        game.eliminate(player, killer);
    }

    @EventHandler
    public void onRespawn(@NotNull PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game == null) {
            if (plugin.lobbyManager().getLobbyLocation() != null) {
                event.setRespawnLocation(plugin.lobbyManager().getLobbyLocation());
            }
            return;
        }
        if (game.getArena().getSpectator() != null) {
            event.setRespawnLocation(game.getArena().getSpectator());
        } else if (game.getArena().getSpawn() != null) {
            event.setRespawnLocation(game.getArena().getSpawn());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game == null) {
            return;
        }
        if (!game.getState().isActive()) {
            event.setCancelled(true);
            return;
        }
        if (!game.isAlive(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        game.handleDamage(player, event);
        if (!event.isCancelled() && player.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            Player killer = null;
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                if (byEntity.getDamager() instanceof Player damager) {
                    killer = damager;
                } else if (byEntity.getDamager() instanceof Projectile projectile
                        && projectile.getShooter() instanceof Player shooter) {
                    killer = shooter;
                }
            }
            game.eliminate(player, killer);
        }
    }

    @EventHandler
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getItem() == null) {
            return;
        }
        if (plugin.lobbyManager().isJoinItem(event.getItem())) {
            event.setCancelled(true);
            if (plugin.setupMode().isInSetup(player)) {
                return;
            }
            plugin.gameManager().join(player, null);
            return;
        }
        if (plugin.lobbyManager().isLeaveItem(event.getItem())) {
            event.setCancelled(true);
            plugin.gameManager().leave(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(@NotNull BlockBreakEvent event) {
        GameInstance game = plugin.gameManager().getByPlayer(event.getPlayer());
        if (game == null) {
            return;
        }
        if (!game.getState().isActive()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(@NotNull BlockPlaceEvent event) {
        GameInstance game = plugin.gameManager().getByPlayer(event.getPlayer());
        if (game == null) {
            return;
        }
        if (!game.getState().isActive()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(@NotNull PlayerDropItemEvent event) {
        GameInstance game = plugin.gameManager().getByPlayer(event.getPlayer());
        if (game != null && game.getState().isJoinable()) {
            event.setCancelled(true);
        }
    }
}
