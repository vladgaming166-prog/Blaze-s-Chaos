package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomFireballsEvent extends ChaosEvent {
    private int tickCounter;
    public RandomFireballsEvent() { super("random-fireballs", "Random Fireballs"); }
    @Override public void start(@NotNull GameInstance game) { tickCounter = 0; }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 25);
        tickCounter++;
        if (tickCounter % interval != 0) return;
        List<Player> alive = game.getAlivePlayers();
        if (alive.isEmpty()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Player target = alive.get(r.nextInt(alive.size()));
        Location loc = target.getLocation().add(r.nextInt(-8, 9), r.nextInt(6, 12), r.nextInt(-8, 9));
        Fireball ball = loc.getWorld().spawn(loc, Fireball.class);
        ball.setDirection(target.getLocation().toVector().subtract(loc.toVector()).normalize());
        ball.setYield((float) settingDouble("power", 1.8));
        ball.setIsIncendiary(true);
        game.trackEntity(ball);
    }
    @Override public void end(@NotNull GameInstance game) {}
}
