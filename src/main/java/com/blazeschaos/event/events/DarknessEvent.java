package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class DarknessEvent extends ChaosEvent {

    public DarknessEvent() {
        super("darkness", "Darkness");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        int amplifier = settingInt("blindness-amplifier", 0);
        for (Player player : game.getAlivePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, getDurationSeconds() * 20, amplifier, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, getDurationSeconds() * 20, amplifier, false, false, true));
        }
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
    }

    @Override
    public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
    }
}
