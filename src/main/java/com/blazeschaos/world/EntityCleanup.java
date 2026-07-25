package com.blazeschaos.world;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Wipes match-created entities from an instance world.
 * Keeps players, Citizens NPCs, and Blaze's Chaos holograms.
 */
public final class EntityCleanup {

    private EntityCleanup() {
    }

    public static void wipeMatchEntities(@NotNull BlazesChaosPlugin plugin, @NotNull World world) {
        for (Entity entity : world.getEntities()) {
            if (shouldKeep(plugin, entity)) {
                continue;
            }
            try {
                entity.remove();
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean shouldKeep(@NotNull BlazesChaosPlugin plugin, @NotNull Entity entity) {
        if (entity instanceof Player) {
            return true;
        }
        // Citizens NPCs
        if (entity.hasMetadata("NPC") || entity.hasMetadata("citizens")) {
            return true;
        }
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("Citizens")
                    && entity.getClass().getName().contains("Citizens")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        // Plugin holograms / NPC helpers
        var pdc = entity.getPersistentDataContainer();
        if (pdc.has(new org.bukkit.NamespacedKey(plugin, "npc_hologram"), PersistentDataType.BYTE)
                || pdc.has(new org.bukkit.NamespacedKey(plugin, "npc_body"), PersistentDataType.BYTE)
                || pdc.has(new org.bukkit.NamespacedKey(plugin, "npc_click"), PersistentDataType.BYTE)) {
            return true;
        }
        // Marker armor stands / text displays used as holograms often have custom names & no gravity
        if (entity instanceof ArmorStand stand && stand.isMarker() && !stand.isVisible()) {
            return true;
        }
        if (entity instanceof TextDisplay) {
            // Keep only if tagged as ours; otherwise remove stray displays from match
            return pdc.has(new org.bukkit.NamespacedKey(plugin, "npc_hologram"), PersistentDataType.BYTE);
        }
        return false;
    }
}
