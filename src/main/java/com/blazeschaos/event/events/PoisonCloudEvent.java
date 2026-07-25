package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class PoisonCloudEvent extends ChaosEvent {
    private final List<Location> clouds = new ArrayList<>();
    public PoisonCloudEvent() { super("poison-cloud", "Poison Cloud"); }
    @Override public void start(@NotNull GameInstance game) {
        clouds.clear();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player player : game.getAlivePlayers()) {
            clouds.add(player.getLocation().add(r.nextInt(-4, 5), 0, r.nextInt(-4, 5)));
        }
    }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        if (tick % 10 != 0) return;
        double radius = settingDouble("radius", 4.0);
        for (Location cloud : clouds) {
            if (cloud.getWorld() == null) continue;
            cloud.getWorld().spawnParticle(Particle.WITCH, cloud.clone().add(0, 1, 0), 25, radius / 2, 1, radius / 2, 0);
            for (Player player : game.getAlivePlayers()) {
                if (player.getLocation().distanceSquared(cloud) <= radius * radius) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, settingInt("amplifier", 0), false, true, true));
                }
            }
        }
    }
    @Override public void end(@NotNull GameInstance game) { clouds.clear(); }
}
