package com.blazeschaos.listener;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GameListener implements Listener {

    private final BlazesChaosPlugin plugin;

    public GameListener(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.gameManager().getByPlayer(player) != null) {
            return;
        }
        plugin.lobbyManager().handleServerJoinSpawn(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || plugin.gameManager().getByPlayer(player) != null) {
                return;
            }
            if (plugin.lobbyManager().isLobbyWorld(player.getWorld())) {
                plugin.lobbyManager().giveLobbyItems(player);
            } else {
                plugin.lobbyManager().removeLobbyItems(player);
            }
            plugin.scoreboardManager().applyLobby(player);
            plugin.tablistManager().apply(player);
        });
    }

    @EventHandler
    public void onWorldChange(@NotNull org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        // Games / setup manage their own inventories — never strip leave-game or setup tools here
        if (plugin.gameManager().getByPlayer(player) != null || plugin.setupMode().isInSetup(player)) {
            return;
        }
        if (plugin.lobbyManager().isLobbyWorld(player.getWorld())) {
            plugin.lobbyManager().giveLobbyItems(player);
        } else {
            plugin.lobbyManager().removeLobbyItems(player);
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game != null) {
            // Do not call eliminate() — that schedules spectator lobby delay and fights fullyRemovePlayer.
            if (game.getState().isActive() && game.isAlive(player.getUniqueId())) {
                plugin.database().addDeath(player.getUniqueId(), player.getName());
            }
            game.fullyRemovePlayer(player, false);
        }
        plugin.npcManager().clearPlayer(player);
        plugin.npcGui().clearPlayer(player);
        plugin.scoreboardManager().remove(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game == null || !game.getState().isActive()) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = player.getKiller();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.spigot().respawn();
            }
            if (game.isAlive(player.getUniqueId()) || game.isSpectator(player.getUniqueId())
                    || game.contains(player.getUniqueId())) {
                if (game.isAlive(player.getUniqueId())) {
                    game.eliminate(player, killer);
                }
            }
        });
    }

    @EventHandler
    public void onRespawn(@NotNull PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game == null) {
            Location spawn = plugin.lobbyManager().getEffectiveSpawn();
            if (spawn == null) {
                spawn = plugin.lobbyManager().getLobbyLocation();
            }
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    plugin.lobbyManager().giveLobbyItems(player);
                    plugin.scoreboardManager().applyLobby(player);
                    plugin.tablistManager().apply(player);
                }
            });
            return;
        }
        if (game.getArena().getSpectator() != null) {
            event.setRespawnLocation(game.getArena().getSpectator());
        } else if (game.getArena().getSpawn() != null) {
            event.setRespawnLocation(game.getArena().getSpawn());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
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
        if (!game.isAlive(player.getUniqueId()) || game.isInGrace(player.getUniqueId())) {
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
        if (plugin.setupMode().isInSetup(player)) {
            return;
        }

        // Solo Survival: Chaos Shard + Victory Altar
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            GameInstance game = plugin.gameManager().getByPlayer(player);
            if (game != null && game.getMode().isSoloSurvival() && game.getState().isActive()) {
                if (plugin.survivalObjective().tryComplete(player, game, event.getClickedBlock())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        String lobbyId = plugin.lobbyManager().lobbyItemId(item);
        if (lobbyId != null) {
            event.setCancelled(true);
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            plugin.lobbyManager().handleLobbyItem(player, lobbyId);
            return;
        }
        if (plugin.lobbyManager().isLeaveItem(item)) {
            event.setCancelled(true);
            plugin.gameManager().leave(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(@NotNull BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (protectLobby(player)) {
            event.setCancelled(true);
            return;
        }
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game != null && !game.getState().isActive()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(@NotNull BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (protectLobby(player)) {
            event.setCancelled(true);
            return;
        }
        GameInstance game = plugin.gameManager().getByPlayer(player);
        if (game != null && !game.getState().isActive()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(@NotNull PlayerBucketEmptyEvent event) {
        if (protectLobby(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(@NotNull PlayerBucketFillEvent event) {
        if (protectLobby(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(@NotNull HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && protectLobby(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Player player = null;
        if (damager instanceof Player p) {
            player = p;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            player = shooter;
        }
        if (player == null || !protectLobby(player)) {
            return;
        }
        Entity victim = event.getEntity();
        if (victim instanceof ItemFrame || victim instanceof ArmorStand || victim instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(@NotNull PlayerDropItemEvent event) {
        if (plugin.lobbyManager().isLobbyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        GameInstance game = plugin.gameManager().getByPlayer(event.getPlayer());
        if (game != null && game.getState().isJoinable()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(@NotNull PlayerSwapHandItemsEvent event) {
        if (plugin.lobbyManager().isLobbyItem(event.getMainHandItem())
                || plugin.lobbyManager().isLobbyItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (plugin.setupMode().isInSetup(player) || plugin.gameManager().getByPlayer(player) != null) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (plugin.lobbyManager().isLobbyItem(current) || plugin.lobbyManager().isLobbyItem(cursor)) {
            event.setCancelled(true);
        }
        if (event.getHotbarButton() >= 0) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            if (plugin.lobbyManager().isLobbyItem(hotbar)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(@NotNull InventoryDragEvent event) {
        if (plugin.lobbyManager().isLobbyItem(event.getOldCursor()) || plugin.lobbyManager().isLobbyItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(@NotNull CraftItemEvent event) {
        for (ItemStack matrix : event.getInventory().getMatrix()) {
            if (plugin.lobbyManager().isLobbyItem(matrix)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean protectLobby(@NotNull Player player) {
        if (player.isOp() || player.hasPermission("blazechaos.build") || player.hasPermission("blazechaos.admin")) {
            return false;
        }
        if (plugin.setupMode().isInSetup(player)) {
            return false;
        }
        return plugin.lobbyManager().isLobbyWorld(player.getWorld());
    }
}
