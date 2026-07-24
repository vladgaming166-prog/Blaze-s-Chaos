package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public final class TreeExplosionEvent extends ChaosEvent {
    private int tickCounter;
    public TreeExplosionEvent() { super("tree-explosion", "Tree Explosion"); }
    @Override public void start(@NotNull GameInstance game) { tickCounter = 0; }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 40);
        tickCounter++;
        if (tickCounter % interval != 0) return;
        float power = (float) settingDouble("power", 1.8);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player player : game.getAlivePlayers()) {
            for (int i = 0; i < 12; i++) {
                Block block = player.getLocation().add(r.nextInt(-10, 11), r.nextInt(-2, 8), r.nextInt(-10, 11)).getBlock();
                if (Tag.LOGS.isTagged(block.getType()) || Tag.LEAVES.isTagged(block.getType())) {
                    block.getWorld().createExplosion(block.getLocation(), power, false, true);
                    break;
                }
            }
        }
    }
    @Override public void end(@NotNull GameInstance game) {}
}
