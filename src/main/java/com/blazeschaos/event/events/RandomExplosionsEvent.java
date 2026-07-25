package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomExplosionsEvent extends ChaosEvent {

    private int tickCounter;

    public RandomExplosionsEvent() {
        super("random-explosions", "Random Explosions");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 35);
        tickCounter++;
        if (tickCounter % interval != 0) {
            return;
        }
        List<Player> alive = game.getAlivePlayers();
        if (alive.isEmpty()) {
            return;
        }
        float power = (float) settingDouble("power", 2.0);
        Player target = alive.get(ThreadLocalRandom.current().nextInt(alive.size()));
        Location loc = target.getLocation().add(
                ThreadLocalRandom.current().nextInt(-6, 7),
                0,
                ThreadLocalRandom.current().nextInt(-6, 7));
        loc.getWorld().createExplosion(loc, power, false, true);
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
