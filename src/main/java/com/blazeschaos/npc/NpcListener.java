package com.blazeschaos.npc;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

public final class NpcListener implements Listener {

    private final BlazesChaosPlugin plugin;

    public NpcListener(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(@NotNull PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        NpcInstance instance = plugin.npcManager().byEntity(entity);
        if (instance == null) {
            return;
        }
        event.setCancelled(true);
        if (plugin.gameManager().getByPlayer(player) != null) {
            return;
        }
        plugin.npcManager().handleClick(player, instance);
    }
}
