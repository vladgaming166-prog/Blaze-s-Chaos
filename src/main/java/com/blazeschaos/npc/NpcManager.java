package com.blazeschaos.npc;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.game.GameState;
import com.blazeschaos.npc.gui.NpcGui;
import com.blazeschaos.npc.nms.NmsBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcManager {

    private final BlazesChaosPlugin plugin;
    private final SkinService skins;
    private final NmsBridge nms;
    private final Map<String, NpcDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, NpcInstance> instances = new ConcurrentHashMap<>();
    private final Map<UUID, String> selected = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Set<String>> favorites = new ConcurrentHashMap<>();
    private final java.util.Set<String> resolvingSkins = ConcurrentHashMap.newKeySet();
    private @Nullable BukkitTask task;
    private int tickCounter;

    public NpcManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        this.skins = new SkinService(plugin);
        this.nms = new NmsBridge(plugin);
        if (CitizensNpcBody.available()) {
            plugin.getLogger().info("Citizens detected — using Citizens for player NPCs.");
        } else if (nms.available()) {
            plugin.getLogger().info("Citizens not found — using internal packet player NPCs.");
        } else {
            plugin.getLogger().warning("Citizens not found and packet NPC bridge failed ("
                    + nms.failureReason() + "). NPC holograms will work but player models may not appear. "
                    + "Install Citizens for reliable NPCs.");
        }
        load();
        start();
    }

    public @NotNull SkinService skins() {
        return skins;
    }

    public @NotNull NmsBridge nms() {
        return nms;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 2L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (NpcInstance instance : instances.values()) {
            instance.despawn();
        }
    }

    public void reload() {
        stop();
        load();
        start();
    }

    public void load() {
        for (NpcInstance instance : instances.values()) {
            instance.despawn();
        }
        instances.clear();
        definitions.clear();
        resolvingSkins.clear();

        FileConfiguration config = plugin.configs().npcs();
        ConfigurationSection section = config.getConfigurationSection("npcs");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection npcSection = section.getConfigurationSection(id);
                if (npcSection == null) {
                    continue;
                }
                NpcDefinition def = NpcDefinition.deserialize(id, npcSection);
                definitions.put(def.getId(), def);
                instances.put(def.getId(), new NpcInstance(plugin, nms, def));
            }
        }
        loadFavorites();
        plugin.getLogger().info("Loaded " + definitions.size() + " NPCs"
                + (CitizensNpcBody.available()
                ? " (Citizens)."
                : (nms.available() ? " (packet player models)." : " (packet bridge unavailable).")));
    }

    public void save() {
        FileConfiguration config = plugin.configs().npcs();
        config.set("npcs", null);
        for (NpcDefinition def : definitions.values()) {
            for (Map.Entry<String, Object> entry : def.serialize().entrySet()) {
                config.set("npcs." + def.getId() + "." + entry.getKey(), entry.getValue());
            }
        }
        plugin.configs().save(config, "npcs.yml");
        saveFavorites();
    }

    private void tick() {
        tickCounter++;
        for (NpcInstance instance : instances.values()) {
            NpcDefinition def = instance.definition();
            if (!def.isVisible() || def.getLocation() == null) {
                if (instance.isSpawned()) {
                    instance.despawn();
                }
                continue;
            }
            // Citizens: keep spawned while visible (chunk tracking handles visibility).
            // Packet: spawn/despawn by player proximity.
            boolean shouldBeSpawned = instance.usesCitizens() || instance.hasNearbyPlayers();
            if (shouldBeSpawned && !instance.isSpawned()) {
                spawnReady(instance);
            } else if (!shouldBeSpawned && instance.isSpawned()) {
                instance.despawn();
            } else if (instance.isSpawned()) {
                instance.tick(tickCounter);
            }
        }
    }

    private void spawnReady(@NotNull NpcInstance instance) {
        NpcDefinition def = instance.definition();
        if (def.getSkin().type() != SkinData.Type.NONE && !def.getSkin().hasTextures()) {
            if (!resolvingSkins.add(def.getId())) {
                return;
            }
            String id = def.getId();
            skins.resolveAsync(def.getSkin(), () -> {
                resolvingSkins.remove(id);
                if (!plugin.isEnabled()) {
                    return;
                }
                NpcInstance current = instances.get(id);
                if (current == null || current != instance) {
                    return;
                }
                if (!current.isSpawned()) {
                    current.spawn();
                } else {
                    current.refreshSkin();
                }
                save();
            });
        } else {
            instance.spawn();
        }
    }

    public void clearPlayer(@NotNull Player player) {
        selected.remove(player.getUniqueId());
        hideFrom(player);
    }

    /** Show all nearby NPCs to a player (join / teleport / world change). */
    public void showNearbyFor(@NotNull Player player) {
        Location loc = player.getLocation();
        for (NpcInstance instance : instances.values()) {
            NpcDefinition def = instance.definition();
            Location npcLoc = def.getLocation();
            if (npcLoc == null || npcLoc.getWorld() != loc.getWorld() || !def.isVisible()) {
                continue;
            }
            double range = def.getViewDistance();
            if (npcLoc.distanceSquared(loc) <= range * range) {
                if (!instance.isSpawned()) {
                    spawnReady(instance);
                }
                instance.showFor(player);
            }
        }
    }

    public void hideFrom(@NotNull Player player) {
        for (NpcInstance instance : instances.values()) {
            instance.hideFrom(player);
        }
    }

    public void onChunkLoad(@NotNull org.bukkit.Chunk chunk) {
        for (NpcInstance instance : instances.values()) {
            NpcDefinition def = instance.definition();
            Location loc = def.getLocation();
            if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(chunk.getWorld())) {
                continue;
            }
            if ((loc.getBlockX() >> 4) != chunk.getX() || (loc.getBlockZ() >> 4) != chunk.getZ()) {
                continue;
            }
            if (!def.isVisible()) {
                continue;
            }
            if (!instance.isSpawned()) {
                spawnReady(instance);
            } else {
                instance.showForNearbyInChunk();
            }
        }
    }

    public @NotNull NpcDefinition create(@NotNull Player player, @NotNull NpcMode mode) {
        String id = uniqueId(mode);
        NpcDefinition def = new NpcDefinition(id);
        def.setMode(mode);
        def.setLocation(player.getLocation());
        def.setHologramLines(NpcDefinition.defaultHologram(mode));
        def.setAnimation(NpcAnimationType.LOOK_AROUND);
        definitions.put(id, def);
        NpcInstance instance = new NpcInstance(plugin, nms, def);
        instances.put(id, instance);
        selected.put(player.getUniqueId(), id);
        save();
        spawnReady(instance);
        return def;
    }

    private @NotNull String uniqueId(@NotNull NpcMode mode) {
        String base = mode.name().toLowerCase(Locale.ROOT);
        if (!definitions.containsKey(base)) {
            return base;
        }
        int i = 2;
        while (definitions.containsKey(base + "_" + i)) {
            i++;
        }
        return base + "_" + i;
    }

    public boolean remove(@NotNull String id) {
        NpcInstance instance = instances.remove(id.toLowerCase(Locale.ROOT));
        NpcDefinition removed = definitions.remove(id.toLowerCase(Locale.ROOT));
        if (instance != null) {
            instance.despawn();
        }
        selected.entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(id));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public @Nullable NpcDefinition get(@NotNull String id) {
        return definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public @Nullable NpcInstance getInstance(@NotNull String id) {
        return instances.get(id.toLowerCase(Locale.ROOT));
    }

    public @NotNull Collection<NpcDefinition> all() {
        return definitions.values();
    }

    public void select(@NotNull Player player, @NotNull String id) {
        selected.put(player.getUniqueId(), id.toLowerCase(Locale.ROOT));
    }

    public @Nullable NpcDefinition selected(@NotNull Player player) {
        String id = selected.get(player.getUniqueId());
        return id == null ? null : definitions.get(id);
    }

    public @Nullable NpcDefinition nearest(@NotNull Player player, double radius) {
        NpcDefinition best = null;
        double bestDist = radius * radius;
        Location loc = player.getLocation();
        for (NpcDefinition def : definitions.values()) {
            Location npcLoc = def.getLocation();
            if (npcLoc == null || npcLoc.getWorld() != loc.getWorld()) {
                continue;
            }
            double dist = npcLoc.distanceSquared(loc);
            if (dist < bestDist) {
                bestDist = dist;
                best = def;
            }
        }
        return best;
    }

    public @Nullable NpcInstance byEntity(@NotNull Entity entity) {
        for (NpcInstance instance : instances.values()) {
            if (instance.isEntity(entity)) {
                return instance;
            }
        }
        return null;
    }

    public void handleClick(@NotNull Player player, @NotNull NpcInstance instance) {
        NpcDefinition def = instance.definition();
        if (def.getPermission() != null && !def.getPermission().isBlank()
                && !player.hasPermission(def.getPermission())) {
            plugin.lang().send(player, "general.no-permission");
            return;
        }
        // Open GUI immediately — animation is cosmetic
        if (def.isOpenGui()) {
            NpcGui.openMain(plugin, player, def.getMode());
        } else {
            quickJoin(player, def.getMode().toGameMode());
        }
        instance.playClickAnimation();
    }

    public void quickJoin(@NotNull Player player) {
        quickJoin(player, com.blazeschaos.game.GameModeType.SOLO);
    }

    public void quickJoin(@NotNull Player player, @NotNull com.blazeschaos.game.GameModeType mode) {
        Arena arena = findBestArena();
        if (arena == null) {
            plugin.lang().send(player, "game.no-arenas");
            return;
        }
        plugin.gameManager().join(player, arena, mode);
    }

    public @Nullable Arena findBestArena() {
        Arena best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Arena arena : plugin.arenaManager().all()) {
            if (!arena.isReady() || !arena.isEnabled()) {
                continue;
            }
            GameInstance game = plugin.gameManager().get(arena.getName());
            if (game != null && (!game.getState().isJoinable() || game.isFull())) {
                continue;
            }
            int players = game == null ? 0 : game.playerCount();
            int stateBonus = 0;
            if (game == null || game.getState() == GameState.WAITING || game.getState() == GameState.LOBBY) {
                stateBonus = 1000;
            } else if (game.getState() == GameState.STARTING) {
                stateBonus = 500;
            }
            // Prefer fuller lobbies (more players) then higher capacity remaining
            int score = stateBonus + players * 25 + (arena.getMaxPlayers() - players);
            if (score > bestScore) {
                bestScore = score;
                best = arena;
            }
        }
        return best;
    }

    public int queueCount(@NotNull NpcMode mode) {
        // Team modes share the same queue pool until dedicated team matchmaking exists
        int total = 0;
        for (GameInstance game : plugin.gameManager().all()) {
            if (game.getState().isJoinable()) {
                total += game.playerCount();
            }
        }
        if (mode == NpcMode.RANDOM) {
            return total;
        }
        return total;
    }

    public int onlinePlaying() {
        int total = 0;
        for (GameInstance game : plugin.gameManager().all()) {
            total += game.playerCount();
        }
        return total;
    }

    public boolean isFavorite(@NotNull Player player, @NotNull String arena) {
        return favorites.getOrDefault(player.getUniqueId(), java.util.Set.of())
                .contains(arena.toLowerCase(Locale.ROOT));
    }

    public void toggleFavorite(@NotNull Player player, @NotNull String arena) {
        favorites.computeIfAbsent(player.getUniqueId(), id -> ConcurrentHashMap.newKeySet());
        java.util.Set<String> set = favorites.get(player.getUniqueId());
        String key = arena.toLowerCase(Locale.ROOT);
        if (!set.add(key)) {
            set.remove(key);
        }
        saveFavorites();
    }

    private void loadFavorites() {
        favorites.clear();
        FileConfiguration config = plugin.configs().npcs();
        ConfigurationSection section = config.getConfigurationSection("favorites");
        if (section == null) {
            return;
        }
        for (String uuid : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(uuid);
                List<String> list = section.getStringList(uuid);
                favorites.put(id, ConcurrentHashMap.newKeySet());
                favorites.get(id).addAll(list);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveFavorites() {
        FileConfiguration config = plugin.configs().npcs();
        config.set("favorites", null);
        for (Map.Entry<UUID, java.util.Set<String>> entry : favorites.entrySet()) {
            config.set("favorites." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        plugin.configs().save(config, "npcs.yml");
    }
}
