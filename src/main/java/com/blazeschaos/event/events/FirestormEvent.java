package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public final class FirestormEvent extends ChaosEvent {
    private int tickCounter;
    public FirestormEvent() { super("firestorm", "Firestorm"); }
    @Override public void start(@NotNull GameInstance game) { tickCounter = 0; }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 15);
        tickCounter++;
        if (tickCounter % interval != 0) return;
        int fires = settingInt("fires-per-wave", 8);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player player : game.getAlivePlayers()) {
            for (int i = 0; i < fires; i++) {
                Block block = player.getLocation().add(r.nextInt(-7, 8), 0, r.nextInt(-7, 8)).getBlock();
                if (block.getType().isAir() && block.getRelative(0, -1, 0).getType().isSolid()) {
                    block.setType(Material.FIRE, false);
                }
            }
            player.setFireTicks(Math.max(player.getFireTicks(), 40));
        }
    }
    @Override public void end(@NotNull GameInstance game) {}
}
