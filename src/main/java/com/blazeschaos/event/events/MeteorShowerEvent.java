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

    public MeteorShowerEvent() {
        super("meteor-shower", "Meteor Shower");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 30);
        tickCounter++;
        if (tickCounter % interval != 0) {
            return;
        }
        List<Player> alive = game.getAlivePlayers();
        if (alive.isEmpty()) {
            return;
        }
        int meteors = settingInt("meteors-per-wave", 2);
        float power = (float) settingDouble("explosion-power", 2.5);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < meteors; i++) {
            Player target = alive.get(random.nextInt(alive.size()));
            Location loc = target.getLocation().clone().add(
                    random.nextInt(-10, 11),
                    random.nextInt(14, 24),
                    random.nextInt(-10, 11));
            if (loc.getWorld() == null) {
                continue;
            }
            Fireball fireball = loc.getWorld().spawn(loc, Fireball.class);
            fireball.setDirection(new Vector(
                    random.nextDouble(-0.2, 0.2),
                    -1.0,
                    random.nextDouble(-0.2, 0.2)));
            fireball.setYield(power);
            fireball.setIsIncendiary(true);
            fireball.setShooter(null);
            game.trackEntity(fireball);
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
