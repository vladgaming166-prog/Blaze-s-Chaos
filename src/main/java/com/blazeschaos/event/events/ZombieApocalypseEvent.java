package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public final class ZombieApocalypseEvent extends ChaosEvent {
    public ZombieApocalypseEvent() { super("zombie-apocalypse", "Zombie Apocalypse"); }
    @Override public void start(@NotNull GameInstance game) {
        int amount = Math.max(1, (int) Math.round(settingInt("mobs-per-player", 10) * mobScale(game)));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player player : game.getAlivePlayers()) {
            for (int i = 0; i < amount; i++) {
                game.trackEntity(player.getWorld().spawn(
                        player.getLocation().add(r.nextInt(-6, 7), 0, r.nextInt(-6, 7)), Zombie.class));
            }
        }
    }
    @Override public void tick(@NotNull GameInstance game, int tick) {}
    @Override public void end(@NotNull GameInstance game) { game.clearTrackedEntities(); }
}
