package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public final class HighGravityEvent extends ChaosEvent {
    public HighGravityEvent() { super("high-gravity", "High Gravity"); }
    @Override public void start(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getDurationSeconds() * 20, 1, false, false, true));
        }
    }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        if (tick % 5 != 0) return;
        double pull = settingDouble("pull", -0.15);
        for (Player player : game.getAlivePlayers()) {
            player.setVelocity(player.getVelocity().add(new Vector(0, pull, 0)));
        }
    }
    @Override public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) player.removePotionEffect(PotionEffectType.SLOWNESS);
    }
}
