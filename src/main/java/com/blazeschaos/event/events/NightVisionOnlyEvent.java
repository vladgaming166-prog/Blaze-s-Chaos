package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class NightVisionOnlyEvent extends ChaosEvent {
    public NightVisionOnlyEvent() { super("night-vision-only", "Night Vision Only"); }
    @Override public void start(@NotNull GameInstance game) {
        if (game.getInstanceWorld() != null) game.getInstanceWorld().setTime(18000);
        for (Player player : game.getAlivePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, getDurationSeconds() * 20, 0, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, getDurationSeconds() * 20, 0, false, false, true));
        }
    }
    @Override public void tick(@NotNull GameInstance game, int tick) {}
    @Override public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }
}
