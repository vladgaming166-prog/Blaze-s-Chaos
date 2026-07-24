package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public final class ChickenMadnessEvent extends ChaosEvent {

    public ChickenMadnessEvent() {
        super("chicken-madness", "Chicken Madness");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        int perPlayer = settingInt("chickens-per-player", 20);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player player : game.getAlivePlayers()) {
            for (int i = 0; i < perPlayer; i++) {
                Chicken chicken = player.getWorld().spawn(
                        player.getLocation().add(random.nextInt(-4, 5), 0, random.nextInt(-4, 5)),
                        Chicken.class);
                chicken.setAdult();
                game.trackEntity(chicken);
            }
        }
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
    }

    @Override
    public void end(@NotNull GameInstance game) {
        game.clearTrackedEntities();
    }
}
