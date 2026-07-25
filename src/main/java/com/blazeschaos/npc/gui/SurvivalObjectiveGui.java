package com.blazeschaos.npc.gui;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import com.blazeschaos.game.GameModeType;
import com.blazeschaos.game.SurvivalObjective;
import com.blazeschaos.util.ColorUtil;
import com.blazeschaos.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Solo Survival objective picker: Survive timer OR Chaos Shard.
 */
public final class SurvivalObjectiveGui implements Listener {

    private final BlazesChaosPlugin plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey arenaKey;

    public SurvivalObjectiveGui(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "survival_obj_action");
        this.arenaKey = new NamespacedKey(plugin, "survival_obj_arena");
    }

    public void open(@NotNull Player player, @Nullable Arena arena) {
        Holder holder = new Holder(arena == null ? "" : arena.getName());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                ColorUtil.parse("<light_purple><bold>Solo Survival</bold></light_purple>"));
        holder.bind(inventory);

        ItemStack pane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, pane);
        }

        int surviveSeconds = plugin.getConfig().getInt("solo-survival.survive-seconds", 1200);
        inventory.setItem(11, tag(new ItemBuilder(Material.CLOCK)
                .name("<green><bold>Survive</bold></green>")
                .lore(List.of(
                        "<gray>Survive for <white>" + surviveSeconds + "</white> seconds.</gray>",
                        "<gray>You win automatically when the timer ends.</gray>",
                        "",
                        "<yellow>Click to play</yellow>"
                )).glow(true).build(), "survive", arena));

        inventory.setItem(15, tag(new ItemBuilder(Material.AMETHYST_SHARD)
                .name("<light_purple><bold>Chaos Shard</bold></light_purple>")
                .lore(List.of(
                        "<gray>Obtain the Chaos Shard to win instantly.</gray>",
                        "<gray>Find it in loot chests, craft it, or both.</gray>",
                        "",
                        "<yellow>Click to play</yellow>"
                )).glow(true).build(), "shard", arena));

        inventory.setItem(22, tag(new ItemBuilder(Material.ARROW)
                .name("<red>Cancel</red>")
                .lore(List.of("<gray>Close</gray>")).build(), "cancel", null));

        player.openInventory(inventory);
    }

    private @NotNull ItemStack tag(@NotNull ItemStack item, @NotNull String action, @Nullable Arena arena) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            if (arena != null) {
                meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arena.getName());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(@NotNull InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || !current.hasItemMeta()) {
            return;
        }
        String action = current.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        if (action.equals("cancel")) {
            player.closeInventory();
            return;
        }
        String arenaName = current.getItemMeta().getPersistentDataContainer().get(arenaKey, PersistentDataType.STRING);
        Arena arena = arenaName == null || arenaName.isBlank()
                ? plugin.arenaManager().findJoinable()
                : plugin.arenaManager().get(arenaName);
        SurvivalObjective objective = action.equals("shard")
                ? SurvivalObjective.CHAOS_SHARD
                : SurvivalObjective.SURVIVE;
        player.closeInventory();
        plugin.gameManager().join(player, arena, GameModeType.SOLO_SURVIVAL, objective);
    }

    public static final class Holder implements InventoryHolder {
        private final String arenaName;
        private @Nullable Inventory inventory;

        public Holder(@NotNull String arenaName) {
            this.arenaName = arenaName;
        }

        public void bind(@NotNull Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory != null ? inventory : Bukkit.createInventory(this, 27);
        }
    }
}
