package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class SolarFlareEvent extends ChaosEvent {
    public SolarFlareEvent() { super("solar-flare", "Solar Flare"); }
    @Override public void start(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, getDurationSeconds() * 20, 0, false, false, true));
            player.setFireTicks(60);
        }
        if (game.getInstanceWorld() != null) {
            game.getInstanceWorld().setTime(6000);
        }
    }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        if (tick % 40 != 0) return;
        for (Player player : game.getAlivePlayers()) {
            if (player.getLocation().getBlock().getLightFromSky() > 10) {
                player.damage(settingDouble("damage", 1.5));
            }
        }
    }
    @Override public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.removePotionEffect(PotionEffectType.GLOWING);
        }
    }
}
