package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class AcidRainEvent extends ChaosEvent {
    private int tickCounter;
    private int interval;

    public AcidRainEvent() {
        super("acid-rain", "Acid Rain");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
        interval = scaledInterval(game, settingInt("interval-ticks", 20));
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        tickCounter++;
        if (tickCounter % Math.max(5, interval) != 0) {
            return;
        }
        double damage = settingDouble("damage", 1.0) * damageScale(game);
        for (Player player : game.getAlivePlayers()) {
            if (player.getLocation().getBlock().getLightFromSky() > 8
                    && player.getInventory().getHelmet() == null) {
                player.damage(damage);
                if (player.getWorld() != null) {
                    player.getWorld().spawnParticle(Particle.FALLING_SPORE_BLOSSOM,
                            player.getLocation().add(0, 2, 0), 12, 0.6, 0.4, 0.6, 0);
                }
            }
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
