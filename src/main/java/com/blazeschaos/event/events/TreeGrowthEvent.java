package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class TreeGrowthEvent extends ChaosEvent {

    private int tickCounter;
    private static final TreeType[] TYPES = {
            TreeType.TREE, TreeType.BIRCH, TreeType.REDWOOD, TreeType.SMALL_JUNGLE, TreeType.ACACIA
    };

    public TreeGrowthEvent() {
        super("tree-growth", "Tree Growth");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
        grow(game);
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 40);
        tickCounter++;
        if (tickCounter % interval == 0) {
            grow(game);
        }
    }

    private void grow(@NotNull GameInstance game) {
        List<Player> alive = game.getAlivePlayers();
        if (alive.isEmpty()) {
            return;
        }
        int trees = settingInt("trees-per-wave", 4);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < trees; i++) {
            Player target = alive.get(random.nextInt(alive.size()));
            Block ground = target.getLocation().add(random.nextInt(-8, 9), 0, random.nextInt(-8, 9)).getBlock();
            while (ground.getY() > ground.getWorld().getMinHeight() && ground.getType().isAir()) {
                ground = ground.getRelative(0, -1, 0);
            }
            Block plant = ground.getRelative(0, 1, 0);
            if (!plant.getType().isAir()) {
                continue;
            }
            TreeType type = TYPES[random.nextInt(TYPES.length)];
            plant.getWorld().generateTree(plant.getLocation(), type);
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
