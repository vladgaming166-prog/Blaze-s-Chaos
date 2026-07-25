package com.blazeschaos.event.events;

import com.blazeschaos.event.ChaosEvent;
import com.blazeschaos.game.GameInstance;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class OnlyBowsEvent extends ChaosEvent {

    private final Map<UUID, ItemStack[]> stored = new HashMap<>();

    public OnlyBowsEvent() {
        super("only-bows", "Only Bows");
    }

    @Override
    public void start(@NotNull GameInstance game) {
        stored.clear();
        for (Player player : game.getAlivePlayers()) {
            stored.put(player.getUniqueId(), player.getInventory().getContents().clone());
            player.getInventory().clear();
            player.getInventory().addItem(new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 64));
            player.updateInventory();
        }
    }

    @Override
    public void tick(@NotNull GameInstance game, int tick) {
    }

    @Override
    public void end(@NotNull GameInstance game) {
        for (Player player : game.getAlivePlayers()) {
            ItemStack[] contents = stored.remove(player.getUniqueId());
            if (contents != null) {
                player.getInventory().setContents(contents);
                player.updateInventory();
            }
        }
        stored.clear();
    }

    @Override
    public void onPlayerDamage(@NotNull GameInstance game, @NotNull Player victim, @NotNull EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
        }
    }
}
