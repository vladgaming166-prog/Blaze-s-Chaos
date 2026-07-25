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

public final class FallingBlocksEvent extends ChaosEvent {

    private int tickCounter;
    private static final Material[] BLOCKS = {
            Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.ANVIL
    };

    public FallingBlocksEvent() {
        super("falling-blocks", "Falling Blocks");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 20);
        tickCounter++;
        if (tickCounter % interval != 0) {
            return;
        }
        List<Player> alive = game.getAlivePlayers();
        if (alive.isEmpty()) {
            return;
        }
        int amount = settingInt("blocks-per-wave", 8);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < amount; i++) {
            Player target = alive.get(random.nextInt(alive.size()));
            Location loc = target.getLocation().add(
                    random.nextInt(-6, 7),
                    random.nextInt(6, 12),
                    random.nextInt(-6, 7));
            Material mat = BLOCKS[random.nextInt(BLOCKS.length)];
            FallingBlock falling = loc.getWorld().spawnFallingBlock(loc, mat.createBlockData());
            falling.setDropItem(false);
            falling.setHurtEntities(true);
            game.trackEntity(falling);
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
