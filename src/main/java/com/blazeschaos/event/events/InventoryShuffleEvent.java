package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class InventoryShuffleEvent extends ChaosEvent {
    private int tickCounter;
    public InventoryShuffleEvent() { super("inventory-shuffle", "Inventory Shuffle"); }
    @Override public void start(@NotNull GameInstance game) { tickCounter = 0; shuffleAll(game); }
    @Override public void tick(@NotNull GameInstance game, int tick) {
        int interval = settingInt("interval-ticks", 100);
        tickCounter++;
        if (tickCounter % interval == 0) shuffleAll(game);
    }
    private void shuffleAll(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            ItemStack[] contents = player.getInventory().getStorageContents();
            List<ItemStack> list = Arrays.asList(contents);
            Collections.shuffle(list);
            player.getInventory().setStorageContents(list.toArray(ItemStack[]::new));
            player.updateInventory();
        }
    }
    @Override public void end(@NotNull GameInstance game) {}
}
