package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class FloodEvent extends ChaosEvent {

    private int currentY;
    private int tickCounter;

    public FloodEvent() {
        super("flood", "Flood");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
        int minY = Integer.MAX_VALUE;
        for (Player player : game.getAlivePlayers()) {
            minY = Math.min(minY, player.getLocation().getBlockY());
        }
        World world = game.getArena().getWorld();
        if (minY == Integer.MAX_VALUE && world != null) {
            minY = world.getMinHeight() + 5;
        }
        currentY = Math.max(world == null ? minY : world.getMinHeight() + 1, minY - 3);
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("rise-interval-ticks", 40);
        tickCounter++;
        if (tickCounter % interval != 0) {
            return;
        }
        World world = game.getArena().getWorld();
        Location center = game.getArena().getSpawn();
        if (world == null || center == null) {
            return;
        }
        int rise = settingInt("blocks-per-rise", 1);
        int radius = (int) Math.ceil(game.getArena().getBorderSize() / 2.0);
        for (int i = 0; i < rise; i++) {
            currentY++;
            int cx = center.getBlockX();
            int cz = center.getBlockZ();
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > radius * radius) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, currentY, z);
                    if (block.getType().isAir()) {
                        block.setType(Material.WATER, false);
                    }
                }
            }
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
