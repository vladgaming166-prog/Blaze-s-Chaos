package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public final class StrongWindEvent extends ChaosEvent {

    private int tickCounter;
    private Vector direction;

    public StrongWindEvent() {
        super("strong-wind", "Strong Wind");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        direction = new Vector(Math.cos(angle), 0.15, Math.sin(angle));
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 10);
        tickCounter++;
        if (tickCounter % interval != 0) {
            return;
        }
        double strength = settingDouble("strength", 1.2);
        Vector push = direction.clone().multiply(strength);
        for (Player player : game.getAlivePlayers()) {
            player.setVelocity(player.getVelocity().add(push));
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
