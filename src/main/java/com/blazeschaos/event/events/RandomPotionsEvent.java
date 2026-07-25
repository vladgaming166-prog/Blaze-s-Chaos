package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomPotionsEvent extends ChaosEvent {
    private static final List<PotionEffectType> POOL = List.of(
            PotionEffectType.SPEED, PotionEffectType.SLOWNESS, PotionEffectType.JUMP_BOOST,
            PotionEffectType.NAUSEA, PotionEffectType.REGENERATION, PotionEffectType.POISON,
            PotionEffectType.STRENGTH, PotionEffectType.WEAKNESS, PotionEffectType.LEVITATION
    );
    private int tickCounter;
    public RandomPotionsEvent() { super("random-potions", "Random Potions"); }
    @Override public void start(@NotNull GameInstance game) { tickCounter = 0; apply(game); }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 60);
        tickCounter++;
        if (tickCounter % interval == 0) apply(game);
    }
    private void apply(@NotNull GameInstance game) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player player : game.getAlivePlayers()) {
            PotionEffectType type = POOL.get(r.nextInt(POOL.size()));
            player.addPotionEffect(new PotionEffect(type, 80, r.nextInt(0, 2), false, true, true));
        }
    }
    @Override public void end(@NotNull GameInstance game) {}
}
