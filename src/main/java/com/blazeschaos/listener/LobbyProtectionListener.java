package com.blazeschaos.listener;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.Material;
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
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Full configurable protection for the main lobby world.
 */
public final class LobbyProtectionListener implements Listener {

    private final BlazesChaosPlugin plugin;

    public LobbyProtectionListener(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean protect(@NotNull Player player) {
        return plugin.lobbyManager().shouldProtect(player);
    }

    private boolean flag(@NotNull String key) {
        return plugin.getConfig().getBoolean("lobby-protection." + key, true);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("lobby-protection.enabled", true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(@NotNull BlockBreakEvent event) {
        if (enabled() && flag("block-break") && protect(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(@NotNull BlockPlaceEvent event) {
        if (enabled() && flag("block-place") && protect(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketEmpty(@NotNull PlayerBucketEmptyEvent event) {
        if (!enabled() || !protect(event.getPlayer())) {
            return;
        }
        Material bucket = event.getBucket();
        if (bucket == Material.LAVA_BUCKET && flag("lava-buckets")) {
            event.setCancelled(true);
            return;
        }
        if (bucket == Material.WATER_BUCKET && flag("water-buckets")) {
            event.setCancelled(true);
            return;
        }
        if (flag("empty-buckets")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketFill(@NotNull PlayerBucketFillEvent event) {
        if (enabled() && flag("fill-buckets") && protect(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDrop(@NotNull PlayerDropItemEvent event) {
        if (plugin.lobbyManager().isLobbyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        if (enabled() && flag("item-drop") && protect(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPickup(@NotNull EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (enabled() && flag("item-pickup") && protect(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSwap(@NotNull PlayerSwapHandItemsEvent event) {
        if (plugin.lobbyManager().isLobbyItem(event.getMainHandItem())
                || plugin.lobbyManager().isLobbyItem(event.getOffHandItem())) {
            if (enabled() && flag("move-lobby-items") && protect(event.getPlayer())) {
                event.setCancelled(true);
            }
            return;
        }
        if (enabled() && flag("swap-items") && protect(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (plugin.setupMode().isInSetup(player) || plugin.gameManager().getByPlayer(player) != null) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean lobbyItem = plugin.lobbyManager().isLobbyItem(current) || plugin.lobbyManager().isLobbyItem(cursor);
        if (event.getHotbarButton() >= 0) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            lobbyItem = lobbyItem || plugin.lobbyManager().isLobbyItem(hotbar);
        }
        if (lobbyItem && enabled() && flag("move-lobby-items") && protect(player)) {
            event.setCancelled(true);
            return;
        }
        // Only lock the player's own inventory (2x2 crafting view) — allow shop/GUIs
        if (enabled() && flag("inventory-interact") && protect(player)
                && event.getView().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (plugin.lobbyManager().isLobbyItem(event.getOldCursor()) || plugin.lobbyManager().isLobbyItem(event.getCursor())) {
            if (enabled() && flag("move-lobby-items") && protect(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCraft(@NotNull CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        for (ItemStack matrix : event.getInventory().getMatrix()) {
            if (plugin.lobbyManager().isLobbyItem(matrix)) {
                event.setCancelled(true);
                return;
            }
        }
        if (enabled() && flag("crafting") && protect(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!enabled() || !protect(player)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null) {
            Material type = item.getType();
            if (flag("flint-and-steel") && type == Material.FLINT_AND_STEEL) {
                event.setCancelled(true);
                return;
            }
            if (flag("ignite-tnt") && (type == Material.TNT || type == Material.TNT_MINECART)) {
                event.setCancelled(true);
                return;
            }
            if (flag("lava-buckets") && type == Material.LAVA_BUCKET) {
                event.setCancelled(true);
                return;
            }
            if (flag("water-buckets") && type == Material.WATER_BUCKET) {
                event.setCancelled(true);
                return;
            }
            if (flag("spawn-eggs") && type.name().endsWith("_SPAWN_EGG")) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getClickedBlock() != null) {
            Material block = event.getClickedBlock().getType();
            if (flag("crafting-table") && block == Material.CRAFTING_TABLE
                    && (event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                event.setCancelled(true);
                return;
            }
            if (flag("redstone-interact") && isRedstone(block) && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        if (!enabled() || !protect(event.getPlayer())) {
            return;
        }
        Entity entity = event.getRightClicked();
        if (flag("item-frames") && entity instanceof ItemFrame) {
            event.setCancelled(true);
        }
        if (flag("armor-stands") && entity instanceof ArmorStand) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingBreak(@NotNull HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && enabled() && flag("item-frames") && protect(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        Player player = damagerPlayer(event.getDamager());
        if (player == null || !enabled() || !protect(player)) {
            return;
        }
        Entity victim = event.getEntity();
        if (flag("damage-entities")) {
            event.setCancelled(true);
            return;
        }
        if (flag("item-frames") && victim instanceof ItemFrame) {
            event.setCancelled(true);
        }
        if (flag("armor-stands") && victim instanceof ArmorStand) {
            event.setCancelled(true);
        }
        if (flag("pvp") && victim instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!enabled() || !protect(player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID && flag("void-damage")) {
            event.setCancelled(true);
            var lobby = plugin.lobbyManager().getEffectiveSpawn();
            if (lobby != null) {
                player.setFallDistance(0f);
                player.teleport(lobby);
            }
            return;
        }
        if (flag("all-damage")) {
            event.setCancelled(true);
            return;
        }
        switch (cause) {
            case FALL -> {
                if (flag("fall-damage")) event.setCancelled(true);
            }
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> {
                if (flag("fire-damage")) event.setCancelled(true);
            }
            case CONTACT -> {
                if (flag("cactus-damage") || flag("berry-damage")) event.setCancelled(true);
            }
            case SUFFOCATION -> {
                if (flag("suffocation-damage")) event.setCancelled(true);
            }
            case DROWNING -> {
                if (flag("drown-damage")) event.setCancelled(true);
            }
            case STARVATION -> {
                if (flag("hunger-damage")) event.setCancelled(true);
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFood(@NotNull FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && enabled() && flag("hunger") && protect(player)) {
            event.setCancelled(true);
            if (player.getFoodLevel() < 20) {
                player.setFoodLevel(20);
                player.setSaturation(20f);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onIgnite(@NotNull BlockIgniteEvent event) {
        if (!enabled() || !flag("ignite-tnt")) {
            return;
        }
        if (event.getPlayer() != null && protect(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.lobbyManager().isLobbyWorld(event.getBlock().getWorld())
                && !plugin.getConfig().getBoolean("lobby-protection.allow-natural-fire", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onExplosion(@NotNull ExplosionPrimeEvent event) {
        if (enabled() && flag("explosions") && plugin.lobbyManager().isLobbyWorld(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPhysical(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) {
            return;
        }
        if (!enabled() || !protect(event.getPlayer())) {
            return;
        }
        Material type = event.getClickedBlock().getType();
        if (flag("trample-farmland") && (type == Material.FARMLAND || type == Material.TURTLE_EGG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCreatureSpawn(@NotNull CreatureSpawnEvent event) {
        if (!enabled() || !flag("mob-spawning")) {
            return;
        }
        if (!plugin.lobbyManager().isLobbyWorld(event.getLocation().getWorld())) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.COMMAND) {
            // Allow admin custom unless spawn-eggs blocked via interact
            if (reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
    }

    private boolean isRedstone(@NotNull Material material) {
        String name = material.name();
        return name.contains("REDSTONE") || name.contains("BUTTON") || name.contains("LEVER")
                || name.contains("PRESSURE_PLATE") || name.contains("TRIPWIRE")
                || name.contains("REPEATER") || name.contains("COMPARATOR")
                || name.contains("PISTON") || name.contains("DAYLIGHT_DETECTOR")
                || name.contains("TARGET") || name.contains("NOTE_BLOCK")
                || material == Material.DISPENSER || material == Material.DROPPER
                || material == Material.HOPPER || material == Material.OBSERVER;
    }

    private @org.jetbrains.annotations.Nullable Player damagerPlayer(@NotNull Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
