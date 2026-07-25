package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public final class WaterTornadoEvent extends ChaosEvent {

    private int tickCounter;
    private Location center;

    public WaterTornadoEvent() {
        super("water-tornado", "Water Tornado");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
        center = game.centerLocation();
        if (center == null) {
            center = game.spawnLocation();
        }
        if (center != null && center.getWorld() != null) {
            center.getWorld().playSound(center, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.6f);
        }
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        tickCounter++;
        if (tickCounter % 4 != 0) {
            return;
        }
        double pull = settingDouble("pull-strength", 0.4) * knockbackScale(game);
        double damage = settingDouble("damage", 0.5) * damageScale(game);
        center.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, center.clone().add(0, 1, 0), 40, 1.8, 4, 1.8, 0.05);
        center.getWorld().spawnParticle(Particle.SPLASH, center.clone().add(0, 2, 0), 25, 1.2, 2.5, 1.2, 0.02);
        for (Player player : game.getAlivePlayers()) {
            Vector dir = center.toVector().subtract(player.getLocation().toVector());
            if (dir.lengthSquared() < 0.01) {
                continue;
            }
            dir = dir.normalize().multiply(pull);
            dir.setY(0.3);
            player.setVelocity(player.getVelocity().add(dir));
            if (player.getLocation().distanceSquared(center) < 16) {
                player.setRemainingAir(Math.max(0, player.getRemainingAir() - 40));
                if (tickCounter % 20 == 0) {
                    player.damage(damage);
                }
            }
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
