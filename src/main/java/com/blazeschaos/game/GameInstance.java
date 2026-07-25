package com.blazeschaos.game;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.util.ColorUtil;
import com.blazeschaos.util.StoredLocation;
import com.blazeschaos.world.EntityCleanup;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GameInstance {

    private final BlazesChaosPlugin plugin;
    private final Arena arena;
    private final GameModeType mode;
    private final String instanceId;
    private final String instanceWorldName;
    private final @Nullable SurvivalObjective survivalObjective;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private final Set<UUID> alive = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Set<UUID> graceProtected = ConcurrentHashMap.newKeySet();
    private final List<Entity> trackedEntities = new ArrayList<>();
    private final List<ActiveChaos> activeEvents = new ArrayList<>();
    private final Map<UUID, ItemStack[]> inventoryBackup = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> joinTicks = new ConcurrentHashMap<>();

    private GameState state = GameState.WAITING;
    private int countdown;
    private int gameTicks;
    private int chaosTicksRemaining;
    private int endingTicks;
    private int graceTicksRemaining;
    private boolean chaosShardSpawned;
    private boolean finished;
    /** Set only by completeSurvivalVictory — blocks accidental Solo Survival wins. */
    private boolean survivalVictoryAuthorized;
    private @Nullable BukkitTask task;
    private @Nullable UUID winner;

    public GameInstance(@NotNull BlazesChaosPlugin plugin, @NotNull Arena arena, @NotNull GameModeType mode,
                        @NotNull String instanceId, @NotNull String instanceWorldName,
                        @Nullable SurvivalObjective survivalObjective) {
        this.plugin = plugin;
        this.arena = arena;
        this.mode = mode;
        this.instanceId = instanceId;
        this.instanceWorldName = instanceWorldName;
        this.survivalObjective = survivalObjective;
        this.countdown = arena.getCountdownSeconds();
        resetChaosTimer();
    }

    public @NotNull String getInstanceId() {
        return instanceId;
    }

    public @NotNull String getInstanceWorldName() {
        return instanceWorldName;
    }

    public boolean isWorldReady() {
        return Bukkit.getWorld(instanceWorldName) != null;
    }

    public @Nullable World getInstanceWorld() {
        return Bukkit.getWorld(instanceWorldName);
    }

    public @Nullable SurvivalObjective getSurvivalObjective() {
        return survivalObjective;
    }

    public @Nullable Location lobbyLocation() {
        return resolve(arena.getLobbyStored() != null ? arena.getLobbyStored() : arena.getSpawnStored());
    }

    public @Nullable Location spawnLocation() {
        return resolve(arena.getSpawnStored());
    }

    public @Nullable Location spectatorLocation() {
        StoredLocation spec = arena.getSpectatorStored();
        return resolve(spec != null ? spec : arena.getSpawnStored());
    }

    public @Nullable Location centerLocation() {
        StoredLocation center = arena.getCenterStored();
        return resolve(center != null ? center : arena.getSpawnStored());
    }

    public @Nullable Location victoryAltarLocation() {
        return resolve(arena.getVictoryAltarStored());
    }

    private @Nullable Location resolve(@Nullable StoredLocation stored) {
        if (stored == null) {
            return null;
        }
        World world = getInstanceWorld();
        if (world == null) {
            return null;
        }
        return stored.withWorld(instanceWorldName).toLocation();
    }

    public @NotNull GameModeType getMode() {
        return mode;
    }

    public int effectiveMinPlayers() {
        if (mode.isSoloSurvival()) {
            return 1;
        }
        String path = "modes.mode-settings." + mode.name() + ".min-players";
        int configured = plugin.getConfig().getInt(path, -1);
        if (configured > 0) {
            return configured;
        }
        return arena.getMinPlayers();
    }

    public int effectiveMaxPlayers() {
        if (mode.isSoloSurvival()) {
            return 1;
        }
        String path = "modes.mode-settings." + mode.name() + ".max-players";
        int configured = plugin.getConfig().getInt(path, -1);
        if (configured > 0) {
            return configured;
        }
        if (mode == GameModeType.MEGA) {
            return Math.max(arena.getMaxPlayers(), 48);
        }
        return arena.getMaxPlayers();
    }

    public int getCountdownSecondsLeft() {
        return Math.max(0, countdown);
    }

    public @NotNull BlazesChaosPlugin plugin() {
        return plugin;
    }

    public @NotNull Arena getArena() {
        return arena;
    }

    public @NotNull GameState getState() {
        return state;
    }

    public void setState(@NotNull GameState state) {
        this.state = state;
    }

    public @Nullable ChaosEvent getActiveEvent() {
        return activeEvents.isEmpty() ? null : activeEvents.getFirst().event();
    }

    public @NotNull List<ChaosEvent> getActiveEvents() {
        List<ChaosEvent> list = new ArrayList<>();
        for (ActiveChaos active : activeEvents) {
            list.add(active.event());
        }
        return list;
    }

    public void setActiveEvent(@Nullable ChaosEvent ignored) {
        // legacy no-op; use startChaosEvent
    }

    public void setEventTicksRemaining(int ignored) {
        // legacy no-op
    }

    public int getEventTicksRemaining() {
        if (activeEvents.isEmpty()) {
            return 0;
        }
        return activeEvents.stream().mapToInt(ActiveChaos::ticksLeft).max().orElse(0);
    }

    public int getGameTicks() {
        return gameTicks;
    }

    public int getGameSeconds() {
        return gameTicks / 20;
    }

    public int getChaosTicksRemaining() {
        return chaosTicksRemaining;
    }

    public int getNextEventSeconds() {
        return Math.max(0, chaosTicksRemaining / 20);
    }

    public @Nullable UUID getWinner() {
        return winner;
    }

    public boolean isInGrace(@NotNull UUID uuid) {
        return graceTicksRemaining > 0 || graceProtected.contains(uuid);
    }

    public void resetChaosTimer() {
        int interval = plugin.configs().events().getInt("interval-seconds",
                plugin.configs().config().getInt("settings.chaos-interval-seconds", 60));
        this.chaosTicksRemaining = Math.max(5, interval) * 20;
    }

    public boolean isFull() {
        return players.size() >= effectiveMaxPlayers();
    }

    public boolean contains(@NotNull UUID uuid) {
        return players.contains(uuid);
    }

    public boolean isAlive(@NotNull UUID uuid) {
        return alive.contains(uuid);
    }

    public boolean isSpectator(@NotNull UUID uuid) {
        return spectators.contains(uuid);
    }

    public @NotNull List<Player> getPlayers() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    public @NotNull List<Player> getAlivePlayers() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : alive) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    public int playerCount() {
        return players.size();
    }

    public int aliveCount() {
        return alive.size();
    }

    public void trackEntity(@NotNull Entity entity) {
        trackedEntities.add(entity);
    }

    public void clearTrackedEntities() {
        for (Entity entity : new ArrayList<>(trackedEntities)) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        trackedEntities.clear();
    }

    public void scheduleMeteorExplosion(@NotNull Location location, float power) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (location.getWorld() != null && state.isActive()) {
                location.getWorld().createExplosion(location, power, false, true);
            }
        }, 25L);
    }

    public void broadcastRaw(@NotNull String mini) {
        for (Player player : getPlayers()) {
            player.sendMessage(ColorUtil.parse(plugin.lang().prefix() + mini));
        }
    }

    public boolean join(@NotNull Player player) {
        if (!state.isJoinable() || isFull() || players.contains(player.getUniqueId())) {
            return false;
        }
        players.add(player.getUniqueId());
        joinTicks.put(player.getUniqueId(), 0);
        if (plugin.configs().config().getBoolean("settings.clear-inventory-on-join", true)) {
            inventoryBackup.put(player.getUniqueId(), player.getInventory().getContents().clone());
            player.getInventory().clear();
        }
        player.setGameMode(GameMode.ADVENTURE);
        resetPlayerVitals(player);
        // Waiting lobby must have ZERO mobs
        plugin.passiveAnimals().clearForGame(this);
        World instanceWorld = getInstanceWorld();
        if (instanceWorld != null) {
            EntityCleanup.wipeMatchEntities(plugin, instanceWorld);
        }
        Location lobby = lobbyLocation();
        if (lobby != null) {
            safeTeleport(player, lobby);
        }
        plugin.lobbyManager().giveLeaveItem(player);
        plugin.scoreboardManager().apply(player, this);
        plugin.tablistManager().apply(player);
        plugin.lang().send(player, "game.joined", Map.of(
                "arena", arena.getDisplayName(),
                "mode", mode.display()
        ));
        showBossBar(player, plugin.lang().raw("bossbar.waiting"), BossBar.Color.YELLOW);
        // Solo Survival: min 1 / max 1 — countdown, then start (never instant victory)
        if (mode.isSoloSurvival() && players.size() >= 1
                && (state == GameState.WAITING || state == GameState.LOBBY)) {
            beginCountdown();
        } else if (!mode.isSoloSurvival()
                && players.size() >= effectiveMinPlayers()
                && (state == GameState.WAITING || state == GameState.LOBBY)) {
            beginCountdown();
        } else if (state == GameState.LOBBY) {
            state = GameState.WAITING;
        }
        ensureTask();
        return true;
    }

    private void resetPlayerVitals(@NotNull Player player) {
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double health = maxHealth != null ? maxHealth.getValue() : 20.0;
        player.setHealth(Math.max(1.0, health));
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
        player.setNoDamageTicks(60);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public void leave(@NotNull Player player, boolean voluntary) {
        fullyRemovePlayer(player, voluntary);
    }

    /**
     * Completely removes the player from the match and restores lobby state.
     */
    public void fullyRemovePlayer(@NotNull Player player, boolean voluntary) {
        UUID uuid = player.getUniqueId();
        boolean wasAlive = alive.remove(uuid);
        players.remove(uuid);
        spectators.remove(uuid);
        graceProtected.remove(uuid);
        joinTicks.remove(uuid);
        hideBossBar(player);
        plugin.gameManager().untrack(player);

        if (!player.isOnline()) {
            inventoryBackup.remove(uuid);
            bossBars.remove(uuid);
            if (state.isActive() && wasAlive) {
                checkWinCondition();
            } else if (state == GameState.STARTING && players.size() < effectiveMinPlayers()) {
                cancelCountdown();
            }
            return;
        }

        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0f);
        player.setFireTicks(0);
        player.setVelocity(new Vector(0, 0, 0));
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.getInventory().clear();
        restoreInventory(player);
        plugin.scoreboardManager().remove(player);

        Location lobby = plugin.lobbyManager().getLobbyLocation();
        if (lobby == null && player.getWorld() != null) {
            lobby = player.getWorld().getSpawnLocation();
        }
        if (lobby != null) {
            safeTeleport(player, lobby);
        }
        plugin.lobbyManager().giveLobbyItems(player);
        plugin.scoreboardManager().applyLobby(player);
        plugin.tablistManager().apply(player);

        if (voluntary) {
            plugin.lang().send(player, "game.left");
        }
        if (state.isActive() && wasAlive) {
            checkWinCondition();
        } else if (state == GameState.STARTING && players.size() < effectiveMinPlayers()) {
            cancelCountdown();
        }
        // Empty waiting instance → tear down temporary world
        if (players.isEmpty() && (state == GameState.WAITING || state == GameState.LOBBY
                || state == GameState.STARTING)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!players.isEmpty() || finished) {
                    return;
                }
                finished = true;
                if (task != null) {
                    task.cancel();
                    task = null;
                }
                plugin.passiveAnimals().clearForGame(this);
                World w = getInstanceWorld();
                if (w != null) {
                    EntityCleanup.wipeMatchEntities(plugin, w);
                }
                plugin.gameManager().onGameFinished(this);
            });
        }
    }

    /**
     * Admin force-start: skips the countdown only.
     * Must never declare victory or reuse last-player-alive logic.
     */
    public void forceStart() {
        if (players.isEmpty() || finished) {
            return;
        }
        if (state != GameState.WAITING && state != GameState.LOBBY && state != GameState.STARTING) {
            return;
        }
        // Solo Survival always allows a single player; other modes keep their min.
        if (!mode.isSoloSurvival() && players.size() < effectiveMinPlayers()) {
            return;
        }
        startGame();
    }

    public void stop(@NotNull String reasonKey) {
        endAllChaosEvents();
        for (Player player : getPlayers()) {
            plugin.lang().send(player, reasonKey);
        }
        endGame(null);
    }

    public void forceNextEvent() {
        if (!state.isActive()) {
            return;
        }
        startChaosWave(true);
    }

    private void beginCountdown() {
        state = GameState.STARTING;
        countdown = arena.getCountdownSeconds();
        ensureTask();
        for (Player player : getPlayers()) {
            showBossBar(player, plugin.lang().raw("bossbar.starting", Map.of("time", String.valueOf(countdown))), BossBar.Color.GREEN);
        }
    }

    private void cancelCountdown() {
        state = GameState.WAITING;
        countdown = arena.getCountdownSeconds();
        for (Player player : getPlayers()) {
            plugin.lang().send(player, "game.countdown-cancelled");
            showBossBar(player, plugin.lang().raw("bossbar.waiting"), BossBar.Color.YELLOW);
        }
    }

    private void ensureTask() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void startTicking() {
        ensureTask();
    }

    private void tick() {
        // Scoreboard/tablist use their own timers — only refresh bossbars here
        if (gameTicks % 5 == 0) {
            for (Player player : getPlayers()) {
                updateBossBar(player);
            }
        }
        switch (state) {
            case WAITING, LOBBY -> {
            }
            case STARTING -> tickStarting();
            case PLAYING, CHAOS_EVENT, DEATHMATCH -> tickPlaying();
            case ENDING -> tickEnding();
            default -> {
            }
        }
    }

    private void tickStarting() {
        if (players.size() < effectiveMinPlayers()) {
            cancelCountdown();
            return;
        }
        if (countdown <= 0) {
            startGame();
            return;
        }
        if (gameTicks % 20 == 0) {
            if (countdown <= 5 || countdown % 10 == 0 || countdown == arena.getCountdownSeconds()) {
                for (Player player : getPlayers()) {
                    plugin.lang().send(player, "game.starting", Map.of("time", String.valueOf(countdown)));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    player.sendActionBar(ColorUtil.parse(plugin.placeholders().apply(player, this,
                            plugin.lang().raw("actionbar.starting", Map.of("time", String.valueOf(countdown))))));
                }
            }
            countdown--;
        }
        gameTicks++;
    }

    private void startGame() {
        if (state.isActive() || state == GameState.ENDING || state == GameState.RESETTING || finished) {
            return;
        }
        state = GameState.PLAYING;
        gameTicks = 0;
        resetChaosTimer();
        // First chaos after a short delay so players can loot/gear up
        int firstDelay = plugin.configs().events().getInt("first-event-delay-seconds", 20);
        firstDelay = plugin.difficultyManager().scaledIntervalSeconds(firstDelay);
        chaosTicksRemaining = Math.max(5, firstDelay) * 20;
        graceTicksRemaining = plugin.configs().config().getInt("settings.spawn-grace-seconds", 5) * 20;
        alive.clear();
        alive.addAll(players);
        spectators.clear();
        graceProtected.clear();
        graceProtected.addAll(players);
        setupBorder();
        World world = getInstanceWorld();
        if (world != null) {
            EntityCleanup.wipeMatchEntities(plugin, world);
        }
        if (plugin.lootManager().isEnabled()) {
            plugin.lootManager().fillInstanceChests(this);
        }
        chaosShardSpawned = false;
        plugin.passiveAnimals().onMatchStart(this);
        Location spawn = spawnLocation();
        for (Player player : getPlayers()) {
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            resetPlayerVitals(player);
            if (spawn != null) {
                Location dest = findSafeScatter(spawn);
                safeTeleport(player, dest);
            }
            plugin.lang().send(player, "game.started");
            if (mode.isSoloSurvival()) {
                if (survivalObjective == SurvivalObjective.SURVIVE) {
                    int seconds = plugin.getConfig().getInt("solo-survival.survive-seconds", 1200);
                    plugin.lang().send(player, "solo-survival.objective-survive",
                            Map.of("time", String.valueOf(seconds)));
                } else {
                    plugin.lang().send(player, "solo-survival.objective-shard");
                }
            }
            String subtitle = "<gray>Survive the chaos</gray>";
            if (mode.isSoloSurvival()) {
                subtitle = survivalObjective == SurvivalObjective.SURVIVE
                        ? "<green>Survive the timer</green>"
                        : "<light_purple>Obtain the Chaos Shard</light_purple>";
            }
            player.showTitle(Title.title(
                    ColorUtil.parse("<gradient:#FF4500:#FFD700><bold>CHAOS BEGINS!</bold></gradient>"),
                    ColorUtil.parse(subtitle),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
            plugin.database().addGame(player.getUniqueId(), player.getName());
            showBossBar(player, plugin.lang().raw("bossbar.playing"), BossBar.Color.RED);
        }
        ensureTask();
    }

    private @NotNull Location findSafeScatter(@NotNull Location center) {
        double angle = Math.random() * Math.PI * 2;
        double radius = Math.min(8.0, Math.max(2.0, arena.getBorderSize() / 16.0));
        Location candidate = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        return findSafeLocation(candidate);
    }

    private @NotNull Location findSafeLocation(@NotNull Location location) {
        World world = location.getWorld();
        if (world == null) {
            return location;
        }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 2, Math.max(world.getMinHeight() + 1, location.getBlockY() + 5));
        for (int y = startY; y >= world.getMinHeight() + 1; y--) {
            Block ground = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            if (ground.getType().isSolid() && !ground.isLiquid()
                    && (feet.getType().isAir() || !feet.getType().isSolid())
                    && (head.getType().isAir() || !head.getType().isSolid())
                    && feet.getType() != Material.LAVA && head.getType() != Material.LAVA) {
                Location safe = new Location(world, x + 0.5, y + 1.0, z + 0.5, location.getYaw(), location.getPitch());
                return safe;
            }
        }
        return new Location(world, x + 0.5, location.getY(), z + 0.5, location.getYaw(), location.getPitch());
    }

    private void safeTeleport(@NotNull Player player, @NotNull Location location) {
        Location safe = findSafeLocation(location);
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(safe);
        player.setFallDistance(0f);
        player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 60));
    }

    private void setupBorder() {
        World world = getInstanceWorld();
        Location center = centerLocation();
        if (world == null || center == null) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        border.setCenter(center);
        border.setSize(Math.max(50.0, arena.getBorderSize()));
        border.setDamageAmount(Math.max(0.0, arena.getBorderDamage()));
        border.setDamageBuffer(2.0);
        border.setWarningDistance(5);
    }

    private int effectiveDeathHeight() {
        Location spawn = spawnLocation();
        int configured = arena.getDeathHeight();
        if (spawn == null) {
            return configured;
        }
        int maxAllowed = spawn.getBlockY() - 5;
        if (configured >= maxAllowed) {
            return Math.min(configured, spawn.getBlockY() - 20);
        }
        return configured;
    }

    private void tickPlaying() {
        gameTicks++;
        if (graceTicksRemaining > 0) {
            graceTicksRemaining--;
            if (graceTicksRemaining == 0) {
                graceProtected.clear();
            }
        }

        if (graceTicksRemaining <= 0) {
            checkDeathHeight();
        }

        tickChaosEngine();

        int deathmatchAfter = arena.getDeathmatchAfterSeconds();
        if (state != GameState.DEATHMATCH && getGameSeconds() >= deathmatchAfter) {
            beginDeathmatch();
        }

        // Solo Survival — SURVIVE timer win
        if (mode.isSoloSurvival() && survivalObjective == SurvivalObjective.SURVIVE && !alive.isEmpty()) {
            int seconds = plugin.getConfig().getInt("solo-survival.survive-seconds", 1200);
            if (getGameSeconds() >= Math.max(1, seconds)) {
                Player winnerPlayer = getAlivePlayers().isEmpty() ? null : getAlivePlayers().getFirst();
                if (winnerPlayer != null) {
                    completeSurvivalVictory(winnerPlayer);
                    return;
                }
            }
        }

        // Chaos Shard objective: optional world drop (loot/craft also grant win on obtain)
        if (mode.isSoloSurvival()
                && survivalObjective == SurvivalObjective.CHAOS_SHARD
                && !chaosShardSpawned
                && plugin.getConfig().getBoolean("solo-survival.shard-world-drop", true)) {
            int shardAfter = plugin.getConfig().getInt("solo-survival.shard-spawn-after-seconds", 180);
            if (getGameSeconds() >= Math.max(10, shardAfter)) {
                chaosShardSpawned = true;
                plugin.survivalObjective().spawnShardInWorld(this);
            }
        }

        if (gameTicks > 0 && gameTicks % (20 * 60) == 0) {
            int reward = plugin.coinsManager().survivalPerMinute();
            for (Player player : getAlivePlayers()) {
                plugin.coinsManager().add(player, reward, "coins.survival");
            }
        }

        checkWinCondition();
    }

    private void tickChaosEngine() {
        // Tick and expire active events
        Iterator<ActiveChaos> iterator = activeEvents.iterator();
        while (iterator.hasNext()) {
            ActiveChaos active = iterator.next();
            if (active.startDelayTicks > 0) {
                active.startDelayTicks--;
                if (active.startDelayTicks == 0) {
                    active.event().start(this);
                    plugin.eventManager().announce(this, active.event());
                }
                continue;
            }
            active.event().tick(this, gameTicks);
            active.ticksLeft--;
            if (active.ticksLeft <= 0) {
                active.event().end(this);
                active.event().markEnded();
                String display = plugin.configs().events().getString("display-names." + active.event().getId(),
                        active.event().getDefaultDisplayName());
                for (Player player : getPlayers()) {
                    plugin.lang().send(player, "event.ending", Map.of("event", ColorUtil.strip(display)));
                }
                iterator.remove();
            }
        }
        if (activeEvents.isEmpty() && state == GameState.CHAOS_EVENT) {
            state = state == GameState.DEATHMATCH ? GameState.DEATHMATCH : GameState.PLAYING;
        }

        chaosTicksRemaining--;
        if (chaosTicksRemaining <= 0) {
            startChaosWave(false);
            resetChaosTimer();
        }
    }

    private void startChaosWave(boolean forced) {
        int maxConcurrent = Math.max(1, plugin.configs().events().getInt("max-concurrent-events", 2));
        int toStart = forced ? 1 : Math.max(1, plugin.configs().events().getInt("events-per-wave", 1));
        int started = 0;
        List<ChaosEvent> exclude = getActiveEvents();
        while (started < toStart && activeEvents.size() < maxConcurrent) {
            ChaosEvent next = plugin.eventManager().pickRandom(exclude);
            if (next == null) {
                break;
            }
            // Avoid duplicate IDs in concurrent list
            boolean already = false;
            for (ActiveChaos active : activeEvents) {
                if (active.event().getId().equals(next.getId())) {
                    already = true;
                    break;
                }
            }
            if (already) {
                exclude = new ArrayList<>(exclude);
                exclude.add(next);
                continue;
            }
            startChaosEvent(next);
            exclude = new ArrayList<>(exclude);
            exclude.add(next);
            started++;
        }
        if (started == 0 && plugin.configs().debug()) {
            plugin.getLogger().warning("Chaos wave produced no events for arena " + arena.getName());
        }
    }

    public void startChaosEvent(@NotNull ChaosEvent event) {
        int scaledDuration = plugin.difficultyManager().scaledDuration(event.getDurationSeconds());
        int durationTicks = Math.max(20, scaledDuration * 20);
        int delayTicks = Math.max(0, event.getStartDelaySeconds()) * 20;
        activeEvents.add(new ActiveChaos(event, durationTicks, delayTicks));
        if (state != GameState.DEATHMATCH) {
            state = GameState.CHAOS_EVENT;
        }
        if (delayTicks <= 0) {
            event.start(this);
            plugin.eventManager().announce(this, event);
        }
        if (plugin.configs().debug()) {
            plugin.getLogger().info("Queued chaos event " + event.getId() + " in " + arena.getName()
                    + " (delay=" + delayTicks + ", duration=" + durationTicks + ")");
        }
    }

    private void endAllChaosEvents() {
        for (ActiveChaos active : new ArrayList<>(activeEvents)) {
            active.event().end(this);
            active.event().markEnded();
        }
        activeEvents.clear();
    }

    private void beginDeathmatch() {
        state = GameState.DEATHMATCH;
        World world = getInstanceWorld();
        if (world != null) {
            double target = plugin.configs().config().getDouble("settings.deathmatch-border-size", 50);
            long seconds = (long) Math.max(10, (world.getWorldBorder().getSize() - target)
                    / Math.max(0.1, arena.getBorderSpeed()));
            world.getWorldBorder().setSize(Math.max(20.0, target), seconds);
        }
        for (Player player : getPlayers()) {
            plugin.lang().send(player, "game.deathmatch");
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.0f);
            player.showTitle(Title.title(
                    ColorUtil.parse("<dark_red><bold>DEATHMATCH</bold></dark_red>"),
                    ColorUtil.parse("<red>The border is collapsing!</red>"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))
            ));
            showBossBar(player, plugin.lang().raw("bossbar.deathmatch"), BossBar.Color.PURPLE);
        }
    }

    private void checkDeathHeight() {
        int deathY = effectiveDeathHeight();
        for (Player player : new ArrayList<>(getAlivePlayers())) {
            if (isInGrace(player.getUniqueId())) {
                continue;
            }
            if (player.getLocation().getY() < deathY) {
                eliminate(player, null);
            }
        }
    }

    public void eliminate(@NotNull Player player, @Nullable Player killer) {
        if (!alive.remove(player.getUniqueId())) {
            return;
        }
        spectators.add(player.getUniqueId());
        graceProtected.remove(player.getUniqueId());
        plugin.database().addDeath(player.getUniqueId(), player.getName());
        if (killer != null && !killer.getUniqueId().equals(player.getUniqueId())) {
            plugin.database().addKill(killer.getUniqueId(), killer.getName());
            plugin.vaultHook().deposit(killer, plugin.configs().config().getDouble("rewards.kill-money", 5.0));
            plugin.coinsManager().add(killer, plugin.coinsManager().killReward(), "coins.kill");
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH) != null
                ? player.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0);
        Location spec = spectatorLocation();
        if (spec != null) {
            player.teleport(spec);
        }

        for (Player viewer : getPlayers()) {
            plugin.lang().send(viewer, "game.eliminated", Map.of(
                    "player", player.getName(),
                    "alive", String.valueOf(alive.size())
            ));
        }
        plugin.scoreboardManager().apply(player, this);
        showBossBar(player, plugin.lang().raw("bossbar.spectator"), BossBar.Color.WHITE);

        int delaySeconds = plugin.configs().config().getInt("settings.death-lobby-delay-seconds", 5);
        plugin.lang().send(player, "game.death-lobby-soon", Map.of("time", String.valueOf(delaySeconds)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!players.contains(player.getUniqueId())) {
                return;
            }
            // If game already ending, leave will happen in mass eject
            if (state == GameState.ENDING || state == GameState.RESETTING) {
                return;
            }
            fullyRemovePlayer(player, false);
            plugin.lang().send(player, "game.sent-to-lobby");
        }, Math.max(1, delaySeconds) * 20L);

        checkWinCondition();
    }

    private void checkWinCondition() {
        if (!state.isActive()) {
            return;
        }
        // Solo Survival has a completely separate win path:
        // SURVIVE → timer in tickPlaying / CHAOS_SHARD → obtain shard.
        // Never win from last-player-alive, empty enemies, force-start, or game start.
        if (mode.isSoloSurvival()) {
            if (alive.isEmpty()) {
                endGame(null); // player died / left — loss, not victory
            }
            return;
        }
        if (alive.isEmpty()) {
            endGame(null);
            return;
        }
        if (alive.size() == 1) {
            endGame(alive.iterator().next());
        }
    }

    /**
     * Solo Survival victory — only called after survive timer OR Chaos Shard obtain.
     */
    public void completeSurvivalVictory(@NotNull Player player) {
        if (!mode.isSoloSurvival() || !state.isActive()) {
            return;
        }
        if (!alive.contains(player.getUniqueId())) {
            return;
        }
        plugin.lang().send(player, "solo-survival.victory");
        survivalVictoryAuthorized = true;
        endGame(player.getUniqueId());
    }

    private void endGame(@Nullable UUID winnerId) {
        if (state == GameState.ENDING || state == GameState.RESETTING || finished) {
            return;
        }
        // Hard guard: Solo Survival must never award a win from Solo/Teams last-alive paths.
        if (mode.isSoloSurvival() && winnerId != null && !survivalVictoryAuthorized) {
            winnerId = null;
        }
        survivalVictoryAuthorized = false;
        endAllChaosEvents();
        clearTrackedEntities();
        plugin.passiveAnimals().clearForGame(this);
        World world = getInstanceWorld();
        if (world != null) {
            EntityCleanup.wipeMatchEntities(plugin, world);
        }
        state = GameState.ENDING;
        winner = winnerId;
        endingTicks = plugin.configs().config().getInt("settings.ending-seconds", 10) * 20;

        if (winnerId != null) {
            Player winnerPlayer = Bukkit.getPlayer(winnerId);
            String name = winnerPlayer != null ? winnerPlayer.getName() : "Unknown";
            if (winnerPlayer != null) {
                plugin.database().addWin(winnerId, name);
                plugin.vaultHook().deposit(winnerPlayer,
                        plugin.configs().config().getDouble("rewards.win-money", 100.0));
                plugin.coinsManager().add(winnerPlayer, plugin.coinsManager().winReward(), "coins.win");
                winnerPlayer.showTitle(Title.title(
                        ColorUtil.parse("<gold><bold>VICTORY</bold></gold>"),
                        ColorUtil.parse("<yellow>You won Blaze's Chaos!</yellow>"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))
                ));
                winnerPlayer.playSound(winnerPlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            for (Player player : getPlayers()) {
                plugin.lang().send(player, "game.winner", Map.of("player", name));
                plugin.vaultHook().deposit(player,
                        plugin.configs().config().getDouble("rewards.participation-money", 10.0));
                plugin.coinsManager().add(player, plugin.coinsManager().participationReward(), "coins.participation");
                showBossBar(player, plugin.lang().raw("bossbar.ending", Map.of("winner", name)), BossBar.Color.YELLOW);
            }
        } else {
            for (Player player : getPlayers()) {
                plugin.lang().send(player, "game.no-winner");
                showBossBar(player, plugin.lang().raw("bossbar.ending-none"), BossBar.Color.WHITE);
            }
        }
        ensureTask();
    }

    private void tickEnding() {
        endingTicks--;
        if (endingTicks > 0) {
            return;
        }
        state = GameState.RESETTING;
        List<Player> snapshot = new ArrayList<>(getPlayers());
        for (Player player : snapshot) {
            fullyRemovePlayer(player, false);
        }
        players.clear();
        alive.clear();
        spectators.clear();
        inventoryBackup.clear();
        graceProtected.clear();
        joinTicks.clear();
        World world = getInstanceWorld();
        if (world != null) {
            world.getWorldBorder().reset();
        }
        plugin.passiveAnimals().clearForGame(this);
        World instanceWorld = getInstanceWorld();
        if (instanceWorld != null) {
            EntityCleanup.wipeMatchEntities(plugin, instanceWorld);
        }
        Runnable finish = () -> {
            finished = true;
            plugin.passiveAnimals().clearForGame(this);
            if (task != null) {
                task.cancel();
                task = null;
            }
            // Destroy temporary instance world (unload + delete)
            plugin.gameManager().onGameFinished(this);
        };
        int delay = plugin.configs().worldReset().getInt("reset-delay-ticks", 40);
        Bukkit.getScheduler().runTaskLater(plugin, finish, delay);
    }

    private void restoreInventory(@NotNull Player player) {
        ItemStack[] backup = inventoryBackup.remove(player.getUniqueId());
        player.getInventory().clear();
        if (backup != null) {
            player.getInventory().setContents(backup);
        }
        player.updateInventory();
    }

    public void handleDamage(@NotNull Player victim, @NotNull org.bukkit.event.entity.EntityDamageEvent event) {
        if (isInGrace(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        for (ActiveChaos active : activeEvents) {
            if (alive.contains(victim.getUniqueId())) {
                active.event().onPlayerDamage(this, victim, event);
            }
        }
    }

    private void showBossBar(@NotNull Player player, @NotNull String mini, @NotNull BossBar.Color color) {
        hideBossBar(player);
        String resolved = plugin.placeholders().apply(player, this, mini);
        BossBar bar = BossBar.bossBar(ColorUtil.parse(resolved), 1.0f, color, BossBar.Overlay.PROGRESS);
        bossBars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
    }

    private void updateBossBar(@NotNull Player player) {
        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) {
            return;
        }
        String text;
        float progress = 1.0f;
        if (state == GameState.STARTING) {
            text = plugin.lang().raw("bossbar.starting", Map.of("time", String.valueOf(countdown)));
            progress = Math.max(0.05f, countdown / (float) Math.max(1, arena.getCountdownSeconds()));
        } else if (state.isActive()) {
            ChaosEvent event = getActiveEvent();
            if (event != null) {
                String display = ColorUtil.strip(plugin.configs().events().getString(
                        "display-names." + event.getId(), event.getDefaultDisplayName()));
                text = plugin.lang().raw("bossbar.event", Map.of(
                        "event", display,
                        "next", String.valueOf(getNextEventSeconds())
                ));
            } else {
                text = plugin.lang().raw("bossbar.next-event", Map.of("next", String.valueOf(getNextEventSeconds())));
            }
            progress = Math.max(0.05f, chaosTicksRemaining / (float) Math.max(1, plugin.eventManager().intervalSeconds() * 20));
        } else {
            return;
        }
        bar.name(ColorUtil.parse(plugin.placeholders().apply(player, this, text)));
        bar.progress(Math.min(1.0f, progress));
    }

    private void hideBossBar(@NotNull Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        endAllChaosEvents();
        clearTrackedEntities();
        plugin.passiveAnimals().clearForGame(this);
        World world = getInstanceWorld();
        if (world != null) {
            EntityCleanup.wipeMatchEntities(plugin, world);
        }
        for (Player player : getPlayers()) {
            hideBossBar(player);
            fullyRemovePlayer(player, false);
        }
        players.clear();
        alive.clear();
        spectators.clear();
    }

    private static final class ActiveChaos {
        private final ChaosEvent event;
        private int ticksLeft;
        private int startDelayTicks;

        private ActiveChaos(@NotNull ChaosEvent event, int ticksLeft, int startDelayTicks) {
            this.event = event;
            this.ticksLeft = ticksLeft;
            this.startDelayTicks = startDelayTicks;
        }

        private ChaosEvent event() {
            return event;
        }

        private int ticksLeft() {
            return ticksLeft;
        }
    }
}
