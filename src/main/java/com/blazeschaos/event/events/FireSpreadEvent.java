package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class FireSpreadEvent extends ChaosEvent {

    private int tickCounter;

    public FireSpreadEvent() {
        super("fire-spread", "Fire Spread");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        tickCounter = 0;
        if (game.getArena().getWorld() != null) {
            game.getArena().getWorld().setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, true);
        }
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 30);
        tickCounter++;
        if (tickCounter % interval != 0) {
            return;
        }
        List<Player> alive = game.getAlivePlayers();
        if (alive.isEmpty()) {
            return;
        }
        int fires = settingInt("fires-per-wave", 5);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < fires; i++) {
            Player target = alive.get(random.nextInt(alive.size()));
            Block block = target.getLocation().add(random.nextInt(-5, 6), 0, random.nextInt(-5, 6)).getBlock();
            if (block.getType().isAir()) {
                Block below = block.getRelative(0, -1, 0);
                if (below.getType().isSolid()) {
                    block.setType(Material.FIRE, false);
                }
            }
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }
}
