package com.blazeschaos.event;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.event.events.BlockPartyEvent;
import com.blazeschaos.event.events.ChickenMadnessEvent;
import com.blazeschaos.event.events.DarknessEvent;
import com.blazeschaos.event.events.DoubleDamageEvent;
import com.blazeschaos.event.events.FallingBlocksEvent;
import com.blazeschaos.event.events.FireSpreadEvent;
import com.blazeschaos.event.events.FloodEvent;
import com.blazeschaos.event.events.IceAgeEvent;
import com.blazeschaos.event.events.KnockbackMadnessEvent;
import com.blazeschaos.event.events.LavaRisingEvent;
import com.blazeschaos.event.events.LightningStormEvent;
import com.blazeschaos.event.events.LowGravityEvent;
import com.blazeschaos.event.events.MeteorShowerEvent;
import com.blazeschaos.event.events.OnlyBowsEvent;
import com.blazeschaos.event.events.RandomExplosionsEvent;
import com.blazeschaos.event.events.ResistanceEvent;
import com.blazeschaos.event.events.SpleefEvent;
import com.blazeschaos.event.events.StrongWindEvent;
import com.blazeschaos.event.events.TNTRainEvent;
import com.blazeschaos.event.events.TreeGrowthEvent;
import com.blazeschaos.game.GameInstance;
import com.blazeschaos.util.ColorUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class ChaosEventManager {

    private final BlazesChaosPlugin plugin;
    private final Map<String, ChaosEvent> events = new LinkedHashMap<>();

    public ChaosEventManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        registerDefaults();
        reload();
    }

    private void registerDefaults() {
        register(new LavaRisingEvent());
        register(new TNTRainEvent());
        register(new BlockPartyEvent());
        register(new SpleefEvent());
        register(new LightningStormEvent());
        register(new StrongWindEvent());
        register(new IceAgeEvent());
        register(new DarknessEvent());
        register(new MeteorShowerEvent());
        register(new FloodEvent());
        register(new ChickenMadnessEvent());
        register(new RandomExplosionsEvent());
        register(new TreeGrowthEvent());
        register(new FallingBlocksEvent());
        register(new DoubleDamageEvent());
        register(new OnlyBowsEvent());
        register(new KnockbackMadnessEvent());
        register(new LowGravityEvent());
        register(new ResistanceEvent());
        register(new FireSpreadEvent());
    }

    public void register(@NotNull ChaosEvent event) {
        events.put(event.getId(), event);
    }

    public void reload() {
        FileConfiguration config = plugin.configs().events();
        for (ChaosEvent event : events.values()) {
            event.applyConfig(config);
        }
    }

    public @NotNull Collection<ChaosEvent> all() {
        return events.values();
    }

    public @Nullable ChaosEvent get(@NotNull String id) {
        return events.get(id.toLowerCase());
    }

    public int intervalSeconds() {
        return Math.max(5, plugin.configs().events().getInt("interval-seconds", 60));
    }

    public @Nullable ChaosEvent pickRandom(@Nullable ChaosEvent exclude) {
        List<ChaosEvent> list = exclude == null ? List.of() : List.of(exclude);
        return pickRandom(list);
    }

    public @Nullable ChaosEvent pickRandom(@Nullable Collection<ChaosEvent> exclude) {
        Set<String> excluded = new HashSet<>();
        if (exclude != null) {
            for (ChaosEvent event : exclude) {
                excluded.add(event.getId());
            }
        }
        List<ChaosEvent> pool = new ArrayList<>();
        int totalWeight = 0;
        for (ChaosEvent event : events.values()) {
            if (!event.isEnabled() || event.getChance() <= 0 || event.isOnCooldown()) {
                continue;
            }
            if (excluded.contains(event.getId())) {
                continue;
            }
            pool.add(event);
            totalWeight += event.getChance();
        }
        if (pool.isEmpty() || totalWeight <= 0) {
            pool.clear();
            totalWeight = 0;
            for (ChaosEvent event : events.values()) {
                if (event.isEnabled() && event.getChance() > 0 && !excluded.contains(event.getId())) {
                    pool.add(event);
                    totalWeight += event.getChance();
                }
            }
        }
        if (pool.isEmpty() || totalWeight <= 0) {
            // Last resort: any enabled event
            for (ChaosEvent event : events.values()) {
                if (event.isEnabled()) {
                    return event;
                }
            }
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cursor = 0;
        for (ChaosEvent event : pool) {
            cursor += event.getChance();
            if (roll < cursor) {
                return event;
            }
        }
        return pool.getLast();
    }

    public void startEvent(@NotNull GameInstance game, @NotNull ChaosEvent event) {
        game.startChaosEvent(event);
    }

    public void announce(@NotNull GameInstance game, @NotNull ChaosEvent event) {
        FileConfiguration eventsConfig = plugin.configs().events();
        String display = eventsConfig.getString("display-names." + event.getId(), event.getDefaultDisplayName());
        Map<String, String> placeholders = Map.of("event", ColorUtil.strip(display));
        for (Player player : game.getPlayers()) {
            plugin.lang().send(player, "event.starting", placeholders);
            if (eventsConfig.getBoolean("announce-title", true)) {
                player.showTitle(Title.title(
                        ColorUtil.parse("<gradient:#FF4500:#FFD700><bold>CHAOS!</bold></gradient>"),
                        ColorUtil.parse(display),
                        Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))
                ));
            }
            try {
                Sound sound = Sound.valueOf(eventsConfig.getString("announce-sound", "ENTITY_ENDER_DRAGON_GROWL"));
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            }
        }
    }

    public void endEvent(@NotNull GameInstance game) {
        // Compatibility: force-end current primary event by starting a clean wave timer
        ChaosEvent active = game.getActiveEvent();
        if (active != null) {
            active.end(game);
            active.markEnded();
        }
        game.resetChaosTimer();
    }
}
