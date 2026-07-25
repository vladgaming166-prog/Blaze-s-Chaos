package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class IceAgeEvent extends ChaosEvent {

    public IceAgeEvent() {
        super("ice-age", "Ice Age");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            if (settingBool("apply-slowness", true)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getDurationSeconds() * 20, 0, false, false, true));
            }
            Block below = player.getLocation().subtract(0, 1, 0).getBlock();
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    Block block = below.getRelative(x, 0, z);
                    if (block.getType().isSolid()) {
                        block.setType(Material.PACKED_ICE, false);
                    }
                    Block surface = below.getRelative(x, 1, z);
                    if (surface.getType().isAir()) {
                        // leave air
                    }
                }
            }
        }
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        if (tick % 20 != 0) {
            return;
        }
        for (Player player : game.getAlivePlayers()) {
            Block below = player.getLocation().subtract(0, 1, 0).getBlock();
            if (below.getType().isSolid() && below.getType() != Material.PACKED_ICE) {
                below.setType(Material.ICE, false);
            }
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }
}
