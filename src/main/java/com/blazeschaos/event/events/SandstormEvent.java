package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class SandstormEvent extends ChaosEvent {
    public SandstormEvent() { super("sandstorm", "Sandstorm"); }
    @Override public void start(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, getDurationSeconds() * 20, 0, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getDurationSeconds() * 20, 0, false, false, true));
        }
    }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        if (tick % 10 != 0) return;
        for (Player player : game.getAlivePlayers()) {
            player.getWorld().spawnParticle(Particle.FALLING_DUST, player.getLocation().add(0, 1, 0),
                    20, 1.5, 1, 1.5, org.bukkit.Material.SAND.createBlockData());
        }
    }
    @Override public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }
}
