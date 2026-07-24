package com.blazeschaos.npc;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runtime NPC visuals: ArmorStand body + Interaction clickbox + TextDisplay holograms.
 */
public final class NpcInstance {

    private final BlazesChaosPlugin plugin;
    private final NpcDefinition definition;
    private @Nullable ArmorStand body;
    private @Nullable Interaction clickbox;
    private final List<TextDisplay> holograms = new ArrayList<>();
    private boolean spawned;
    private int animTick;
    private float baseYaw;

    public NpcInstance(@NotNull BlazesChaosPlugin plugin, @NotNull NpcDefinition definition) {
        this.plugin = plugin;
        this.definition = definition;
    }

    public @NotNull NpcDefinition definition() {
        return definition;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public @Nullable UUID bodyId() {
        return body == null ? null : body.getUniqueId();
    }

    public @Nullable UUID clickId() {
        return clickbox == null ? null : clickbox.getUniqueId();
    }

    public boolean isEntity(@NotNull Entity entity) {
        UUID id = entity.getUniqueId();
        if (clickbox != null && clickbox.getUniqueId().equals(id)) {
            return true;
        }
        if (body != null && body.getUniqueId().equals(id)) {
            return true;
        }
        for (TextDisplay hologram : holograms) {
            if (hologram.getUniqueId().equals(id)) {
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
        baseYaw = loc.getYaw();
        World world = loc.getWorld();

        body = world.spawn(loc.clone(), ArmorStand.class, stand -> {
            stand.setInvisible(false);
            stand.setVisible(true);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setCanPickupItems(false);
            stand.setPersistent(false);
            stand.setRemoveWhenFarAway(false);
            stand.setCustomNameVisible(false);
            stand.setSilent(true);
            stand.addDisabledSlots(EquipmentSlot.values());
            stand.setMarker(false);
            stand.setSmall(false);
        });

        ItemStack skull = plugin.npcManager().skins().createSkull(definition.getSkin());
        EntityEquipment equipment = body.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(skull);
            equipment.setChestplate(new ItemStack(org.bukkit.Material.LEATHER_CHESTPLATE));
            equipment.setLeggings(new ItemStack(org.bukkit.Material.LEATHER_LEGGINGS));
            equipment.setBoots(new ItemStack(org.bukkit.Material.LEATHER_BOOTS));
        }

        clickbox = world.spawn(loc.clone().add(0, 1.0, 0), Interaction.class, interaction -> {
            interaction.setInteractionWidth(0.9f);
            interaction.setInteractionHeight(2.0f);
            interaction.setResponsive(true);
            interaction.setPersistent(false);
            interaction.setInvulnerable(true);
        });

        spawnHolograms(loc);
        spawned = true;
        animTick = 0;
    }

    public void refreshSkin() {
        if (body == null || !body.isValid()) {
            return;
        }
        EntityEquipment equipment = body.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(plugin.npcManager().skins().createSkull(definition.getSkin()));
        }
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
            String resolved = viewer == null
                    ? plugin.placeholders().apply(null, line)
                    : plugin.placeholders().apply(viewer, line);
            resolved = plugin.animationManager().resolve(resolved);
            if (viewer != null) {
                resolved = plugin.placeholders().apply(viewer, resolved);
            } else {
                resolved = plugin.placeholders().apply(null, resolved);
            }
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
            TextDisplay display = base.getWorld().spawn(holoLoc, TextDisplay.class, text -> {
                text.text(ColorUtil.parse(plugin.animationManager().resolve(
                        plugin.placeholders().apply(null, line))));
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
        if (!spawned || body == null || !body.isValid()) {
            return;
        }
        animTick++;
        Location base = definition.getLocation();
        if (base == null) {
            return;
        }

        if (definition.isLookAtPlayers()) {
            Player nearest = nearestPlayer(base, definition.getViewDistance());
            if (nearest != null) {
                Location eyes = body.getLocation();
                Location target = nearest.getEyeLocation();
                double dx = target.getX() - eyes.getX();
                double dz = target.getZ() - eyes.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                Location rotated = body.getLocation();
                rotated.setYaw(yaw);
                rotated.setPitch(0f);
                body.teleport(rotated);
                if (clickbox != null && clickbox.isValid()) {
                    Location clickLoc = base.clone().add(0, 1.0, 0);
                    clickLoc.setYaw(yaw);
                    clickbox.teleport(clickLoc);
                }
            }
        }

        applyAnimation(globalTick);

        // Refresh hologram placeholders occasionally
        if (globalTick % 20 == 0) {
            Player nearest = nearestPlayer(base, definition.getViewDistance());
            refreshHolograms(nearest);
        }
    }

    private void applyAnimation(int globalTick) {
        if (body == null) {
            return;
        }
        NpcAnimationType type = definition.getAnimation();
        if (type == NpcAnimationType.RANDOM) {
            NpcAnimationType[] pool = {
                    NpcAnimationType.IDLE, NpcAnimationType.WAVE, NpcAnimationType.LOOK_AROUND,
                    NpcAnimationType.JUMP, NpcAnimationType.WALK_IN_PLACE
            };
            type = pool[(globalTick / 80) % pool.length];
        }
        double t = animTick / 5.0;
        switch (type) {
            case WAVE -> {
                body.setRightArmPose(new EulerAngle(Math.toRadians(-20), 0, Math.toRadians(-140 + Math.sin(t) * 35)));
                body.setLeftArmPose(new EulerAngle(0, 0, 0));
            }
            case SPIN -> {
                Location loc = body.getLocation();
                loc.setYaw(baseYaw + (animTick * 8f));
                body.teleport(loc);
            }
            case LOOK_AROUND -> {
                Location loc = body.getLocation();
                loc.setYaw(baseYaw + (float) (Math.sin(t / 3.0) * 50));
                body.teleport(loc);
            }
            case JUMP -> {
                Location loc = definition.getLocation();
                if (loc != null) {
                    double yOff = Math.abs(Math.sin(t / 2.0)) * 0.35;
                    Location jumped = loc.clone().add(0, yOff, 0);
                    jumped.setYaw(body.getLocation().getYaw());
                    body.teleport(jumped);
                }
            }
            case CELEBRATE -> {
                body.setRightArmPose(new EulerAngle(Math.toRadians(-160), 0, Math.toRadians(Math.sin(t) * 20)));
                body.setLeftArmPose(new EulerAngle(Math.toRadians(-160), 0, Math.toRadians(-Math.sin(t) * 20)));
                body.setHeadPose(new EulerAngle(Math.toRadians(Math.sin(t) * 10), 0, 0));
            }
            case WALK_IN_PLACE -> {
                body.setRightArmPose(new EulerAngle(Math.toRadians(Math.sin(t) * 30), 0, 0));
                body.setLeftArmPose(new EulerAngle(Math.toRadians(-Math.sin(t) * 30), 0, 0));
                body.setRightLegPose(new EulerAngle(Math.toRadians(-Math.sin(t) * 35), 0, 0));
                body.setLeftLegPose(new EulerAngle(Math.toRadians(Math.sin(t) * 35), 0, 0));
            }
            case IDLE -> {
                body.setRightArmPose(new EulerAngle(0, 0, Math.toRadians(2)));
                body.setLeftArmPose(new EulerAngle(0, 0, Math.toRadians(-2)));
                body.setHeadPose(EulerAngle.ZERO);
                body.setRightLegPose(EulerAngle.ZERO);
                body.setLeftLegPose(EulerAngle.ZERO);
            }
            default -> {
            }
        }
        // Occasional arm swing pulse for interactivity feel
        if (type == NpcAnimationType.IDLE && animTick % 100 == 0) {
            body.setRightArmPose(new EulerAngle(Math.toRadians(-40), 0, 0));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (body != null && body.isValid()) {
                    body.setRightArmPose(EulerAngle.ZERO);
                }
            }, 8L);
        }
    }

    public void playClickAnimation() {
        if (body == null || !body.isValid()) {
            return;
        }
        body.setRightArmPose(new EulerAngle(Math.toRadians(-80), 0, 0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (body != null && body.isValid()) {
                body.setRightArmPose(EulerAngle.ZERO);
            }
        }, 6L);
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
        double radius = definition.getDespawnDistance();
        double radiusSq = radius * radius;
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    public void despawn() {
        spawned = false;
        if (body != null) {
            body.remove();
            body = null;
        }
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
        baseYaw = location.getYaw();
        if (spawned) {
            spawn();
        }
    }
}
