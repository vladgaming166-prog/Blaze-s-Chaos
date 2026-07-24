package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ExplodingChickensEvent extends ChaosEvent {
    private final List<Chicken> chickens = new ArrayList<>();
    private int tickCounter;
    public ExplodingChickensEvent() { super("exploding-chickens", "Exploding Chickens"); }
    @Override public void start(@NotNull GameInstance game) {
        chickens.clear(); tickCounter = 0;
        int amount = settingInt("per-player", 6);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player player : game.getAlivePlayers()) {
            for (int i = 0; i < amount; i++) {
                Chicken chicken = player.getWorld().spawn(player.getLocation().add(r.nextInt(-3,4),0,r.nextInt(-3,4)), Chicken.class);
                chickens.add(chicken);
                game.trackEntity(chicken);
            }
        }
    }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("explode-interval-ticks", 60);
        tickCounter++;
        if (tickCounter % interval != 0 || chickens.isEmpty()) return;
        float power = (float) settingDouble("power", 1.5);
        Chicken chicken = chickens.remove(ThreadLocalRandom.current().nextInt(chickens.size()));
        if (chicken.isValid()) {
            chicken.getWorld().createExplosion(chicken.getLocation(), power, false, true);
            chicken.remove();
        }
    }
    @Override public void end(@NotNull GameInstance game) { chickens.clear(); game.clearTrackedEntities(); }
}
