package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class BlockPartyEvent extends ChaosEvent {

    private DyeColor safeColor;
    private final List<Block> changed = new ArrayList<>();

    public BlockPartyEvent() {
        super("block-party", "Block Party");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        changed.clear();
        List<String> colors = settings() == null
                ? List.of("RED", "BLUE", "GREEN", "YELLOW")
                : settings().getStringList("safe-colors");
        if (colors.isEmpty()) {
            colors = List.of("RED", "BLUE", "GREEN", "YELLOW");
        }
        String pick = colors.get(ThreadLocalRandom.current().nextInt(colors.size()));
        try {
            safeColor = DyeColor.valueOf(pick.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            safeColor = DyeColor.RED;
        }
        World world = game.getInstanceWorld();
        Location center = game.spawnLocation();
        if (world == null || center == null) {
            return;
        }
        int radius = 12;
        for (Player player : game.getAlivePlayers()) {
            Location base = player.getLocation().subtract(0, 1, 0);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(base.getBlockX() + x, base.getBlockY(), base.getBlockZ() + z);
                    if (!block.getType().isSolid()) {
                        continue;
                    }
                    DyeColor color = randomColor();
                    Material wool = Material.matchMaterial(color.name() + "_WOOL");
                    if (wool == null) {
                        wool = Material.WHITE_WOOL;
                    }
                    block.setType(wool, false);
                    changed.add(block);
                }
            }
        }
        game.broadcastRaw("<light_purple>Block Party!</light_purple> <gray>Only</gray> <yellow>"
                + safeColor.name() + "</yellow> <gray>is safe!</gray>");
    }

    private DyeColor randomColor() {
        DyeColor[] values = DyeColor.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
        if (tick != getDurationSeconds() * 10) { // mid-event purge approx when half done via seconds*20/2 handled externally
        }
        if (tick > 0 && tick % 40 == 0) {
            Material safe = Material.matchMaterial(safeColor.name() + "_WOOL");
            if (safe == null) {
                safe = Material.RED_WOOL;
            }
            for (Block block : changed) {
                if (block.getType() != safe && block.getType().name().endsWith("_WOOL")) {
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    @Override
    public void end(@NotNull GameInstance game) {
        changed.clear();
    }
}
