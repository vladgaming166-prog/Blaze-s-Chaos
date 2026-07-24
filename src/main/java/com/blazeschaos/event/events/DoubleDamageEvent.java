package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

public final class DoubleDamageEvent extends ChaosEvent {

    public DoubleDamageEvent() {
        super("double-damage", "Double Damage");
    }

    @Override
    public void start(@NotNull GameInstance game) {
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
    }

    @Override
    public void end(@NotNull GameInstance game) {
    }

    @Override
    public void onPlayerDamage(@NotNull GameInstance game, @NotNull Player victim, @NotNull EntityDamageEvent event) {
        double multiplier = settingDouble("multiplier", 2.0);
        event.setDamage(event.getDamage() * multiplier);
    }
}
