package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomTeleportEvent extends ChaosEvent {
    private int tickCounter;
    public RandomTeleportEvent() { super("random-teleport", "Random Teleport"); }
    @Override public void start(@NotNull GameInstance game) { tickCounter = 0; teleport(game); }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 80);
        tickCounter++;
        if (tickCounter % interval == 0) teleport(game);
    }
    private void teleport(@NotNull GameInstance game) {
        List<Player> alive = game.getAlivePlayers();
        if (alive.size() < 2) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player player : alive) {
            Player other = alive.get(r.nextInt(alive.size()));
            Location dest = other.getLocation().add(r.nextInt(-3, 4), 0, r.nextInt(-3, 4));
            player.teleport(dest);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        }
    }
    @Override public void end(@NotNull GameInstance game) {}
}
