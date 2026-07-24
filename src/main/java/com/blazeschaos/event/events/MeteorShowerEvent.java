package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
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
                    random.nextInt(12, 22),
                    random.nextInt(-10, 11));
            FallingBlock meteor = loc.getWorld().spawnFallingBlock(loc, Material.MAGMA_BLOCK.createBlockData());
            meteor.setDropItem(false);
            meteor.setHurtEntities(true);
            meteor.setVelocity(new Vector(
                    random.nextDouble(-0.3, 0.3),
                    -1.2,
                    random.nextDouble(-0.3, 0.3)));
            game.trackEntity(meteor);
            game.scheduleMeteorExplosion(loc.clone().subtract(0, 15, 0), power);
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
