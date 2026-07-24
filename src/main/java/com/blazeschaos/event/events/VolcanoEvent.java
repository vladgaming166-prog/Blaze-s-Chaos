package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public final class VolcanoEvent extends ChaosEvent {
    private int tickCounter;
    private Location center;
    public VolcanoEvent() { super("volcano", "Volcano"); }
    @Override public void start(@NotNull GameInstance game) {
        tickCounter = 0;
        center = game.getArena().getCenter();
        if (center == null) center = game.getArena().getSpawn();
    }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        if (center == null || center.getWorld() == null) return;
        int interval = settingInt("interval-ticks", 15);
        tickCounter++;
        if (tickCounter % interval != 0) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location spout = center.clone().add(0, 1, 0);
        FallingBlock magma = spout.getWorld().spawnFallingBlock(spout, Material.MAGMA_BLOCK.createBlockData());
        magma.setVelocity(new Vector(r.nextDouble(-0.6, 0.6), r.nextDouble(0.8, 1.4), r.nextDouble(-0.6, 0.6)));
        magma.setDropItem(false);
        magma.setHurtEntities(true);
        game.trackEntity(magma);
        if (r.nextBoolean()) spout.getWorld().createExplosion(spout, (float) settingDouble("power", 1.2), true, true);
    }
    @Override public void end(@NotNull GameInstance game) {}
}
