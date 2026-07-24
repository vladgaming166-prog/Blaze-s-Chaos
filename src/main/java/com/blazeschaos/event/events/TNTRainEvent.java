package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class TNTRainEvent extends ChaosEvent {

    private int tickCounter;

    public TNTRainEvent() {
        super("tnt-rain", "TNT Rain");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 20);
        tickCounter++;
        if (tickCounter % interval != 0) {
            return;
        }
        List<Player> alive = game.getAlivePlayers();
        if (alive.isEmpty()) {
            return;
        }
        int amount = settingInt("tnt-per-wave", 3);
        int fuse = settingInt("fuse-ticks", 40);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < amount; i++) {
            Player target = alive.get(random.nextInt(alive.size()));
            Location loc = target.getLocation().clone().add(
                    random.nextInt(-6, 7),
                    random.nextInt(8, 16),
                    random.nextInt(-6, 7)
            );
            TNTPrimed tnt = loc.getWorld().spawn(loc, TNTPrimed.class);
            tnt.setFuseTicks(fuse);
            tnt.setSource(null);
            game.trackEntity(tnt);
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
