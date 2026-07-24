package com.blazeschaos.npc;

import com.blazeschaos.util.LocationUtil;
import com.blazeschaos.util.StoredLocation;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class NpcDefinition {

    private final String id;
    private NpcMode mode = NpcMode.SOLO;
    private @Nullable StoredLocation location;
    private final SkinData skin = new SkinData();
    private final List<String> hologramLines = new ArrayList<>();
    private NpcAnimationType animation = NpcAnimationType.IDLE;
    private boolean lookAtPlayers = true;
    private boolean openGui = true;
    private boolean visible = true;
    private @Nullable String permission;
    private double viewDistance = 48.0;
    private double despawnDistance = 64.0;

    public NpcDefinition(@NotNull String id) {
        this.id = id.toLowerCase(Locale.ROOT);
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull NpcMode getMode() {
        return mode;
    }

    public void setMode(@NotNull NpcMode mode) {
        this.mode = mode;
    }

    public @Nullable Location getLocation() {
        return location == null ? null : location.toLocation();
    }

    public void setLocation(@Nullable Location location) {
        this.location = StoredLocation.from(location);
    }

    public @NotNull SkinData getSkin() {
        return skin;
    }

    public @NotNull List<String> getHologramLines() {
        return hologramLines;
    }

    public void setHologramLines(@NotNull List<String> lines) {
        hologramLines.clear();
        hologramLines.addAll(lines);
    }

    public @NotNull NpcAnimationType getAnimation() {
        return animation;
    }

    public void setAnimation(@NotNull NpcAnimationType animation) {
        this.animation = animation;
    }

    public boolean isLookAtPlayers() {
        return lookAtPlayers;
    }

    public void setLookAtPlayers(boolean lookAtPlayers) {
        this.lookAtPlayers = lookAtPlayers;
    }

    public boolean isOpenGui() {
        return openGui;
    }

    public void setOpenGui(boolean openGui) {
        this.openGui = openGui;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public @Nullable String getPermission() {
        return permission;
    }

    public void setPermission(@Nullable String permission) {
        this.permission = permission;
    }

    public double getViewDistance() {
        return viewDistance;
    }

    public void setViewDistance(double viewDistance) {
        this.viewDistance = Math.max(8.0, viewDistance);
    }

    public double getDespawnDistance() {
        return despawnDistance;
    }

    public void setDespawnDistance(double despawnDistance) {
        this.despawnDistance = Math.max(viewDistance, despawnDistance);
    }

    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mode", mode.name().toLowerCase(Locale.ROOT));
        if (location != null) {
            map.put("location", LocationUtil.serialize(location.toLocation()));
        }
        map.put("animation", animation.name().toLowerCase(Locale.ROOT));
        map.put("look-at-players", lookAtPlayers);
        map.put("open-gui", openGui);
        map.put("visible", visible);
        if (permission != null && !permission.isBlank()) {
            map.put("permission", permission);
        }
        map.put("view-distance", viewDistance);
        map.put("despawn-distance", despawnDistance);
        map.put("hologram", new ArrayList<>(hologramLines));

        Map<String, Object> skinMap = new LinkedHashMap<>();
        skinMap.put("type", skin.type().name().toLowerCase(Locale.ROOT));
        if (skin.value() != null) {
            skinMap.put("value", skin.value());
        }
        if (skin.texture() != null) {
            skinMap.put("texture", skin.texture());
        }
        if (skin.signature() != null) {
            skinMap.put("signature", skin.signature());
        }
        if (skin.profileName() != null) {
            skinMap.put("profile-name", skin.profileName());
        }
        if (skin.profileId() != null) {
            skinMap.put("profile-id", skin.profileId().toString());
        }
        map.put("skin", skinMap);
        return map;
    }

    public static @NotNull NpcDefinition deserialize(@NotNull String id, @NotNull ConfigurationSection section) {
        NpcDefinition def = new NpcDefinition(id);
        try {
            def.mode = NpcMode.parse(section.getString("mode", "solo"));
        } catch (Exception ignored) {
            def.mode = NpcMode.SOLO;
        }
        def.location = StoredLocation.deserialize(section.getConfigurationSection("location"));
        try {
            def.animation = NpcAnimationType.parse(section.getString("animation", "idle"));
        } catch (Exception ignored) {
            def.animation = NpcAnimationType.IDLE;
        }
        def.lookAtPlayers = section.getBoolean("look-at-players", true);
        def.openGui = section.getBoolean("open-gui", true);
        def.visible = section.getBoolean("visible", true);
        def.permission = section.getString("permission");
        def.viewDistance = section.getDouble("view-distance", 48.0);
        def.despawnDistance = section.getDouble("despawn-distance", 64.0);
        def.hologramLines.clear();
        def.hologramLines.addAll(section.getStringList("hologram"));
        if (def.hologramLines.isEmpty()) {
            def.hologramLines.addAll(defaultHologram(def.mode));
        }

        ConfigurationSection skinSection = section.getConfigurationSection("skin");
        if (skinSection != null) {
            try {
                def.skin.setType(SkinData.Type.valueOf(skinSection.getString("type", "none").toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
                def.skin.setType(SkinData.Type.NONE);
            }
            def.skin.setValue(skinSection.getString("value"));
            def.skin.setTexture(skinSection.getString("texture"));
            def.skin.setSignature(skinSection.getString("signature"));
            def.skin.setProfileName(skinSection.getString("profile-name"));
            String pid = skinSection.getString("profile-id");
            if (pid != null) {
                try {
                    def.skin.setProfileId(UUID.fromString(pid));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return def;
    }

    public static @NotNull List<String> defaultHologram(@NotNull NpcMode mode) {
        return List.of(
                "<gold><bold>Blaze's Chaos</bold></gold>",
                mode.colorName(),
                "<gray>Players: <aqua>%blazechaos_queue_" + mode.name().toLowerCase(Locale.ROOT) + "%</aqua></gray>",
                "<yellow>Click to Play</yellow>"
        );
    }
}
