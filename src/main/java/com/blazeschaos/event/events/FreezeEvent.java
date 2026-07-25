package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class FreezeEvent extends ChaosEvent {
    public FreezeEvent() { super("freeze", "Freeze"); }
    @Override public void start(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.setFreezeTicks(Math.max(player.getFreezeTicks(), getDurationSeconds() * 20));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getDurationSeconds() * 20, 2, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, getDurationSeconds() * 20, 1, false, false, true));
        }
    }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        if (tick % 20 != 0) return;
        for (Player player : game.getAlivePlayers()) {
            player.setFreezeTicks(Math.max(player.getFreezeTicks(), 40));
        }
    }
    @Override public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.setFreezeTicks(0);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        }
    }
}
