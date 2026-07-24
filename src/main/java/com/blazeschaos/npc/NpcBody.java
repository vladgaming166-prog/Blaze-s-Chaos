package com.blazeschaos.npc;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Render backend for an NPC body (Citizens or internal packet player).
 */
public interface NpcBody {

    void spawnForNearby();

    void showFor(@NotNull Player player);

    void hide(@NotNull Player player);

    void despawnAll();

    void tickLook(@Nullable Player nearest);

    void applyIdleMotion(int animTick, @NotNull NpcAnimationType type);

    void playSwing();

    void refreshSkin();

    boolean isBodySpawned();

    /** Bukkit entity when the backend uses a real entity (Citizens); otherwise null. */
    @Nullable Entity bukkitEntity();

    default void teleportBody(@NotNull Location location) {
        Entity entity = bukkitEntity();
        if (entity != null && entity.isValid()) {
            entity.teleport(location);
        }
    }
}
