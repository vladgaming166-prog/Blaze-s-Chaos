package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class FastMiningEvent extends ChaosEvent {
    public FastMiningEvent() { super("fast-mining", "Fast Mining"); }
    @Override public void start(@NotNull GameInstance game) {
        int amp = settingInt("amplifier", 2);
        for (Player player : game.getAlivePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, getDurationSeconds() * 20, amp, false, false, true));
        }
    }
    @Override public void tick(@NotNull GameInstance game, int tick) {}
    @Override public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) player.removePotionEffect(PotionEffectType.HASTE);
    }
}
