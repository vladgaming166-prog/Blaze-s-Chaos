package com.blazeschaos.cosmetics;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.database.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders owned/selected particle trails behind players.
 */
public final class CosmeticTrailManager {

    private final BlazesChaosPlugin plugin;
    private final Map<String, TrailDefinition> trails = new ConcurrentHashMap<>();
    private @Nullable BukkitTask task;
    private int tick;

    public CosmeticTrailManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        trails.clear();
        ConfigurationSection section = plugin.configs().shop().getConfigurationSection("items");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection item = section.getConfigurationSection(id);
                if (item == null) {
                    continue;
                }
                if (!"trail".equalsIgnoreCase(item.getString("category", ""))) {
                    continue;
                }
                String particleName = item.getString("particle", "FLAME");
                Particle particle;
                try {
                    particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    particle = Particle.FLAME;
                }
                trails.put(id.toLowerCase(Locale.ROOT), new TrailDefinition(id.toLowerCase(Locale.ROOT), particle,
                        item.getBoolean("dust", false),
                        item.getInt("dust-red", 255),
                        item.getInt("dust-green", 120),
                        item.getInt("dust-blue", 40)));
            }
        }
        // Built-in fallbacks if shop.yml missing trail particles
        putDefault("trail_flame", Particle.FLAME, false);
        putDefault("trail_heart", Particle.HEART, false);
        putDefault("trail_hearts", Particle.HEART, false);
        putDefault("trail_rainbow", Particle.DUST, true);
        putDefault("trail_cloud", Particle.CLOUD, false);
        putDefault("trail_snow", Particle.SNOWFLAKE, false);
        putDefault("trail_soul", Particle.SOUL, false);
        putDefault("trail_electric", Particle.ELECTRIC_SPARK, false);
        putDefault("trail_lava", Particle.LAVA, false);
        putDefault("trail_water", Particle.DRIPPING_WATER, false);
        putDefault("trail_magic", Particle.WITCH, false);
        putDefault("trail_end", Particle.PORTAL, false);
        putDefault("trail_cherry", Particle.CHERRY_LEAVES, false);
        putDefault("trail_leaf", Particle.FALLING_SPORE_BLOSSOM, false);
        putDefault("trail_critical", Particle.CRIT, false);
        putDefault("trail_totem", Particle.TOTEM_OF_UNDYING, false);
        putDefault("trail_dragon", Particle.DRAGON_BREATH, false);
    }

    private void putDefault(@NotNull String id, @NotNull Particle particle, boolean dust) {
        trails.putIfAbsent(id, new TrailDefinition(id, particle, dust, 255, 80, 180));
    }

    public void start() {
        stop();
        int interval = Math.max(1, plugin.getConfig().getInt("cosmetics.trails.tick-interval", 2));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public @Nullable String selectedTrailId(@NotNull Player player) {
        PlayerStats stats = plugin.database().getStats(player.getUniqueId(), player.getName());
        for (String entry : stats.selectedCosmetics()) {
            if (entry.startsWith("trail:")) {
                return entry.substring("trail:".length());
            }
        }
        return null;
    }

    public boolean isTrail(@NotNull String id) {
        return trails.containsKey(id.toLowerCase(Locale.ROOT));
    }

    private void tick() {
        tick++;
        if (!plugin.getConfig().getBoolean("cosmetics.trails.enabled", true)) {
            return;
        }
        int amount = Math.max(1, plugin.getConfig().getInt("cosmetics.trails.particle-amount", 2));
        double spacing = Math.max(0.05, plugin.getConfig().getDouble("cosmetics.trails.spacing", 0.35));
        double speed = Math.max(0.0, plugin.getConfig().getDouble("cosmetics.trails.speed", 0.02));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline() || player.isInvisible() || player.isDead()) {
                continue;
            }
            String trailId = selectedTrailId(player);
            if (trailId == null) {
                continue;
            }
            TrailDefinition def = trails.get(trailId.toLowerCase(Locale.ROOT));
            if (def == null) {
                continue;
            }
            spawnTrail(player, def, amount, spacing, speed);
        }
    }

    private void spawnTrail(@NotNull Player player, @NotNull TrailDefinition def,
                            int amount, double spacing, double speed) {
        Location base = player.getLocation().clone().add(0.0, 0.15, 0.0);
        Vector back = player.getLocation().getDirection().setY(0);
        if (back.lengthSquared() < 0.0001) {
            back = new Vector(0, 0, 1);
        }
        back.normalize().multiply(-spacing);
        Location at = base.add(back);
        if (def.dust || def.particle == Particle.DUST) {
            float hue = (tick % 40) / 40.0f;
            Color color = Color.fromRGB(
                    Math.min(255, Math.max(0, (int) (Math.sin(hue * Math.PI * 2) * 127 + 128))),
                    Math.min(255, Math.max(0, (int) (Math.sin((hue + 0.33) * Math.PI * 2) * 127 + 128))),
                    Math.min(255, Math.max(0, (int) (Math.sin((hue + 0.66) * Math.PI * 2) * 127 + 128)))
            );
            if (!def.dust) {
                color = Color.fromRGB(def.red, def.green, def.blue);
            }
            Particle.DustOptions options = new Particle.DustOptions(color, 1.1f);
            player.getWorld().spawnParticle(Particle.DUST, at, amount, 0.08, 0.05, 0.08, speed, options);
            return;
        }
        try {
            player.getWorld().spawnParticle(def.particle, at, amount, 0.08, 0.05, 0.08, speed);
        } catch (Throwable ignored) {
            player.getWorld().spawnParticle(Particle.FLAME, at, amount, 0.08, 0.05, 0.08, speed);
        }
    }

    private record TrailDefinition(@NotNull String id, @NotNull Particle particle, boolean dust,
                                   int red, int green, int blue) {
    }
}
