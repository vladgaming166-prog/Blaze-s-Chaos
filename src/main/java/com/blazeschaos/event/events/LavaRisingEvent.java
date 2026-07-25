package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class LavaRisingEvent extends ChaosEvent {

    private int currentY;
    private int tickCounter;
    private int riseInterval;

    public LavaRisingEvent() {
        super("lava-rising", "Lava Rising");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
        riseInterval = scaledInterval(game, settingInt("rise-interval-ticks", 40));
        World world = game.getInstanceWorld();
        int minY = Integer.MAX_VALUE;
        for (Player player : game.getAlivePlayers()) {
            minY = Math.min(minY, player.getLocation().getBlockY());
        }
        if (minY == Integer.MAX_VALUE && world != null) {
            minY = world.getMinHeight() + 5;
        }
        currentY = minY + settingInt("start-y-offset", -5);
        if (world != null) {
            currentY = Math.max(world.getMinHeight() + 1, currentY);
        }
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        tickCounter++;
        if (tickCounter % Math.max(5, riseInterval) != 0) {
            return;
        }
        World world = game.getInstanceWorld();
        if (world == null) {
            return;
        }
        int rise = Math.max(1, scaledCount(game, settingInt("blocks-per-rise", 1)));
        Location center = game.spawnLocation();
        if (center == null) {
            return;
        }
        int radius = (int) Math.ceil(game.getArena().getBorderSize() / 2.0);
        for (int i = 0; i < rise; i++) {
            currentY++;
            if (currentY >= world.getMaxHeight() - 1) {
                return;
            }
            int cx = center.getBlockX();
            int cz = center.getBlockZ();
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > radius * radius) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, currentY, z);
                    if (block.getType().isAir() || block.isLiquid()) {
                        block.setType(Material.LAVA, false);
                    }
                }
            }
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
        // lava remains until world reset
    }
}
