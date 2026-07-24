package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class SpleefEvent extends ChaosEvent {

    private int tickCounter;

    public SpleefEvent() {
        super("spleef", "Spleef");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 15);
        tickCounter++;
        if (tickCounter % interval != 0) {
            return;
        }
        int radius = settingInt("radius", 3);
        for (Player player : game.getAlivePlayers()) {
            Block center = player.getLocation().subtract(0, 1, 0).getBlock();
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getRelative(x, 0, z);
                    if (block.getType().isSolid()) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
