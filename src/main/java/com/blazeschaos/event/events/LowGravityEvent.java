package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class LowGravityEvent extends ChaosEvent {

    public LowGravityEvent() {
        super("low-gravity", "Low Gravity");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        int jump = settingInt("jump-amplifier", 2);
        boolean slowFalling = settingBool("slow-falling", true);
        for (Player player : game.getAlivePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, getDurationSeconds() * 20, jump, false, false, true));
            if (slowFalling) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, getDurationSeconds() * 20, 0, false, false, true));
            }
        }
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
    }

    @Override
    public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
            player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        }
    }
}
