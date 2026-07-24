package com.blazeschaos.npc.nms;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Reflection bridge for Paper 1.21 packet-based fake players (real player model).
 * No compile-time NMS/authlib dependency.
 */
public final class NmsBridge {

    private final BlazesChaosPlugin plugin;
    private boolean available;
    private @Nullable String failureReason = "not initialized";

    private Class<?> craftPlayerClass;
    private Class<?> craftServerClass;
    private Class<?> craftWorldClass;
    private Class<?> serverPlayerClass;
    private Class<?> serverLevelClass;
    private Class<?> minecraftServerClass;
    private Class<?> clientInformationClass;
    private Class<?> connectionClass;
    private Class<?> packetClass;
    private Class<?> addEntityPacketClass;
    private Class<?> removeEntitiesPacketClass;
    private Class<?> rotateHeadPacketClass;
    private Class<?> playerInfoUpdatePacketClass;
    private Class<?> playerInfoRemovePacketClass;
    private Class<?> animatePacketClass;
    private Class<?> entityClass;
    private Class<?> infoActionClass;
    private Class<?> gameProfileClass;
    private Class<?> propertyClass;
    private Class<?> moveRotClass;
    private Class<?> teleportPacketClass;

    private Method getHandlePlayer;
    private Method getHandleWorld;
    private Method getServer;
    private Method sendPacket;
    private Method setPos;
    private Method setRot;
    private Method setYHeadRot;
    private Method setYBodyRot;
    private Method getId;
    private Method getUUID;
    private Method createDefaultClientInfo;
    private Method getProperties;
    private Method putProperty;
    private Method setListed;
    private Constructor<?> serverPlayerCtor;
    private Constructor<?> addEntityCtor;
    private Constructor<?> removeEntitiesCtor;
    private Constructor<?> rotateHeadCtor;
    private Constructor<?> animateCtor;
    private Constructor<?> playerInfoRemoveCtor;
    private Constructor<?> gameProfileCtor;
    private Constructor<?> propertyCtor2;
    private Constructor<?> propertyCtor3;
    private Constructor<?> moveRotCtor;
    private Constructor<?> teleportCtorEntity;
    private Constructor<?> teleportCtorCoords;
    private Method playerInfoUpdateFactory;
    private Field connectionField;
    private Field entityCounterField;
    private final AtomicInteger fallbackEntityId = new AtomicInteger(1_500_000);

    public NmsBridge(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        try {
            init();
            available = true;
            failureReason = null;
            plugin.getLogger().info("Packet player NPC bridge initialized.");
        } catch (Throwable ex) {
            available = false;
            failureReason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to initialize packet NPC bridge — real player NPCs unavailable. " + failureReason, ex);
        }
    }

    public boolean available() {
        return available;
    }

    public @Nullable String failureReason() {
        return failureReason;
    }

    private void init() throws Exception {
        String craft = resolveCraftPackage();
        craftPlayerClass = Class.forName(craft + ".entity.CraftPlayer");
        craftServerClass = Class.forName(craft + ".CraftServer");
        craftWorldClass = Class.forName(craft + ".CraftWorld");

        serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
        clientInformationClass = Class.forName("net.minecraft.server.level.ClientInformation");
        connectionClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        packetClass = Class.forName("net.minecraft.network.protocol.Packet");
        entityClass = Class.forName("net.minecraft.world.entity.Entity");

        addEntityPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
        removeEntitiesPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
        rotateHeadPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket");
        playerInfoUpdatePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        playerInfoRemovePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
        animatePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAnimatePacket");
        infoActionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");

        gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        propertyClass = Class.forName("com.mojang.authlib.properties.Property");

        getHandlePlayer = craftPlayerClass.getMethod("getHandle");
        getHandleWorld = craftWorldClass.getMethod("getHandle");
        getServer = craftServerClass.getMethod("getServer");
        sendPacket = connectionClass.getMethod("send", packetClass);

        connectionField = findField(serverPlayerClass, "connection");
        if (connectionField == null) {
            throw new IllegalStateException("ServerPlayer.connection not found");
        }

        setPos = requireMethod(entityClass, double.class, double.class, double.class, "setPos");
        setRot = findMethod(entityClass, float.class, float.class, "setRot");
        setYHeadRot = findMethod(entityClass, float.class, "setYHeadRot", "setHeadRotation");
        setYBodyRot = findMethod(entityClass, float.class, "setYBodyRot", "setBodyRotation");
        getId = requireMethod(entityClass, "getId");
        getUUID = requireMethod(entityClass, "getUUID");
        createDefaultClientInfo = clientInformationClass.getMethod("createDefault");

        gameProfileCtor = gameProfileClass.getConstructor(UUID.class, String.class);
        getProperties = gameProfileClass.getMethod("getProperties");
        putProperty = Class.forName("com.google.common.collect.Multimap")
                .getMethod("put", Object.class, Object.class);
        try {
            propertyCtor3 = propertyClass.getConstructor(String.class, String.class, String.class);
        } catch (NoSuchMethodException ex) {
            propertyCtor3 = null;
        }
        propertyCtor2 = propertyClass.getConstructor(String.class, String.class);

        serverPlayerCtor = serverPlayerClass.getConstructor(
                minecraftServerClass, serverLevelClass, gameProfileClass, clientInformationClass);

        try {
            addEntityCtor = addEntityPacketClass.getConstructor(entityClass);
        } catch (NoSuchMethodException ex) {
            addEntityCtor = addEntityPacketClass.getConstructor(entityClass, int.class);
        }
        removeEntitiesCtor = removeEntitiesPacketClass.getConstructor(int[].class);
        rotateHeadCtor = rotateHeadPacketClass.getConstructor(entityClass, byte.class);
        animateCtor = animatePacketClass.getConstructor(entityClass, int.class);
        playerInfoRemoveCtor = playerInfoRemovePacketClass.getConstructor(List.class);

        try {
            playerInfoUpdateFactory = playerInfoUpdatePacketClass.getMethod("createPlayerInitializing", Collection.class);
        } catch (NoSuchMethodException ex) {
            playerInfoUpdateFactory = null;
        }

        try {
            setListed = serverPlayerClass.getMethod("setListed", boolean.class);
        } catch (NoSuchMethodException ex) {
            setListed = null;
        }

        try {
            moveRotClass = Class.forName("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot");
            moveRotCtor = moveRotClass.getConstructor(int.class, byte.class, byte.class, boolean.class);
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            moveRotClass = null;
            moveRotCtor = null;
        }

        resolveTeleportConstructors();

        for (Field field : entityClass.getDeclaredFields()) {
            if (field.getType() == AtomicInteger.class) {
                field.setAccessible(true);
                entityCounterField = field;
                break;
            }
        }
    }

    private void resolveTeleportConstructors() {
        try {
            teleportPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket");
        } catch (ClassNotFoundException ex) {
            teleportPacketClass = null;
            return;
        }
        try {
            teleportCtorEntity = teleportPacketClass.getConstructor(entityClass);
        } catch (NoSuchMethodException ignored) {
            teleportCtorEntity = null;
        }
        for (Constructor<?> ctor : teleportPacketClass.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length >= 6
                    && params[0] == int.class
                    && params[1] == double.class
                    && params[2] == double.class
                    && params[3] == double.class) {
                teleportCtorCoords = ctor;
                break;
            }
        }
    }

    private static @NotNull String resolveCraftPackage() {
        String name = Bukkit.getServer().getClass().getName();
        // org.bukkit.craftbukkit.CraftServer or org.bukkit.craftbukkit.v1_21_R3.CraftServer
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            return name.substring(0, lastDot);
        }
        return "org.bukkit.craftbukkit";
    }

    private static @Nullable Field findField(@NotNull Class<?> type, @NotNull String name) {
        Class<?> c = type;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ex) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static @NotNull Method requireMethod(@NotNull Class<?> type, @NotNull String... names) throws NoSuchMethodException {
        Method m = findMethod(type, names);
        if (m == null) {
            throw new NoSuchMethodException(type.getName() + " methods " + String.join(",", names));
        }
        return m;
    }

    private static @NotNull Method requireMethod(@NotNull Class<?> type, @NotNull Class<?> p1, @NotNull Class<?> p2,
                                                 @NotNull Class<?> p3, @NotNull String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                Method m = type.getMethod(name, p1, p2, p3);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(type.getName() + " " + names[0]);
    }

    private static @Nullable Method findMethod(@NotNull Class<?> type, @NotNull String... names) {
        for (String name : names) {
            try {
                Method m = type.getMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static @Nullable Method findMethod(@NotNull Class<?> type, @NotNull Class<?> p1, @NotNull String... names) {
        for (String name : names) {
            try {
                Method m = type.getMethod(name, p1);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static @Nullable Method findMethod(@NotNull Class<?> type, @NotNull Class<?> p1, @NotNull Class<?> p2,
                                               @NotNull String... names) {
        for (String name : names) {
            try {
                Method m = type.getMethod(name, p1, p2);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public int nextEntityId() {
        try {
            if (entityCounterField != null) {
                Object counter = entityCounterField.get(null);
                if (counter instanceof AtomicInteger atomic) {
                    return atomic.incrementAndGet();
                }
            }
        } catch (Throwable ignored) {
        }
        return fallbackEntityId.incrementAndGet();
    }

    public @NotNull Object createProfile(@NotNull UUID uuid, @NotNull String name,
                                         @Nullable String texture, @Nullable String signature) throws Exception {
        Object profile = gameProfileCtor.newInstance(uuid, trimName(name));
        if (texture != null && !texture.isBlank()) {
            Object properties = getProperties.invoke(profile);
            Object property = (signature != null && !signature.isBlank() && propertyCtor3 != null)
                    ? propertyCtor3.newInstance("textures", texture, signature)
                    : propertyCtor2.newInstance("textures", texture);
            putProperty.invoke(properties, "textures", property);
        }
        return profile;
    }

    private @NotNull String trimName(@NotNull String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.isBlank()) {
            cleaned = "NPC";
        }
        return cleaned.length() > 16 ? cleaned.substring(0, 16) : cleaned;
    }

    public @NotNull Object createServerPlayer(@NotNull Location location, @NotNull Object profile) throws Exception {
        Object craftServer = craftServerClass.cast(Bukkit.getServer());
        Object minecraftServer = getServer.invoke(craftServer);
        Object craftWorld = craftWorldClass.cast(location.getWorld());
        Object serverLevel = getHandleWorld.invoke(craftWorld);
        Object clientInfo = createDefaultClientInfo.invoke(null);
        Object npc = serverPlayerCtor.newInstance(minecraftServer, serverLevel, profile, clientInfo);
        setPos.invoke(npc, location.getX(), location.getY(), location.getZ());
        if (setRot != null) {
            setRot.invoke(npc, location.getYaw(), location.getPitch());
        }
        if (setYHeadRot != null) {
            setYHeadRot.invoke(npc, location.getYaw());
        }
        if (setYBodyRot != null) {
            setYBodyRot.invoke(npc, location.getYaw());
        }
        if (setListed != null) {
            setListed.invoke(npc, false);
        }
        return npc;
    }

    public int entityId(@NotNull Object nmsEntity) throws Exception {
        return (Integer) getId.invoke(nmsEntity);
    }

    public @NotNull UUID entityUuid(@NotNull Object nmsEntity) throws Exception {
        return (UUID) getUUID.invoke(nmsEntity);
    }

    public void updatePosition(@NotNull Object nmsEntity, double x, double y, double z,
                               float yaw, float pitch) throws Exception {
        setPos.invoke(nmsEntity, x, y, z);
        if (setRot != null) {
            setRot.invoke(nmsEntity, yaw, pitch);
        }
        if (setYHeadRot != null) {
            setYHeadRot.invoke(nmsEntity, yaw);
        }
        if (setYBodyRot != null) {
            setYBodyRot.invoke(nmsEntity, yaw);
        }
    }

    public void sendSpawn(@NotNull Player viewer, @NotNull Object nmsPlayer) throws Exception {
        Object connection = connectionOf(viewer);
        sendPacket.invoke(connection, createAddInfoPacket(nmsPlayer));
        Object addPacket = addEntityCtor.getParameterCount() == 1
                ? addEntityCtor.newInstance(nmsPlayer)
                : addEntityCtor.newInstance(nmsPlayer, 0);
        sendPacket.invoke(connection, addPacket);
        Object meta = createMetadataPacket(nmsPlayer);
        if (meta != null) {
            sendPacket.invoke(connection, meta);
        }
    }

    private @Nullable Object createMetadataPacket(@NotNull Object nmsPlayer) {
        try {
            enableSkinLayers(nmsPlayer);
            Method getEntityData = entityClass.getMethod("getEntityData");
            Object data = getEntityData.invoke(nmsPlayer);
            Method pack = null;
            for (String name : new String[]{"getNonDefaultValues", "packAll", "packDirty"}) {
                try {
                    pack = data.getClass().getMethod(name);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (pack == null) {
                return null;
            }
            Object values = pack.invoke(data);
            if (values == null) {
                return null;
            }
            Class<?> metaClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            try {
                Constructor<?> ctor = metaClass.getConstructor(int.class, List.class);
                return ctor.newInstance(entityId(nmsPlayer), values);
            } catch (NoSuchMethodException ex) {
                for (Constructor<?> ctor : metaClass.getConstructors()) {
                    if (ctor.getParameterCount() == 2 && ctor.getParameterTypes()[0] == int.class) {
                        return ctor.newInstance(entityId(nmsPlayer), values);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void enableSkinLayers(@NotNull Object nmsPlayer) {
        try {
            Class<?> playerClass = Class.forName("net.minecraft.world.entity.player.Player");
            Class<?> accessorClass = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            Field accessorField = null;
            for (Field field : playerClass.getDeclaredFields()) {
                if (!accessorClass.isAssignableFrom(field.getType())) {
                    continue;
                }
                String name = field.getName().toLowerCase();
                if (name.contains("customisation") || name.contains("customization")
                        || (name.contains("model") && name.contains("part"))) {
                    accessorField = field;
                    break;
                }
            }
            if (accessorField == null) {
                try {
                    accessorField = playerClass.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION");
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (accessorField == null) {
                return;
            }
            accessorField.setAccessible(true);
            Object accessor = accessorField.get(null);
            Method getEntityData = entityClass.getMethod("getEntityData");
            Object data = getEntityData.invoke(nmsPlayer);
            Method set = data.getClass().getMethod("set", accessorClass, Object.class);
            // Enable cape + jacket + sleeves + pants + hat
            set.invoke(data, accessor, (byte) 0x7F);
        } catch (Throwable ignored) {
        }
    }

    public void sendTabRemove(@NotNull Player viewer, @NotNull UUID profileId) throws Exception {
        sendPacket.invoke(connectionOf(viewer), playerInfoRemoveCtor.newInstance(List.of(profileId)));
    }

    public void sendDespawn(@NotNull Player viewer, @NotNull Object nmsPlayer,
                            @NotNull UUID profileId, int entityId) throws Exception {
        sendDespawn(viewer, profileId, entityId);
    }

    public void sendDespawn(@NotNull Player viewer, @NotNull UUID profileId, int entityId) throws Exception {
        Object connection = connectionOf(viewer);
        sendPacket.invoke(connection, playerInfoRemoveCtor.newInstance(List.of(profileId)));
        sendPacket.invoke(connection, removeEntitiesCtor.newInstance((Object) new int[]{entityId}));
    }

    public void sendLook(@NotNull Player viewer, @NotNull Object nmsPlayer, float yaw, float pitch) throws Exception {
        Object connection = connectionOf(viewer);
        byte yawB = toAngle(yaw);
        byte pitchB = toAngle(pitch);
        sendPacket.invoke(connection, rotateHeadCtor.newInstance(nmsPlayer, yawB));
        if (moveRotCtor != null) {
            sendPacket.invoke(connection, moveRotCtor.newInstance(entityId(nmsPlayer), yawB, pitchB, true));
        }
    }

    public void sendTeleport(@NotNull Player viewer, @NotNull Object nmsPlayer,
                             double x, double y, double z, float yaw, float pitch) throws Exception {
        Object connection = connectionOf(viewer);
        if (teleportCtorEntity != null) {
            sendPacket.invoke(connection, teleportCtorEntity.newInstance(nmsPlayer));
            return;
        }
        if (teleportCtorCoords != null) {
            Class<?>[] params = teleportCtorCoords.getParameterTypes();
            Object[] args = new Object[params.length];
            args[0] = entityId(nmsPlayer);
            args[1] = x;
            args[2] = y;
            args[3] = z;
            // Remaining args vary by version (rot, onGround, relative flags, etc.)
            for (int i = 4; i < params.length; i++) {
                Class<?> type = params[i];
                if (type == float.class || type == Float.class) {
                    args[i] = (i == 4) ? yaw : pitch;
                } else if (type == byte.class || type == Byte.class) {
                    args[i] = (i == 4) ? toAngle(yaw) : toAngle(pitch);
                } else if (type == boolean.class || type == Boolean.class) {
                    args[i] = true;
                } else if (type == int.class || type == Integer.class) {
                    args[i] = 0;
                } else if (type.isEnum()) {
                    Object[] constants = type.getEnumConstants();
                    args[i] = constants.length > 0 ? constants[0] : null;
                } else {
                    args[i] = null;
                }
            }
            sendPacket.invoke(connection, teleportCtorCoords.newInstance(args));
        }
    }

    public void sendSwing(@NotNull Player viewer, @NotNull Object nmsPlayer) throws Exception {
        sendPacket.invoke(connectionOf(viewer), animateCtor.newInstance(nmsPlayer, 0));
    }

    private static byte toAngle(float degrees) {
        return (byte) Math.floor(degrees * 256.0F / 360.0F);
    }

    private @NotNull Object connectionOf(@NotNull Player player) throws Exception {
        Object handle = getHandlePlayer.invoke(craftPlayerClass.cast(player));
        Object connection = connectionField.get(handle);
        if (connection == null) {
            throw new IllegalStateException("Player connection is null");
        }
        return connection;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @NotNull Object createAddInfoPacket(@NotNull Object nmsPlayer) throws Exception {
        if (playerInfoUpdateFactory != null) {
            return playerInfoUpdateFactory.invoke(null, List.of(nmsPlayer));
        }
        Enum add = Enum.valueOf((Class<? extends Enum>) infoActionClass, "ADD_PLAYER");
        EnumSet actions = EnumSet.of(add);
        try {
            Enum listed = Enum.valueOf((Class<? extends Enum>) infoActionClass, "UPDATE_LISTED");
            actions.add(listed);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            Enum display = Enum.valueOf((Class<? extends Enum>) infoActionClass, "UPDATE_DISPLAY_NAME");
            actions.add(display);
        } catch (IllegalArgumentException ignored) {
        }
        Constructor<?> ctor = playerInfoUpdatePacketClass.getConstructor(EnumSet.class, Collection.class);
        return ctor.newInstance(actions, List.of(nmsPlayer));
    }
}
