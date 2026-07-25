package com.blazeschaos.npc;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Citizens-backed real player NPC (preferred when Citizens is installed).
 * Uses reflection so Citizens stays a soft-depend (no compile-time jar required).
 */
public final class CitizensNpcBody implements NpcBody {

    private final BlazesChaosPlugin plugin;
    private final NpcDefinition definition;
    private @Nullable Object citizensNpc;
    private @Nullable Entity bukkitEntity;
    private boolean spawned;
    private float renderYaw;
    private float targetYaw;

    public CitizensNpcBody(@NotNull BlazesChaosPlugin plugin, @NotNull NpcDefinition definition) {
        this.plugin = plugin;
        this.definition = definition;
        Location loc = definition.getLocation();
        this.renderYaw = loc == null ? 0f : loc.getYaw();
        this.targetYaw = renderYaw;
    }

    public static boolean available() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            return false;
        }
        try {
            Class.forName("net.citizensnpcs.api.CitizensAPI");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    @Override
    public void spawnForNearby() {
        Location loc = definition.getLocation();
        if (loc == null || loc.getWorld() == null || !definition.isVisible()) {
            despawnAll();
            return;
        }
        // Ensure chunk is loaded
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        if (!loc.getWorld().isChunkLoaded(cx, cz)) {
            loc.getWorld().loadChunk(cx, cz);
        }
        if (citizensNpc == null || !isNpcSpawned()) {
            createAndSpawn(loc);
        } else {
            syncEntity();
            Entity entity = bukkitEntity();
            if (entity != null && entity.getWorld() == loc.getWorld()
                    && entity.getLocation().distanceSquared(loc) > 0.05) {
                entity.teleport(loc);
            }
        }
        spawned = isNpcSpawned();
    }

    private void createAndSpawn(@NotNull Location loc) {
        try {
            destroyCitizensNpc();
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = api.getMethod("getNPCRegistry").invoke(null);
            String name = profileName();
            citizensNpc = registry.getClass()
                    .getMethod("createNPC", EntityType.class, String.class)
                    .invoke(registry, EntityType.PLAYER, name);

            // Hide nameplate — we use our TextDisplay holograms
            try {
                Method data = citizensNpc.getClass().getMethod("data");
                Object meta = data.invoke(citizensNpc);
                boolean set = false;
                try {
                    Class<?> metadata = Class.forName("net.citizensnpcs.api.npc.NPC$Metadata");
                    Object nameplate = null;
                    for (Object constant : metadata.getEnumConstants()) {
                        if (constant.toString().equalsIgnoreCase("NAMEPLATE_VISIBLE")) {
                            nameplate = constant;
                            break;
                        }
                    }
                    if (nameplate != null) {
                        try {
                            meta.getClass().getMethod("setPersistent", metadata, Object.class)
                                    .invoke(meta, nameplate, false);
                            set = true;
                        } catch (NoSuchMethodException ex) {
                            meta.getClass().getMethod("set", metadata, Object.class)
                                    .invoke(meta, nameplate, false);
                            set = true;
                        }
                    }
                } catch (Throwable ignored) {
                }
                if (!set) {
                    try {
                        meta.getClass().getMethod("setPersistent", String.class, Object.class)
                                .invoke(meta, "nameplate-visible", false);
                    } catch (Throwable ignored) {
                        citizensNpc.getClass().getMethod("setName", String.class).invoke(citizensNpc, " ");
                    }
                }
            } catch (Throwable ignored) {
            }

            applySkinBeforeSpawn();
            applyLookClose();

            boolean ok = (Boolean) citizensNpc.getClass()
                    .getMethod("spawn", Location.class)
                    .invoke(citizensNpc, loc);
            if (!ok) {
                // Retry next tick once chunk settles
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        if (citizensNpc != null) {
                            citizensNpc.getClass().getMethod("spawn", Location.class)
                                    .invoke(citizensNpc, definition.getLocation());
                            syncEntity();
                            spawned = isNpcSpawned();
                        }
                    } catch (Throwable ex) {
                        plugin.getLogger().log(Level.WARNING, "Citizens NPC delayed spawn failed", ex);
                    }
                }, 5L);
            }
            syncEntity();
            spawned = isNpcSpawned();
            if (spawned) {
                Entity entity = bukkitEntity;
                if (entity != null) {
                    entity.setSilent(true);
                    entity.setInvulnerable(true);
                    if (entity instanceof LivingEntity living) {
                        living.setRemoveWhenFarAway(false);
                        living.setCollidable(false);
                    }
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn Citizens NPC " + definition.getId(), ex);
            citizensNpc = null;
            bukkitEntity = null;
            spawned = false;
        }
    }

    private void applySkinBeforeSpawn() {
        if (citizensNpc == null) {
            return;
        }
        SkinData skin = definition.getSkin();
        try {
            Class<?> skinTraitClass = Class.forName("net.citizensnpcs.trait.SkinTrait");
            Object trait = citizensNpc.getClass()
                    .getMethod("getOrAddTrait", Class.class)
                    .invoke(citizensNpc, skinTraitClass);
            if (skin.hasTextures() && skin.texture() != null) {
                String key = skin.profileName() != null ? skin.profileName() : definition.getId();
                String signature = skin.signature() != null ? skin.signature() : "";
                try {
                    // setSkinPersistent(name, signature, texture) — note arg order
                    trait.getClass().getMethod("setSkinPersistent", String.class, String.class, String.class)
                            .invoke(trait, key, signature, skin.texture());
                } catch (NoSuchMethodException ex) {
                    trait.getClass().getMethod("setTexture", String.class, String.class)
                            .invoke(trait, skin.texture(), signature);
                }
            } else if (skin.type() == SkinData.Type.PLAYER && skin.value() != null) {
                try {
                    trait.getClass().getMethod("setSkinName", String.class, boolean.class)
                            .invoke(trait, skin.value(), true);
                } catch (NoSuchMethodException ex) {
                    trait.getClass().getMethod("setSkinName", String.class)
                            .invoke(trait, skin.value());
                }
            }
        } catch (Throwable ex) {
            if (plugin.configs().debug()) {
                plugin.getLogger().log(Level.WARNING, "Citizens skin apply failed", ex);
            }
        }
    }

    private void applyLookClose() {
        if (citizensNpc == null || !definition.isLookAtPlayers()) {
            return;
        }
        try {
            Class<?> lookClass = Class.forName("net.citizensnpcs.trait.LookClose");
            Object trait = citizensNpc.getClass()
                    .getMethod("getOrAddTrait", Class.class)
                    .invoke(citizensNpc, lookClass);
            try {
                trait.getClass().getMethod("lookClose", boolean.class).invoke(trait, true);
            } catch (NoSuchMethodException ignored) {
                trait.getClass().getMethod("setLookClose", boolean.class).invoke(trait, true);
            }
            try {
                trait.getClass().getMethod("setRange", double.class)
                        .invoke(trait, definition.getViewDistance());
            } catch (NoSuchMethodException ignored) {
                try {
                    trait.getClass().getMethod("setRange", int.class)
                            .invoke(trait, (int) definition.getViewDistance());
                } catch (NoSuchMethodException ignored2) {
                }
            }
            try {
                trait.getClass().getMethod("setRealisticLooking", boolean.class).invoke(trait, true);
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private void syncEntity() {
        bukkitEntity = null;
        if (citizensNpc == null) {
            return;
        }
        try {
            Object entity = citizensNpc.getClass().getMethod("getEntity").invoke(citizensNpc);
            if (entity instanceof Entity e) {
                bukkitEntity = e;
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isNpcSpawned() {
        if (citizensNpc == null) {
            return false;
        }
        try {
            return (Boolean) citizensNpc.getClass().getMethod("isSpawned").invoke(citizensNpc);
        } catch (Throwable ex) {
            return false;
        }
    }

    private @NotNull String profileName() {
        String skinName = definition.getSkin().profileName();
        if (skinName != null && !skinName.isBlank()) {
            return skinName.length() > 16 ? skinName.substring(0, 16) : skinName;
        }
        String id = definition.getId().replaceAll("[^A-Za-z0-9_]", "_");
        return id.length() > 16 ? id.substring(0, 16) : id;
    }

    @Override
    public void showFor(@NotNull Player player) {
        // Citizens handles visibility via chunk tracking — ensure spawned
        spawnForNearby();
    }

    @Override
    public void hide(@NotNull Player player) {
        // Real entity — no per-viewer hide needed
    }

    @Override
    public void despawnAll() {
        destroyCitizensNpc();
        spawned = false;
        bukkitEntity = null;
    }

    private void destroyCitizensNpc() {
        if (citizensNpc == null) {
            return;
        }
        try {
            if (isNpcSpawned()) {
                citizensNpc.getClass().getMethod("despawn").invoke(citizensNpc);
            }
        } catch (Throwable ignored) {
            try {
                citizensNpc.getClass().getMethod("destroy").invoke(citizensNpc);
                citizensNpc = null;
                return;
            } catch (Throwable ignored2) {
            }
        }
        try {
            citizensNpc.getClass().getMethod("destroy").invoke(citizensNpc);
        } catch (Throwable ignored) {
        }
        citizensNpc = null;
    }

    @Override
    public void tickLook(@Nullable Player nearest) {
        // Citizens LookClose handles this when enabled; soft manual fallback otherwise
        if (definition.isLookAtPlayers() && nearest != null) {
            Entity entity = bukkitEntity();
            if (entity == null) {
                syncEntity();
                entity = bukkitEntity;
            }
            if (entity == null) {
                return;
            }
            Location eyes = entity.getLocation().add(0, 1.62, 0);
            Location target = nearest.getEyeLocation();
            double dx = target.getX() - eyes.getX();
            double dz = target.getZ() - eyes.getZ();
            targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            renderYaw = lerpAngle(renderYaw, targetYaw, 0.28f);
            Location loc = entity.getLocation();
            loc.setYaw(renderYaw);
            entity.setRotation(renderYaw, loc.getPitch());
        }
    }

    @Override
    public void applyIdleMotion(int animTick, @NotNull NpcAnimationType type) {
        Entity entity = bukkitEntity();
        if (entity == null) {
            return;
        }
        Location base = definition.getLocation();
        if (base == null) {
            return;
        }
        if (type == NpcAnimationType.SPIN) {
            float yaw = base.getYaw() + animTick * 6f;
            entity.setRotation(yaw, 0f);
        } else if (type == NpcAnimationType.LOOK_AROUND && !definition.isLookAtPlayers()) {
            float yaw = base.getYaw() + (float) (Math.sin(animTick / 18.0) * 40);
            entity.setRotation(yaw, 0f);
        }
    }

    @Override
    public void playSwing() {
        Entity entity = bukkitEntity();
        if (entity instanceof LivingEntity living) {
            living.swingMainHand();
        }
    }

    @Override
    public void refreshSkin() {
        Location loc = definition.getLocation();
        if (loc == null) {
            return;
        }
        boolean was = spawned;
        despawnAll();
        if (was || definition.isVisible()) {
            createAndSpawn(loc);
        }
    }

    @Override
    public boolean isBodySpawned() {
        return spawned && isNpcSpawned();
    }

    @Override
    public @Nullable Entity bukkitEntity() {
        if (bukkitEntity != null && bukkitEntity.isValid()) {
            return bukkitEntity;
        }
        syncEntity();
        return bukkitEntity;
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
