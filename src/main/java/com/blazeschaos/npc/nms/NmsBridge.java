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
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Reflection bridge for Paper 1.21+ packet player NPCs.
 * Uses ServerEntity + getAddEntityPacket when available (required on 1.21).
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
    private Class<?> entityClass;
    private Class<?> entityTypeClass;
    private Class<?> serverEntityClass;
    private Class<?> gameProfileClass;
    private Class<?> propertyClass;
    private Class<?> infoActionClass;
    private Class<?> playerInfoUpdatePacketClass;
    private Class<?> playerInfoRemovePacketClass;
    private Class<?> removeEntitiesPacketClass;
    private Class<?> rotateHeadPacketClass;
    private Class<?> animatePacketClass;
    private Class<?> moveRotClass;
    private Class<?> synchedDataClass;
    private Class<?> entityDataAccessorClass;
    private Class<?> packetFlowClass;
    private Class<?> networkConnectionClass;
    private Class<?> commonListenerCookieClass;
    private Class<?> gamePacketListenerCtorClass;

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
    private Method setId;
    private Method createDefaultClientInfo;
    private Method getProperties;
    private Method putProperty;
    private Method setListed;
    private Method getAddEntityPacket;
    private Method packEntityData;
    private Object entityTypePlayer;

    private Constructor<?> serverPlayerCtor;
    private Constructor<?> gameProfileCtor;
    private Constructor<?> propertyCtor2;
    private Constructor<?> propertyCtor3;
    private Constructor<?> removeEntitiesCtor;
    private Constructor<?> rotateHeadCtor;
    private Constructor<?> animateCtor;
    private Constructor<?> playerInfoRemoveCtor;
    private Constructor<?> moveRotCtor;
    private Constructor<?> serverEntityCtor;
    private Constructor<?> addEntityManualCtor;
    private Constructor<?> setEntityDataCtor;
    private Constructor<?> networkConnectionCtor;
    private Method commonCookieFactory;
    private Constructor<?> gamePacketListenerCtor;
    private Method playerInfoUpdateFactory;
    private Constructor<?> playerInfoUpdateEnumCtor;
    private Field connectionField;
    private Field entityCounterField;
    private final AtomicInteger fallbackEntityId = new AtomicInteger(1_800_000);

    public NmsBridge(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        try {
            init();
            available = true;
            failureReason = null;
            plugin.getLogger().info("Packet player NPC bridge ready (Paper 1.21).");
        } catch (Throwable ex) {
            available = false;
            failureReason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            plugin.getLogger().log(Level.SEVERE,
                    "Packet NPC bridge failed — player NPCs will not render. " + failureReason, ex);
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
        entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
        serverEntityClass = Class.forName("net.minecraft.server.level.ServerEntity");
        synchedDataClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
        entityDataAccessorClass = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
        packetFlowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        networkConnectionClass = Class.forName("net.minecraft.network.Connection");
        commonListenerCookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");

        playerInfoUpdatePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        playerInfoRemovePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
        removeEntitiesPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
        rotateHeadPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket");
        animatePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAnimatePacket");
        infoActionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
        gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        propertyClass = Class.forName("com.mojang.authlib.properties.Property");

        getHandlePlayer = craftPlayerClass.getMethod("getHandle");
        getHandleWorld = craftWorldClass.getMethod("getHandle");
        getServer = craftServerClass.getMethod("getServer");
        sendPacket = findSendMethod(connectionClass, packetClass);
        connectionField = findField(serverPlayerClass, "connection");
        if (connectionField == null) {
            throw new IllegalStateException("ServerPlayer.connection not found");
        }

        setPos = requireMethod(entityClass, double.class, double.class, double.class, "setPos", "snapTo");
        setRot = findMethod(entityClass, float.class, float.class, "setRot", "setYRot");
        setYHeadRot = findMethod(entityClass, float.class, "setYHeadRot", "setHeadRotation");
        setYBodyRot = findMethod(entityClass, float.class, "setYBodyRot", "setBodyRotation");
        getId = requireMethod(entityClass, "getId");
        getUUID = requireMethod(entityClass, "getUUID");
        setId = findMethod(entityClass, int.class, "setId");

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

        entityTypePlayer = entityTypeClass.getField("PLAYER").get(null);

        // ServerEntity(level, entity, updateInterval, trackDelta, broadcast, seenBy)
        serverEntityCtor = findServerEntityCtor();
        getAddEntityPacket = findGetAddEntityPacket();

        removeEntitiesCtor = removeEntitiesPacketClass.getConstructor(int[].class);
        rotateHeadCtor = rotateHeadPacketClass.getConstructor(entityClass, byte.class);
        animateCtor = animatePacketClass.getConstructor(entityClass, int.class);
        playerInfoRemoveCtor = playerInfoRemovePacketClass.getConstructor(List.class);

        try {
            playerInfoUpdateFactory = playerInfoUpdatePacketClass.getMethod("createPlayerInitializing", Collection.class);
        } catch (NoSuchMethodException ex) {
            playerInfoUpdateFactory = null;
            playerInfoUpdateEnumCtor = playerInfoUpdatePacketClass.getConstructor(EnumSet.class, Collection.class);
        }

        try {
            setListed = serverPlayerClass.getMethod("setListed", boolean.class);
        } catch (NoSuchMethodException ex) {
            setListed = null;
        }

        try {
            moveRotClass = Class.forName("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot");
            moveRotCtor = moveRotClass.getConstructor(int.class, byte.class, byte.class, boolean.class);
        } catch (ReflectiveOperationException ex) {
            moveRotCtor = null;
        }

        // Manual add-entity fallback: (id, uuid, x, y, z, pitch, yaw, type, data, velocity, yHeadRot)
        addEntityManualCtor = findManualAddEntityCtor();
        setEntityDataCtor = findSetEntityDataCtor();

        for (String name : new String[]{"getNonDefaultValues", "packAll", "packDirty"}) {
            try {
                packEntityData = synchedDataClass.getMethod(name);
                break;
            } catch (NoSuchMethodException ignored) {
            }
        }

        initDummyConnectionSupport();

        for (Field field : entityClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == AtomicInteger.class) {
                field.setAccessible(true);
                entityCounterField = field;
                break;
            }
        }
    }

    private void initDummyConnectionSupport() {
        try {
            networkConnectionCtor = networkConnectionClass.getConstructor(packetFlowClass);
            try {
                commonCookieFactory = commonListenerCookieClass.getMethod("createInitial", gameProfileClass, boolean.class);
            } catch (NoSuchMethodException ex) {
                commonCookieFactory = null;
            }
            for (Constructor<?> ctor : connectionClass.getConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length >= 3
                        && minecraftServerClass.isAssignableFrom(p[0])
                        && networkConnectionClass.isAssignableFrom(p[1])
                        && serverPlayerClass.isAssignableFrom(p[2])) {
                    gamePacketListenerCtor = ctor;
                    break;
                }
            }
        } catch (Throwable ignored) {
            networkConnectionCtor = null;
            gamePacketListenerCtor = null;
        }
    }

    private @Nullable Constructor<?> findServerEntityCtor() {
        for (Constructor<?> ctor : serverEntityClass.getConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length >= 5
                    && serverLevelClass.isAssignableFrom(p[0])
                    && entityClass.isAssignableFrom(p[1])
                    && (p[2] == int.class || p[2] == Integer.class)) {
                return ctor;
            }
        }
        return null;
    }

    private @Nullable Method findGetAddEntityPacket() {
        for (Method method : serverPlayerClass.getMethods()) {
            if (!method.getName().equals("getAddEntityPacket")) {
                continue;
            }
            if (method.getParameterCount() == 1
                    && serverEntityClass.isAssignableFrom(method.getParameterTypes()[0])) {
                return method;
            }
        }
        // Entity.getAddEntityPacket() no-arg (older)
        try {
            return entityClass.getMethod("getAddEntityPacket");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private @Nullable Constructor<?> findManualAddEntityCtor() {
        try {
            Class<?> add = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
            Class<?> vec3 = Class.forName("net.minecraft.world.phys.Vec3");
            for (Constructor<?> ctor : add.getConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length >= 10
                        && p[0] == int.class
                        && p[1] == UUID.class
                        && p[2] == double.class
                        && entityTypeClass.isAssignableFrom(p[7])) {
                    return ctor;
                }
            }
            // Try Entity-only
            try {
                return add.getConstructor(entityClass);
            } catch (NoSuchMethodException ignored) {
            }
            try {
                return add.getConstructor(entityClass, int.class);
            } catch (NoSuchMethodException ignored) {
            }
            // Entity + ServerEntity
            try {
                return add.getConstructor(entityClass, serverEntityClass);
            } catch (NoSuchMethodException ignored) {
            }
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    private @Nullable Constructor<?> findSetEntityDataCtor() {
        try {
            Class<?> meta = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            try {
                return meta.getConstructor(int.class, List.class);
            } catch (NoSuchMethodException ex) {
                for (Constructor<?> ctor : meta.getConstructors()) {
                    if (ctor.getParameterCount() == 2 && ctor.getParameterTypes()[0] == int.class) {
                        return ctor;
                    }
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    private static @NotNull Method findSendMethod(@NotNull Class<?> connection, @NotNull Class<?> packet)
            throws NoSuchMethodException {
        try {
            return connection.getMethod("send", packet);
        } catch (NoSuchMethodException ex) {
            for (Method method : connection.getMethods()) {
                if (method.getName().equals("send") && method.getParameterCount() >= 1
                        && packet.isAssignableFrom(method.getParameterTypes()[0])) {
                    return method;
                }
            }
            throw ex;
        }
    }

    private static @NotNull String resolveCraftPackage() {
        String name = Bukkit.getServer().getClass().getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(0, lastDot) : "org.bukkit.craftbukkit";
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

    private static @NotNull Method requireMethod(@NotNull Class<?> type, @NotNull String... names)
            throws NoSuchMethodException {
        Method m = findMethod(type, names);
        if (m == null) {
            throw new NoSuchMethodException(type.getName() + " " + String.join(",", names));
        }
        return m;
    }

    private static @NotNull Method requireMethod(@NotNull Class<?> type,
                                                 @NotNull Class<?> p1, @NotNull Class<?> p2, @NotNull Class<?> p3,
                                                 @NotNull String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                Method m = type.getMethod(name, p1, p2, p3);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
            // also try declared
            try {
                Method m = type.getDeclaredMethod(name, p1, p2, p3);
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

    public @NotNull Object createServerPlayer(@NotNull Location location, @NotNull Object profile,
                                              int forcedEntityId) throws Exception {
        Object craftServer = craftServerClass.cast(Bukkit.getServer());
        Object minecraftServer = getServer.invoke(craftServer);
        Object craftWorld = craftWorldClass.cast(location.getWorld());
        Object serverLevel = getHandleWorld.invoke(craftWorld);
        Object clientInfo = createDefaultClientInfo.invoke(null);
        Object npc = serverPlayerCtor.newInstance(minecraftServer, serverLevel, profile, clientInfo);

        if (setId != null) {
            setId.invoke(npc, forcedEntityId);
        }

        // Dummy connection required so PlayerInfo packets can read the NPC safely on 1.21
        attachDummyConnection(npc, minecraftServer, profile);

        setPos.invoke(npc, location.getX(), location.getY(), location.getZ());
        applyRotation(npc, location.getYaw(), location.getPitch());

        // Keep listed=true initially so clients load the skin; tab cleanup will unlist later
        if (setListed != null) {
            setListed.invoke(npc, true);
        }
        return npc;
    }

    private void attachDummyConnection(@NotNull Object npc, @NotNull Object minecraftServer,
                                       @NotNull Object profile) {
        if (networkConnectionCtor == null || gamePacketListenerCtor == null) {
            return;
        }
        try {
            Object clientbound = Enum.valueOf((Class<? extends Enum>) packetFlowClass.asSubclass(Enum.class), "CLIENTBOUND");
            // Prefer SERVERBOUND for inbound dummy socket
            Object flow;
            try {
                flow = Enum.valueOf((Class<? extends Enum>) packetFlowClass.asSubclass(Enum.class), "SERVERBOUND");
            } catch (IllegalArgumentException ex) {
                flow = clientbound;
            }
            Object network = networkConnectionCtor.newInstance(flow);
            Object cookie = null;
            if (commonCookieFactory != null) {
                cookie = commonCookieFactory.invoke(null, profile, false);
            }
            Object listener;
            Class<?>[] params = gamePacketListenerCtor.getParameterTypes();
            Object[] args = new Object[params.length];
            args[0] = minecraftServer;
            args[1] = network;
            args[2] = npc;
            for (int i = 3; i < params.length; i++) {
                if (cookie != null && params[i].isInstance(cookie)) {
                    args[i] = cookie;
                } else if (params[i] == boolean.class) {
                    args[i] = false;
                } else {
                    args[i] = null;
                }
            }
            listener = gamePacketListenerCtor.newInstance(args);
            connectionField.set(npc, listener);
        } catch (Throwable ex) {
            if (plugin.configs().debug()) {
                plugin.getLogger().log(Level.WARNING, "Could not attach dummy NPC connection", ex);
            }
        }
    }

    private void applyRotation(@NotNull Object npc, float yaw, float pitch) throws Exception {
        if (setRot != null) {
            try {
                setRot.invoke(npc, yaw, pitch);
            } catch (Throwable ignored) {
                // some mappings use setYRot only
            }
        }
        if (setYHeadRot != null) {
            setYHeadRot.invoke(npc, yaw);
        }
        if (setYBodyRot != null) {
            setYBodyRot.invoke(npc, yaw);
        }
        // Also try absMoveTo if present
        Method abs = findMethod(entityClass, double.class, double.class, double.class, float.class, float.class,
                "absMoveTo", "snapTo");
        // findMethod with 5 params - need custom
        try {
            Method m = entityClass.getMethod("absMoveTo", double.class, double.class, double.class, float.class, float.class);
            Object x = ((Method) entityClass.getMethod("getX")).invoke(npc);
            Object y = ((Method) entityClass.getMethod("getY")).invoke(npc);
            Object z = ((Method) entityClass.getMethod("getZ")).invoke(npc);
            m.invoke(npc, x, y, z, yaw, pitch);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static @Nullable Method findMethod(@NotNull Class<?> type,
                                               @NotNull Class<?> a, @NotNull Class<?> b, @NotNull Class<?> c,
                                               @NotNull Class<?> d, @NotNull Class<?> e,
                                               @NotNull String... names) {
        for (String name : names) {
            try {
                Method m = type.getMethod(name, a, b, c, d, e);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public int entityId(@NotNull Object nmsEntity) throws Exception {
        return (Integer) getId.invoke(nmsEntity);
    }

    public void updatePosition(@NotNull Object nmsEntity, double x, double y, double z,
                               float yaw, float pitch) throws Exception {
        setPos.invoke(nmsEntity, x, y, z);
        applyRotation(nmsEntity, yaw, pitch);
    }

    public void setListed(@NotNull Object nmsPlayer, boolean listed) {
        if (setListed == null) {
            return;
        }
        try {
            setListed.invoke(nmsPlayer, listed);
        } catch (Throwable ignored) {
        }
    }

    public void sendPlayerInfo(@NotNull Player viewer, @NotNull Object nmsPlayer) throws Exception {
        send(viewer, createAddInfoPacket(nmsPlayer));
    }

    public void sendSpawnEntity(@NotNull Player viewer, @NotNull Object nmsPlayer, @NotNull Location loc) throws Exception {
        Object packet = createAddEntityPacket(nmsPlayer, loc);
        send(viewer, packet);
        Object meta = createMetadataPacket(nmsPlayer);
        if (meta != null) {
            send(viewer, meta);
        }
    }

    /** Full spawn: info then entity. Prefer delayed entity via PacketPlayerNpc. */
    public void sendSpawn(@NotNull Player viewer, @NotNull Object nmsPlayer, @NotNull Location loc) throws Exception {
        sendPlayerInfo(viewer, nmsPlayer);
        sendSpawnEntity(viewer, nmsPlayer, loc);
    }

    private @NotNull Object createAddEntityPacket(@NotNull Object nmsPlayer, @NotNull Location loc) throws Exception {
        Object serverLevel = getHandleWorld.invoke(craftWorldClass.cast(loc.getWorld()));
        Object serverEntity = null;
        if (serverEntityCtor != null) {
            serverEntity = newServerEntity(serverLevel, nmsPlayer);
        }

        if (getAddEntityPacket != null && getAddEntityPacket.getParameterCount() == 1 && serverEntity != null) {
            Object packet = getAddEntityPacket.invoke(nmsPlayer, serverEntity);
            if (packet != null) {
                return packet;
            }
        }
        if (getAddEntityPacket != null && getAddEntityPacket.getParameterCount() == 0) {
            Object packet = getAddEntityPacket.invoke(nmsPlayer);
            if (packet != null) {
                return packet;
            }
        }

        if (addEntityManualCtor != null) {
            Class<?>[] p = addEntityManualCtor.getParameterTypes();
            if (p.length == 1 && entityClass.isAssignableFrom(p[0])) {
                return addEntityManualCtor.newInstance(nmsPlayer);
            }
            if (p.length == 2 && entityClass.isAssignableFrom(p[0]) && serverEntityClass.isAssignableFrom(p[1])
                    && serverEntity != null) {
                return addEntityManualCtor.newInstance(nmsPlayer, serverEntity);
            }
            if (p.length == 2 && entityClass.isAssignableFrom(p[0]) && p[1] == int.class) {
                return addEntityManualCtor.newInstance(nmsPlayer, 0);
            }
            if (p.length >= 10) {
                return buildManualAddEntity(nmsPlayer, loc);
            }
        }
        throw new IllegalStateException("No ClientboundAddEntityPacket constructor available");
    }

    private @NotNull Object newServerEntity(@NotNull Object serverLevel, @NotNull Object nmsPlayer) throws Exception {
        Class<?>[] params = serverEntityCtor.getParameterTypes();
        Object[] args = new Object[params.length];
        args[0] = serverLevel;
        args[1] = nmsPlayer;
        args[2] = 0; // update interval
        Consumer<Object> noop = packet -> {
        };
        for (int i = 3; i < params.length; i++) {
            Class<?> type = params[i];
            if (type == boolean.class || type == Boolean.class) {
                args[i] = false;
            } else if (Consumer.class.isAssignableFrom(type)) {
                args[i] = noop;
            } else if (Set.class.isAssignableFrom(type)) {
                args[i] = Set.of();
            } else if (type.isInterface()) {
                args[i] = null;
            } else {
                args[i] = null;
            }
        }
        return serverEntityCtor.newInstance(args);
    }

    private @NotNull Object buildManualAddEntity(@NotNull Object nmsPlayer, @NotNull Location loc) throws Exception {
        Class<?>[] p = addEntityManualCtor.getParameterTypes();
        Object[] args = new Object[p.length];
        args[0] = entityId(nmsPlayer);
        args[1] = getUUID.invoke(nmsPlayer);
        args[2] = loc.getX();
        args[3] = loc.getY();
        args[4] = loc.getZ();
        // pitch / yaw order varies; Paper uses xRot then yRot as floats
        if (p[5] == float.class) {
            args[5] = loc.getPitch();
            args[6] = loc.getYaw();
        } else if (p[5] == byte.class) {
            args[5] = toAngle(loc.getPitch());
            args[6] = toAngle(loc.getYaw());
        } else {
            args[5] = loc.getPitch();
            args[6] = loc.getYaw();
        }
        args[7] = entityTypePlayer;
        args[8] = 0;
        // velocity Vec3.ZERO
        Object zero = Class.forName("net.minecraft.world.phys.Vec3").getField("ZERO").get(null);
        args[9] = zero;
        if (p.length > 10) {
            if (p[10] == double.class) {
                args[10] = (double) loc.getYaw();
            } else if (p[10] == float.class) {
                args[10] = loc.getYaw();
            } else {
                args[10] = toAngle(loc.getYaw());
            }
        }
        return addEntityManualCtor.newInstance(args);
    }

    private @Nullable Object createMetadataPacket(@NotNull Object nmsPlayer) {
        try {
            enableSkinLayers(nmsPlayer);
            if (packEntityData == null || setEntityDataCtor == null) {
                return null;
            }
            Method getEntityData = entityClass.getMethod("getEntityData");
            Object data = getEntityData.invoke(nmsPlayer);
            Object values = packEntityData.invoke(data);
            if (values == null) {
                return null;
            }
            return setEntityDataCtor.newInstance(entityId(nmsPlayer), values);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void enableSkinLayers(@NotNull Object nmsPlayer) {
        try {
            Class<?> playerClass = Class.forName("net.minecraft.world.entity.player.Player");
            Field accessorField = null;
            for (Field field : playerClass.getDeclaredFields()) {
                if (!entityDataAccessorClass.isAssignableFrom(field.getType())) {
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
            Method set = data.getClass().getMethod("set", entityDataAccessorClass, Object.class);
            set.invoke(data, accessor, (byte) 0x7F);
        } catch (Throwable ignored) {
        }
    }

    public void sendTabRemove(@NotNull Player viewer, @NotNull UUID profileId) throws Exception {
        send(viewer, playerInfoRemoveCtor.newInstance(List.of(profileId)));
    }

    public void sendDespawn(@NotNull Player viewer, @NotNull UUID profileId, int entityId) throws Exception {
        send(viewer, playerInfoRemoveCtor.newInstance(List.of(profileId)));
        send(viewer, removeEntitiesCtor.newInstance((Object) new int[]{entityId}));
    }

    public void sendLook(@NotNull Player viewer, @NotNull Object nmsPlayer, float yaw, float pitch) throws Exception {
        byte yawB = toAngle(yaw);
        byte pitchB = toAngle(pitch);
        send(viewer, rotateHeadCtor.newInstance(nmsPlayer, yawB));
        if (moveRotCtor != null) {
            send(viewer, moveRotCtor.newInstance(entityId(nmsPlayer), yawB, pitchB, true));
        }
    }

    public void sendTeleport(@NotNull Player viewer, @NotNull Object nmsPlayer,
                             double x, double y, double z, float yaw, float pitch) throws Exception {
        // Prefer entity teleport packet via reflection
        try {
            Class<?> teleport = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket");
            try {
                Constructor<?> ctor = teleport.getConstructor(entityClass);
                updatePosition(nmsPlayer, x, y, z, yaw, pitch);
                send(viewer, ctor.newInstance(nmsPlayer));
                return;
            } catch (NoSuchMethodException ignored) {
            }
            // Position sync (1.21.2+)
            try {
                Class<?> sync = Class.forName("net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket");
                Method of = null;
                for (Method method : sync.getMethods()) {
                    if (Modifier.isStatic(method.getModifiers())
                            && method.getParameterCount() == 1
                            && entityClass.isAssignableFrom(method.getParameterTypes()[0])) {
                        of = method;
                        break;
                    }
                }
                if (of != null) {
                    updatePosition(nmsPlayer, x, y, z, yaw, pitch);
                    send(viewer, of.invoke(null, nmsPlayer));
                    return;
                }
            } catch (ClassNotFoundException ignored) {
            }
        } catch (ClassNotFoundException ignored) {
        }
        updatePosition(nmsPlayer, x, y, z, yaw, pitch);
        sendLook(viewer, nmsPlayer, yaw, pitch);
    }

    public void sendSwing(@NotNull Player viewer, @NotNull Object nmsPlayer) throws Exception {
        send(viewer, animateCtor.newInstance(nmsPlayer, 0));
    }

    private void send(@NotNull Player viewer, @NotNull Object packet) throws Exception {
        Object connection = connectionOf(viewer);
        if (sendPacket.getParameterCount() == 1) {
            sendPacket.invoke(connection, packet);
        } else {
            Object[] args = new Object[sendPacket.getParameterCount()];
            args[0] = packet;
            sendPacket.invoke(connection, args);
        }
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
            actions.add(Enum.valueOf((Class<? extends Enum>) infoActionClass, "UPDATE_LISTED"));
        } catch (IllegalArgumentException ignored) {
        }
        try {
            actions.add(Enum.valueOf((Class<? extends Enum>) infoActionClass, "UPDATE_DISPLAY_NAME"));
        } catch (IllegalArgumentException ignored) {
        }
        try {
            actions.add(Enum.valueOf((Class<? extends Enum>) infoActionClass, "UPDATE_GAME_MODE"));
        } catch (IllegalArgumentException ignored) {
        }
        return playerInfoUpdateEnumCtor.newInstance(actions, List.of(nmsPlayer));
    }
}
