package com.blazeschaos.game;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.util.ColorUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GameInstance {

    private final BlazesChaosPlugin plugin;
    private final Arena arena;
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();
    private final Set<UUID> alive = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final List<Entity> trackedEntities = new ArrayList<>();
    private final Map<UUID, ItemStack[]> inventoryBackup = new ConcurrentHashMap<>();

    private GameState state = GameState.WAITING;
    private @Nullable ChaosEvent activeEvent;
    private int countdown;
    private int gameTicks;
    private int chaosTicksRemaining;
    private int eventTicksRemaining;
    private int endingTicks;
    private @Nullable BukkitTask task;
    private @Nullable UUID winner;

    public GameInstance(@NotNull BlazesChaosPlugin plugin, @NotNull Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.countdown = plugin.configs().config().getInt("settings.countdown-seconds", 30);
        resetChaosTimer();
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
        return activeEvent;
    }

    public void setActiveEvent(@Nullable ChaosEvent activeEvent) {
        this.activeEvent = activeEvent;
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

    public void setEventTicksRemaining(int eventTicksRemaining) {
        this.eventTicksRemaining = eventTicksRemaining;
    }

    public int getEventTicksRemaining() {
        return eventTicksRemaining;
    }

    public @Nullable UUID getWinner() {
        return winner;
    }

    public void resetChaosTimer() {
        this.chaosTicksRemaining = plugin.eventManager().intervalSeconds() * 20;
    }

    public boolean isFull() {
        return players.size() >= arena.getMaxPlayers();
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
            player.sendMessage(ColorUtil.parse(plugin.configs().prefix() + mini));
        }
    }

    public boolean join(@NotNull Player player) {
        if (!state.isJoinable() || isFull() || players.contains(player.getUniqueId())) {
            return false;
        }
        players.add(player.getUniqueId());
        if (plugin.configs().config().getBoolean("settings.clear-inventory-on-join", true)) {
            inventoryBackup.put(player.getUniqueId(), player.getInventory().getContents().clone());
            player.getInventory().clear();
        }
        player.setGameMode(GameMode.ADVENTURE);
        resetPlayerVitals(player);
        Location lobby = arena.getLobby() != null ? arena.getLobby() : arena.getSpawn();
        if (lobby != null) {
            player.teleport(lobby);
        }
        plugin.lobbyManager().giveLeaveItem(player);
        plugin.scoreboardManager().apply(player, this);
        plugin.configs().send(player, "game.joined", Map.of("arena", arena.getName()));
        if (players.size() >= arena.getMinPlayers() && state == GameState.WAITING) {
            beginCountdown();
        } else if (state == GameState.LOBBY) {
            state = GameState.WAITING;
        }
        return true;
    }

    private void resetPlayerVitals(@NotNull Player player) {
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public void leave(@NotNull Player player, boolean voluntary) {
        UUID uuid = player.getUniqueId();
        boolean wasAlive = alive.remove(uuid);
        players.remove(uuid);
        spectators.remove(uuid);
        restoreInventory(player);
        player.setGameMode(GameMode.SURVIVAL);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        plugin.scoreboardManager().remove(player);
        Location lobby = plugin.lobbyManager().getLobbyLocation();
        if (lobby != null) {
            player.teleport(lobby);
        }
        if (voluntary) {
            plugin.configs().send(player, "game.left");
        }
        if (state.isActive() && wasAlive) {
            checkWinCondition();
        } else if (state == GameState.STARTING && players.size() < arena.getMinPlayers()) {
            cancelCountdown();
        }
    }

    public void forceStart() {
        if (players.isEmpty()) {
            return;
        }
        startGame();
    }

    public void stop(@NotNull String reasonKey) {
        if (activeEvent != null) {
            plugin.eventManager().endEvent(this);
        }
        for (Player player : getPlayers()) {
            plugin.configs().send(player, reasonKey);
        }
        endGame(null);
    }

    public void forceNextEvent() {
        if (!state.isActive()) {
            return;
        }
        if (activeEvent != null) {
            plugin.eventManager().endEvent(this);
        }
        ChaosEvent next = plugin.eventManager().pickRandom(null);
        if (next != null) {
            plugin.eventManager().startEvent(this, next);
        }
    }

    private void beginCountdown() {
        state = GameState.STARTING;
        countdown = plugin.configs().config().getInt("settings.countdown-seconds", 30);
        ensureTask();
    }

    private void cancelCountdown() {
        state = GameState.WAITING;
        countdown = plugin.configs().config().getInt("settings.countdown-seconds", 30);
        for (Player player : getPlayers()) {
            plugin.configs().send(player, "game.countdown-cancelled");
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
        plugin.scoreboardManager().updateGame(this);
        switch (state) {
            case STARTING -> tickStarting();
            case PLAYING, CHAOS_EVENT, DEATHMATCH -> tickPlaying();
            case ENDING -> tickEnding();
            default -> {
            }
        }
    }

    private void tickStarting() {
        if (players.size() < arena.getMinPlayers()) {
            cancelCountdown();
            return;
        }
        if (countdown <= 0) {
            startGame();
            return;
        }
        if (gameTicks % 20 == 0) {
            if (countdown <= 5 || countdown % 10 == 0 || countdown == plugin.configs().config().getInt("settings.countdown-seconds", 30)) {
                for (Player player : getPlayers()) {
                    plugin.configs().send(player, "game.starting", Map.of("time", String.valueOf(countdown)));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }
            }
            countdown--;
        }
        gameTicks++;
    }

    private void startGame() {
        state = GameState.PLAYING;
        gameTicks = 0;
        resetChaosTimer();
        alive.clear();
        alive.addAll(players);
        spectators.clear();
        setupBorder();
        Location spawn = arena.getSpawn();
        for (Player player : getPlayers()) {
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            resetPlayerVitals(player);
            if (spawn != null) {
                player.teleport(scatter(spawn, players.size()));
            }
            plugin.configs().send(player, "game.started");
            player.showTitle(Title.title(
                    ColorUtil.parse("<gradient:#FF4500:#FFD700><bold>CHAOS BEGINS!</bold></gradient>"),
                    ColorUtil.parse("<gray>Survive the chaos</gray>"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))
            ));
            plugin.database().addGame(player.getUniqueId(), player.getName());
        }
        ensureTask();
    }

    private @NotNull Location scatter(@NotNull Location center, int count) {
        double angle = Math.random() * Math.PI * 2;
        double radius = Math.min(12.0, arena.getBorderSize() / 8.0);
        return center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }

    private void setupBorder() {
        World world = arena.getWorld();
        Location spawn = arena.getSpawn();
        if (world == null || spawn == null) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        border.setCenter(spawn);
        border.setSize(arena.getBorderSize());
        border.setDamageAmount(arena.getBorderDamage());
        border.setWarningDistance(10);
    }

    private void tickPlaying() {
        gameTicks++;
        checkDeathHeight();

        if (activeEvent != null) {
            activeEvent.tick(this, gameTicks);
            eventTicksRemaining--;
            if (eventTicksRemaining <= 0) {
                plugin.eventManager().endEvent(this);
            }
        } else {
            chaosTicksRemaining--;
            if (chaosTicksRemaining <= 0) {
                ChaosEvent next = plugin.eventManager().pickRandom(null);
                if (next != null) {
                    plugin.eventManager().startEvent(this, next);
                } else {
                    resetChaosTimer();
                }
            }
        }

        int deathmatchAfter = plugin.configs().config().getInt("settings.deathmatch-after-seconds", 600);
        if (state != GameState.DEATHMATCH && getGameSeconds() >= deathmatchAfter) {
            beginDeathmatch();
        }

        checkWinCondition();
    }

    private void beginDeathmatch() {
        state = GameState.DEATHMATCH;
        World world = arena.getWorld();
        if (world != null) {
            double target = plugin.configs().config().getDouble("settings.deathmatch-border-size", 50);
            long seconds = (long) Math.max(10, (world.getWorldBorder().getSize() - target) / Math.max(0.1, arena.getBorderSpeed()));
            world.getWorldBorder().setSize(target, seconds);
        }
        for (Player player : getPlayers()) {
            plugin.configs().send(player, "game.deathmatch");
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.0f);
        }
    }

    private void checkDeathHeight() {
        for (Player player : new ArrayList<>(getAlivePlayers())) {
            if (player.getLocation().getY() < arena.getDeathHeight()) {
                eliminate(player, null);
            }
        }
    }

    public void eliminate(@NotNull Player player, @Nullable Player killer) {
        if (!alive.remove(player.getUniqueId())) {
            return;
        }
        spectators.add(player.getUniqueId());
        plugin.database().addDeath(player.getUniqueId(), player.getName());
        if (killer != null && !killer.getUniqueId().equals(player.getUniqueId())) {
            plugin.database().addKill(killer.getUniqueId(), killer.getName());
            plugin.vaultHook().deposit(killer, plugin.configs().config().getDouble("rewards.kill-money", 5.0));
        }
        if (plugin.configs().config().getBoolean("settings.spectator-on-death", true)) {
            player.setGameMode(GameMode.SPECTATOR);
            Location spec = arena.getSpectator() != null ? arena.getSpectator() : arena.getSpawn();
            if (spec != null) {
                player.teleport(spec);
            }
        }
        for (Player viewer : getPlayers()) {
            plugin.configs().send(viewer, "game.eliminated", Map.of(
                    "player", player.getName(),
                    "alive", String.valueOf(alive.size())
            ));
        }
        plugin.scoreboardManager().apply(player, this);
        checkWinCondition();
    }

    private void checkWinCondition() {
        if (!state.isActive()) {
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

    private void endGame(@Nullable UUID winnerId) {
        if (state == GameState.ENDING || state == GameState.RESETTING) {
            return;
        }
        if (activeEvent != null) {
            ChaosEvent ending = activeEvent;
            ending.end(this);
            ending.markEnded();
            activeEvent = null;
        }
        clearTrackedEntities();
        state = GameState.ENDING;
        winner = winnerId;
        endingTicks = plugin.configs().config().getInt("settings.ending-seconds", 10) * 20;

        if (winnerId != null) {
            Player winnerPlayer = Bukkit.getPlayer(winnerId);
            String name = winnerPlayer != null ? winnerPlayer.getName() : "Unknown";
            if (winnerPlayer != null) {
                plugin.database().addWin(winnerId, name);
                plugin.vaultHook().deposit(winnerPlayer, plugin.configs().config().getDouble("rewards.win-money", 100.0));
            }
            for (Player player : getPlayers()) {
                plugin.configs().send(player, "game.winner", Map.of("player", name));
                plugin.vaultHook().deposit(player, plugin.configs().config().getDouble("rewards.participation-money", 10.0));
            }
        } else {
            for (Player player : getPlayers()) {
                plugin.configs().send(player, "game.no-winner");
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
            leave(player, false);
        }
        players.clear();
        alive.clear();
        spectators.clear();
        inventoryBackup.clear();
        World world = arena.getWorld();
        if (world != null) {
            world.getWorldBorder().reset();
        }
        int delay = plugin.configs().worldReset().getInt("reset-delay-ticks", 40);
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.worldResetManager().resetArenaWorld(arena, () -> {
            state = GameState.WAITING;
            gameTicks = 0;
            resetChaosTimer();
            if (task != null) {
                task.cancel();
                task = null;
            }
            plugin.gameManager().onGameReset(this);
        }), delay);
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
        if (activeEvent != null && alive.contains(victim.getUniqueId())) {
            activeEvent.onPlayerDamage(this, victim, event);
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (activeEvent != null) {
            activeEvent.end(this);
            activeEvent = null;
        }
        clearTrackedEntities();
        for (Player player : getPlayers()) {
            restoreInventory(player);
            player.setGameMode(GameMode.SURVIVAL);
            plugin.scoreboardManager().remove(player);
        }
        players.clear();
        alive.clear();
        spectators.clear();
    }
}
