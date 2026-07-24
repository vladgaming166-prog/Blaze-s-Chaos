package com.blazeschaos.npc;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.npc.nms.NmsBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Real packet-based player NPC visible to nearby players.
 */
public final class PacketPlayerNpc {

    private final BlazesChaosPlugin plugin;
    private final NmsBridge nms;
    private final NpcDefinition definition;
    private final UUID profileId;
    private final String profileName;

    private @Nullable Object nmsPlayer;
    private @Nullable Object gameProfile;
    private int entityId = -1;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> tabCleanupScheduled = ConcurrentHashMap.newKeySet();
    private float renderYaw;
    private float renderPitch;
    private float targetYaw;
    private float targetPitch;
    private double lastSentX = Double.NaN;
    private double lastSentY = Double.NaN;
    private double lastSentZ = Double.NaN;
    private boolean spawned;

    public PacketPlayerNpc(@NotNull BlazesChaosPlugin plugin, @NotNull NmsBridge nms,
                              @NotNull NpcDefinition definition) {
        this.plugin = plugin;
        this.nms = nms;
        this.definition = definition;
        this.profileId = UUID.nameUUIDFromBytes(("BlazeNPC:" + definition.getId()).getBytes());
        String skinName = definition.getSkin().profileName();
        this.profileName = skinName == null || skinName.isBlank() ? "NPC" : skinName;
        Location loc = definition.getLocation();
        this.renderYaw = loc == null ? 0f : loc.getYaw();
        this.targetYaw = renderYaw;
    }

    public boolean isSpawned() {
        return spawned && nmsPlayer != null;
    }

    public int entityId() {
        return entityId;
    }

    public @NotNull UUID profileId() {
        return profileId;
    }

    public @NotNull Set<UUID> viewers() {
        return viewers;
    }

    public void spawnForNearby() {
        if (!nms.available()) {
            return;
        }
        Location loc = definition.getLocation();
        if (loc == null || loc.getWorld() == null || !definition.isVisible()) {
            despawnAll();
            return;
        }
        try {
            if (nmsPlayer == null) {
                ensureNmsEntity(loc);
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to create packet NPC " + definition.getId(), ex);
            return;
        }
        spawned = true;
        double range = definition.getViewDistance();
        double rangeSq = range * range;
        double despawnSq = definition.getDespawnDistance() * definition.getDespawnDistance();
        for (Player player : loc.getWorld().getPlayers()) {
            double dist = player.getLocation().distanceSquared(loc);
            if (dist <= rangeSq) {
                show(player);
            } else if (dist > despawnSq) {
                hide(player);
            }
        }
        for (UUID id : Set.copyOf(viewers)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || player.getWorld() != loc.getWorld()
                    || player.getLocation().distanceSquared(loc) > despawnSq) {
                if (player != null) {
                    hide(player);
                } else {
                    hide(id);
                }
            }
        }
    }

    private void ensureNmsEntity(@NotNull Location loc) throws Exception {
        SkinData skin = definition.getSkin();
        gameProfile = nms.createProfile(profileId, profileName, skin.texture(), skin.signature());
        nmsPlayer = nms.createServerPlayer(loc, gameProfile);
        entityId = nms.entityId(nmsPlayer);
        renderYaw = loc.getYaw();
        targetYaw = renderYaw;
        renderPitch = 0f;
        targetPitch = 0f;
        lastSentX = loc.getX();
        lastSentY = loc.getY();
        lastSentZ = loc.getZ();
    }

    public void show(@NotNull Player player) {
        if (nmsPlayer == null || !nms.available()) {
            return;
        }
        if (!viewers.add(player.getUniqueId())) {
            return;
        }
        try {
            nms.sendSpawn(player, nmsPlayer);
            nms.sendLook(player, nmsPlayer, renderYaw, renderPitch);
            scheduleTabCleanup(player);
        } catch (Throwable ex) {
            viewers.remove(player.getUniqueId());
            if (plugin.configs().debug()) {
                plugin.getLogger().log(Level.WARNING, "Failed to spawn NPC for " + player.getName(), ex);
            }
        }
    }

    private void scheduleTabCleanup(@NotNull Player player) {
        UUID id = player.getUniqueId();
        if (!tabCleanupScheduled.add(id)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tabCleanupScheduled.remove(id);
            if (!viewers.contains(id) || nmsPlayer == null) {
                return;
            }
            Player online = Bukkit.getPlayer(id);
            if (online == null || !online.isOnline()) {
                return;
            }
            try {
                nms.sendTabRemove(online, profileId);
            } catch (Throwable ignored) {
            }
        }, 40L);
    }

    public void hide(@Nullable Player player) {
        if (player == null) {
            return;
        }
        hide(player.getUniqueId(), player);
    }

    public void hide(@NotNull UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        hide(playerId, player);
    }

    private void hide(@NotNull UUID playerId, @Nullable Player player) {
        tabCleanupScheduled.remove(playerId);
        if (!viewers.remove(playerId)) {
            return;
        }
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            if (nmsPlayer != null) {
                nms.sendDespawn(player, nmsPlayer, profileId, entityId);
            } else if (entityId > 0) {
                nms.sendDespawn(player, profileId, entityId);
            }
        } catch (Throwable ex) {
            if (plugin.configs().debug()) {
                plugin.getLogger().log(Level.WARNING, "Failed to despawn NPC for " + player.getName(), ex);
            }
        }
    }

    public void despawnAll() {
        for (UUID id : Set.copyOf(viewers)) {
            hide(id);
        }
        viewers.clear();
        tabCleanupScheduled.clear();
        nmsPlayer = null;
        gameProfile = null;
        spawned = false;
        entityId = -1;
        lastSentX = Double.NaN;
        lastSentY = Double.NaN;
        lastSentZ = Double.NaN;
    }

    public void refreshSkin() {
        Location loc = definition.getLocation();
        if (loc == null) {
            return;
        }
        Set<UUID> previous = Set.copyOf(viewers);
        despawnAll();
        try {
            ensureNmsEntity(loc);
            spawned = true;
            for (UUID id : previous) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) {
                    show(player);
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to refresh NPC skin " + definition.getId(), ex);
        }
    }

    public void tickLook(@Nullable Player nearest) {
        if (nmsPlayer == null) {
            return;
        }
        Location loc = definition.getLocation();
        if (loc == null) {
            return;
        }
        if (nearest != null && definition.isLookAtPlayers()) {
            Location eyes = loc.clone().add(0, 1.62, 0);
            Location target = nearest.getEyeLocation();
            double dx = target.getX() - eyes.getX();
            double dy = target.getY() - eyes.getY();
            double dz = target.getZ() - eyes.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            targetPitch = (float) Math.toDegrees(-Math.atan2(dy, Math.max(0.01, dist)));
            targetPitch = Math.max(-30f, Math.min(30f, targetPitch));
        } else {
            targetYaw = loc.getYaw();
            targetPitch = 0f;
        }

        renderYaw = lerpAngle(renderYaw, targetYaw, 0.28f);
        renderPitch = renderPitch + (targetPitch - renderPitch) * 0.22f;
    }

    public void playSwing() {
        if (nmsPlayer == null) {
            return;
        }
        for (UUID id : viewers) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer == null) {
                continue;
            }
            try {
                nms.sendSwing(viewer, nmsPlayer);
            } catch (Throwable ignored) {
            }
        }
    }

    public void applyIdleMotion(int animTick, @NotNull NpcAnimationType type) {
        Location loc = definition.getLocation();
        if (loc == null || nmsPlayer == null) {
            return;
        }
        NpcAnimationType effective = type;
        if (type == NpcAnimationType.RANDOM) {
            NpcAnimationType[] pool = {
                    NpcAnimationType.IDLE, NpcAnimationType.WAVE, NpcAnimationType.LOOK_AROUND,
                    NpcAnimationType.JUMP, NpcAnimationType.WALK_IN_PLACE, NpcAnimationType.SPIN
            };
            effective = pool[(animTick / 80) % pool.length];
        }
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        float yaw = renderYaw;
        float pitch = renderPitch;
        switch (effective) {
            case JUMP -> y += Math.abs(Math.sin(animTick / 8.0)) * 0.28;
            case SPIN -> yaw = loc.getYaw() + animTick * 6f;
            case LOOK_AROUND -> {
                if (!definition.isLookAtPlayers()) {
                    yaw = loc.getYaw() + (float) (Math.sin(animTick / 18.0) * 40);
                }
            }
            case WALK_IN_PLACE -> {
                y += Math.sin(animTick / 5.0) * 0.03;
                if (animTick % 12 == 0) {
                    playSwing();
                }
            }
            case WAVE, CELEBRATE -> {
                if (animTick % 16 == 0) {
                    playSwing();
                }
            }
            default -> {
            }
        }
        if (effective == NpcAnimationType.SPIN || effective == NpcAnimationType.LOOK_AROUND) {
            renderYaw = yaw;
        }
        broadcastPose(x, y, z, yaw, pitch);
    }

    private void broadcastPose(double x, double y, double z, float yaw, float pitch) {
        try {
            nms.updatePosition(nmsPlayer, x, y, z, yaw, pitch);
            boolean moved = Double.isNaN(lastSentX)
                    || Math.abs(x - lastSentX) > 0.001
                    || Math.abs(y - lastSentY) > 0.001
                    || Math.abs(z - lastSentZ) > 0.001;
            for (UUID id : viewers) {
                Player viewer = Bukkit.getPlayer(id);
                if (viewer == null || !viewer.isOnline()) {
                    continue;
                }
                if (moved) {
                    nms.sendTeleport(viewer, nmsPlayer, x, y, z, yaw, pitch);
                }
                nms.sendLook(viewer, nmsPlayer, yaw, pitch);
            }
            lastSentX = x;
            lastSentY = y;
            lastSentZ = z;
        } catch (Throwable ex) {
            if (plugin.configs().debug()) {
                plugin.getLogger().log(Level.WARNING, "NPC pose update failed", ex);
            }
        }
    }

    private static float lerpAngle(float current, float target, float factor) {
        float diff = target - current;
        while (diff < -180f) {
            diff += 360f;
        }
        while (diff > 180f) {
            diff -= 360f;
        }
        return current + diff * factor;
    }
}
