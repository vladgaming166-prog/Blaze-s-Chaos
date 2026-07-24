package com.blazeschaos.shop;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.util.ColorUtil;
import com.blazeschaos.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShopManager implements Listener {

    private final BlazesChaosPlugin plugin;
    private final NamespacedKey shopKey;

    public ShopManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        this.shopKey = new NamespacedKey(plugin, "shop_item");
    }

    public void open(@NotNull Player player) {
        Inventory inventory = Bukkit.createInventory(new ShopHolder(),
                plugin.configs().shop().getInt("size", 27),
                ColorUtil.parse(plugin.configs().shop().getString("title", "<gold>Shop</gold>")));
        ConfigurationSection items = plugin.configs().shop().getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection section = items.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                Material material = Material.matchMaterial(section.getString("material", "CHEST"));
                if (material == null) {
                    material = Material.CHEST;
                }
                int cost = section.getInt("cost", 100);
                boolean owned = plugin.database().ownsCosmetic(player.getUniqueId(), player.getName(), id);
                List<String> lore = new ArrayList<>(section.getStringList("lore"));
                lore.add("");
                lore.add(owned
                        ? "<green>Owned — click to select</green>"
                        : "<yellow>Cost: <white>" + cost + "</white> coins</yellow>");
                ItemStack icon = new ItemBuilder(material)
                        .name(section.getString("name", id))
                        .lore(lore)
                        .glow(owned)
                        .build();
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, id);
                    icon.setItemMeta(meta);
                }
                inventory.setItem(section.getInt("slot", 0), icon);
            }
        }
        ItemStack balance = new ItemBuilder(Material.GOLD_INGOT)
                .name("<gold>Your Coins: <white>" + plugin.coinsManager().balance(player) + "</white></gold>")
                .build();
        inventory.setItem(plugin.configs().shop().getInt("balance-slot", 22), balance);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(@NotNull InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) {
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
        String id = current.getItemMeta().getPersistentDataContainer().get(shopKey, PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        ConfigurationSection section = plugin.configs().shop().getConfigurationSection("items." + id);
        if (section == null) {
            return;
        }
        purchaseOrSelect(player, id, section);
        open(player);
    }

    private void purchaseOrSelect(@NotNull Player player, @NotNull String id, @NotNull ConfigurationSection section) {
        String category = section.getString("category", "cosmetic").toLowerCase(Locale.ROOT);
        if (plugin.database().ownsCosmetic(player.getUniqueId(), player.getName(), id)) {
            plugin.database().selectCosmetic(player.getUniqueId(), player.getName(), category, id);
            plugin.lang().send(player, "shop.selected", Map.of("item", ColorUtil.strip(section.getString("name", id))));
            return;
        }
        int cost = section.getInt("cost", 100);
        if (!plugin.coinsManager().spend(player, cost)) {
            plugin.lang().send(player, "shop.not-enough", Map.of(
                    "cost", String.valueOf(cost),
                    "balance", String.valueOf(plugin.coinsManager().balance(player))
            ));
            return;
        }
        plugin.database().unlockCosmetic(player.getUniqueId(), player.getName(), id);
        plugin.database().selectCosmetic(player.getUniqueId(), player.getName(), category, id);
        plugin.lang().send(player, "shop.purchased", Map.of(
                "item", ColorUtil.strip(section.getString("name", id)),
                "cost", String.valueOf(cost)
        ));
    }

    public record ShopHolder() implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return Bukkit.createInventory(this, 9);
        }
    }
}
