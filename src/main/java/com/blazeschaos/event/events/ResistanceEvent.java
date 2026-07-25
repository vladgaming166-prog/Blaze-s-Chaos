package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class ResistanceEvent extends ChaosEvent {

    public ResistanceEvent() {
        super("resistance", "Resistance");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        int amplifier = settingInt("amplifier", 1);
        for (Player player : game.getAlivePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, getDurationSeconds() * 20, amplifier, false, false, true));
        }
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
    }

    @Override
    public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.removePotionEffect(PotionEffectType.RESISTANCE);
        }
    }
}
