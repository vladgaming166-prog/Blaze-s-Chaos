package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class FallingAnvilsEvent extends ChaosEvent {
    private int tickCounter;
    public FallingAnvilsEvent() { super("falling-anvils", "Falling Anvils"); }
    @Override public void start(@NotNull GameInstance game) { tickCounter = 0; }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 25);
        tickCounter++;
        if (tickCounter % interval != 0) return;
        int amount = settingInt("amount", 3);
        List<Player> alive = game.getAlivePlayers();
        if (alive.isEmpty()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < amount; i++) {
            Player target = alive.get(r.nextInt(alive.size()));
            Location loc = target.getLocation().add(r.nextInt(-3, 4), r.nextInt(8, 14), r.nextInt(-3, 4));
            FallingBlock anvil = loc.getWorld().spawnFallingBlock(loc, Material.ANVIL.createBlockData());
            anvil.setHurtEntities(true);
            anvil.setDropItem(false);
            game.trackEntity(anvil);
        }
    }
    @Override public void end(@NotNull GameInstance game) {}
}
