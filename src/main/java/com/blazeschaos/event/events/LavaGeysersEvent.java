package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public final class LavaGeysersEvent extends ChaosEvent {
    private int tickCounter;
    public LavaGeysersEvent() { super("lava-geysers", "Lava Geysers"); }
    @Override public void start(@NotNull GameInstance game) { tickCounter = 0; }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 30);
        tickCounter++;
        if (tickCounter % interval != 0) return;
        int height = settingInt("height", 5);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player player : game.getAlivePlayers()) {
            Block base = player.getLocation().add(r.nextInt(-6, 7), -1, r.nextInt(-6, 7)).getBlock();
            for (int y = 0; y < height; y++) {
                Block block = base.getRelative(0, y + 1, 0);
                if (block.getType().isAir()) block.setType(Material.LAVA, false);
            }
        }
    }
    @Override public void end(@NotNull GameInstance game) {}
}
