package com.blazeschaos.npc;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.npc.nms.NmsBridge;
import com.blazeschaos.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runtime NPC: real packet player model + Interaction clickbox + TextDisplay holograms.
 */
public final class NpcInstance {

    private final BlazesChaosPlugin plugin;
    private final NpcDefinition definition;
    private final PacketPlayerNpc packetNpc;
    private @Nullable Interaction clickbox;
    private final List<TextDisplay> holograms = new ArrayList<>();
    private boolean spawned;
    private int animTick;

    public NpcInstance(@NotNull BlazesChaosPlugin plugin, @NotNull NmsBridge nms,
                       @NotNull NpcDefinition definition) {
        this.plugin = plugin;
        this.definition = definition;
        this.packetNpc = new PacketPlayerNpc(plugin, nms, definition);
    }

    public @NotNull NpcDefinition definition() {
        return definition;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public @Nullable UUID clickId() {
        return clickbox == null ? null : clickbox.getUniqueId();
    }

    public boolean isEntity(@NotNull Entity entity) {
        if (clickbox != null && clickbox.getUniqueId().equals(entity.getUniqueId())) {
            return true;
        }
        for (TextDisplay hologram : holograms) {
            if (hologram.getUniqueId().equals(entity.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    public void spawn() {
        despawn();
        Location loc = definition.getLocation();
        if (loc == null || loc.getWorld() == null || !definition.isVisible()) {
            return;
        }
        World world = loc.getWorld();

        // Invisible interaction hitbox for reliable clicking (packet players aren't Bukkit entities)
        clickbox = world.spawn(loc.clone().add(0, 1.0, 0), Interaction.class, interaction -> {
            interaction.setInteractionWidth(0.8f);
            interaction.setInteractionHeight(2.0f);
            interaction.setResponsive(true);
            interaction.setPersistent(false);
            interaction.setInvulnerable(true);
            interaction.setSilent(true);
        });

        spawnHolograms(loc);
        packetNpc.spawnForNearby();
        spawned = true;
        animTick = 0;
    }

    /** Re-show packet player for a joining / nearby player. */
    public void showFor(@NotNull Player player) {
        if (!spawned) {
            return;
        }
        packetNpc.showFor(player);
    }

    public void hideFrom(@NotNull Player player) {
        packetNpc.hide(player);
    }

    public void refreshSkin() {
        packetNpc.refreshSkin();
    }

    public void refreshHolograms(@Nullable Player viewer) {
        Location loc = definition.getLocation();
        if (loc == null) {
            return;
        }
        if (holograms.isEmpty() && spawned) {
            spawnHolograms(loc);
            return;
        }
        List<String> lines = definition.getHologramLines();
        for (int i = 0; i < holograms.size(); i++) {
            TextDisplay display = holograms.get(i);
            if (!display.isValid()) {
                continue;
            }
            String line = i < lines.size() ? lines.get(i) : "";
            String resolved = plugin.animationManager().resolve(line);
            resolved = viewer == null
                    ? plugin.placeholders().apply(null, resolved)
                    : plugin.placeholders().apply(viewer, resolved);
            display.text(ColorUtil.parse(resolved));
        }
    }

    private void spawnHolograms(@NotNull Location base) {
        clearHolograms();
        List<String> lines = definition.getHologramLines();
        double startY = 2.15;
        double step = 0.28;
        for (int i = 0; i < lines.size(); i++) {
            Location holoLoc = base.clone().add(0, startY + (lines.size() - 1 - i) * step, 0);
            String line = lines.get(i);
            String resolved = plugin.animationManager().resolve(plugin.placeholders().apply(null, line));
            TextDisplay display = base.getWorld().spawn(holoLoc, TextDisplay.class, text -> {
                text.text(ColorUtil.parse(resolved));
                text.setBillboard(Display.Billboard.CENTER);
                text.setSeeThrough(true);
                text.setShadowed(true);
                text.setDefaultBackground(false);
                text.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                text.setAlignment(TextDisplay.TextAlignment.CENTER);
                text.setPersistent(false);
                text.setInvulnerable(true);
            });
            holograms.add(display);
        }
    }

    public void tick(int globalTick) {
        if (!spawned) {
            return;
        }
        animTick++;
        Location base = definition.getLocation();
        if (base == null) {
            return;
        }

        // Maintain viewer set + packet player visibility
        packetNpc.spawnForNearby();

        Player nearest = nearestPlayer(base, definition.getViewDistance());
        packetNpc.tickLook(nearest);
        packetNpc.applyIdleMotion(animTick, definition.getAnimation());

        if (clickbox != null && clickbox.isValid()) {
            Location clickLoc = base.clone().add(0, 1.0, 0);
            clickbox.teleport(clickLoc);
        }

        if (globalTick % 10 == 0) {
            refreshHolograms(nearest);
        }
    }

    public void playClickAnimation() {
        packetNpc.playSwing();
    }

    private @Nullable Player nearestPlayer(@NotNull Location location, double radius) {
        Player best = null;
        double bestDist = radius * radius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != location.getWorld()) {
                continue;
            }
            if (plugin.gameManager().getByPlayer(player) != null) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(location);
            if (dist <= bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    public boolean hasNearbyPlayers() {
        Location loc = definition.getLocation();
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        double radiusSq = definition.getDespawnDistance() * definition.getDespawnDistance();
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    public void despawn() {
        spawned = false;
        packetNpc.despawnAll();
        if (clickbox != null) {
            clickbox.remove();
            clickbox = null;
        }
        clearHolograms();
    }

    private void clearHolograms() {
        for (TextDisplay hologram : holograms) {
            if (hologram != null && hologram.isValid()) {
                hologram.remove();
            }
        }
        holograms.clear();
    }

    public void moveTo(@NotNull Location location) {
        definition.setLocation(location);
        if (spawned) {
            spawn();
        }
    }
}
