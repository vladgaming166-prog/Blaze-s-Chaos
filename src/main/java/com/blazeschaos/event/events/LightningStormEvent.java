package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class LightningStormEvent extends ChaosEvent {

    private int tickCounter;
    private int interval;

    public LightningStormEvent() {
        super("lightning-storm", "Lightning Storm");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
        interval = scaledInterval(game, settingInt("interval-ticks", 25));
        World world = game.getArena().getWorld();
        if (world != null) {
            world.setStorm(true);
            world.setThundering(true);
        }
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        tickCounter++;
        if (tickCounter % Math.max(5, interval) != 0) {
            return;
        }
        List<Player> alive = game.getAlivePlayers();
        if (alive.isEmpty()) {
            return;
        }
        int strikes = Math.max(1, (int) Math.round(settingInt("strikes-per-wave", 2) * lightningScale(game)));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < strikes; i++) {
            Player target = alive.get(random.nextInt(alive.size()));
            Location loc = target.getLocation().clone().add(
                    random.nextInt(-8, 9), 0, random.nextInt(-8, 9));
            if (loc.getWorld() != null) {
                loc.getWorld().strikeLightning(loc);
            }
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
        World world = game.getArena().getWorld();
        if (world != null) {
            world.setStorm(false);
            world.setThundering(false);
        }
    }
}
