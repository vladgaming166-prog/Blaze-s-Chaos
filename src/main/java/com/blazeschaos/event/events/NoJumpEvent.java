package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class NoJumpEvent extends ChaosEvent {
    public NoJumpEvent() { super("no-jump", "No Jump"); }
    @Override public void start(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, getDurationSeconds() * 20, 128, false, false, true));
        }
    }
    @Override public void tick(@NotNull GameInstance game, int tick) {}
    @Override public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }
}
