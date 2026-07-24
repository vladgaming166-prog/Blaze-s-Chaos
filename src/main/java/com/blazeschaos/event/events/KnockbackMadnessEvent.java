package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public final class KnockbackMadnessEvent extends ChaosEvent {

    public KnockbackMadnessEvent() {
        super("knockback-madness", "Knockback Madness");
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
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return;
        }
        if (!(byEntity.getDamager() instanceof Player attacker)) {
            return;
        }
        double multiplier = settingDouble("multiplier", 3.0);
        Vector direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize();
        direction.setY(0.35);
        victim.setVelocity(direction.multiply(multiplier));
    }
}
