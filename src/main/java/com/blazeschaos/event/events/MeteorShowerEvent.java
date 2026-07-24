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

public final class MeteorShowerEvent extends ChaosEvent {

    private int tickCounter;
    private int interval;

    public MeteorShowerEvent() {
        super("meteor-shower", "Meteor Shower");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
        interval = scaledInterval(game, settingInt("interval-ticks", 30));
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
        int meteors = Math.max(1, (int) Math.round(settingInt("meteors-per-wave", 2) * meteorScale(game)));
        float power = (float) (settingDouble("explosion-power", 2.5) * explosionScale(game));
        int radius = settingInt("meteor-radius", 10);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < meteors; i++) {
            Player target = alive.get(random.nextInt(alive.size()));
            Location loc = target.getLocation().clone().add(
                    random.nextInt(-radius, radius + 1),
                    random.nextInt(14, 24),
                    random.nextInt(-radius, radius + 1));
            if (loc.getWorld() == null) {
                continue;
            }
            Fireball fireball = loc.getWorld().spawn(loc, Fireball.class);
            fireball.setDirection(new Vector(
                    random.nextDouble(-0.2, 0.2),
                    -1.0,
                    random.nextDouble(-0.2, 0.2)));
            fireball.setYield(Math.min(8.0f, power));
            fireball.setIsIncendiary(true);
            fireball.setShooter(null);
            game.trackEntity(fireball);
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
