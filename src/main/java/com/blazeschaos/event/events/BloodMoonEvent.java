package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class BloodMoonEvent extends ChaosEvent {
    public BloodMoonEvent() { super("blood-moon", "Blood Moon"); }
    @Override public void start(@NotNull GameInstance game) {
        World world = game.getArena().getWorld();
        if (world != null) {
            world.setTime(18000);
            world.setStorm(true);
        }
        for (Player player : game.getAlivePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, getDurationSeconds() * 20, 0, false, false, true));
        }
    }
    @Override public void tick(@NotNull GameInstance game, int tick) {}
    @Override public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.removePotionEffect(PotionEffectType.WEAKNESS);
        }
    }
}
