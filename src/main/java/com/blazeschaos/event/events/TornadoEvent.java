package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public final class TornadoEvent extends ChaosEvent {
    private int tickCounter;
    private Location center;

    public TornadoEvent() {
        super("tornado", "Tornado");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
        center = game.getArena().getCenter();
        if (center == null) {
            center = game.getArena().getSpawn();
        }
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        tickCounter++;
        if (tickCounter % 5 != 0) {
            return;
        }
        double pull = settingDouble("pull-strength", 0.35) * knockbackScale(game);
        center.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0, 2, 0), 30, 1.5, 3, 1.5, 0.02);
        for (Player player : game.getAlivePlayers()) {
            Vector dir = center.toVector().subtract(player.getLocation().toVector());
            if (dir.lengthSquared() < 0.01) {
                continue;
            }
            dir = dir.normalize().multiply(pull);
            dir.setY(0.25);
            player.setVelocity(player.getVelocity().add(dir));
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
