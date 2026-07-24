package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public final class EarthquakeEvent extends ChaosEvent {
    private int tickCounter;
    public EarthquakeEvent() { super("earthquake", "Earthquake"); }
    @Override public void start(@NotNull GameInstance game) { tickCounter = 0; }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        int interval = Math.max(5, settingInt("interval-ticks", 8));
        tickCounter++;
        if (tickCounter % interval != 0) return;
        double strength = settingDouble("strength", 0.45);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player player : game.getAlivePlayers()) {
            player.setVelocity(player.getVelocity().add(new Vector(
                    r.nextDouble(-strength, strength), 0.05, r.nextDouble(-strength, strength))));
            player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.15f, 0.5f);
        }
    }
    @Override public void end(@NotNull GameInstance game) {}
}
