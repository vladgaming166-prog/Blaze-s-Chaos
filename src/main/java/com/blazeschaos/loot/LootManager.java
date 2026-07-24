package com.blazeschaos.loot;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class LootManager {

    private final BlazesChaosPlugin plugin;
    private boolean enabled;

    public LootManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        enabled = plugin.configs().loot().getBoolean("enabled", false);
        plugin.lootRarityManager().reload();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.configs().loot().set("enabled", enabled);
        plugin.configs().save(plugin.configs().loot(), "loot.yml");
    }

    public void fillArenaChests(@NotNull Arena arena) {
        if (!enabled) {
            return;
        }
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        int filled = 0;
        int radius = (int) Math.ceil(arena.getBorderSize() / 2.0) + 16;
        var center = arena.getCenter();
        if (center == null) {
            return;
        }
        int minChunkX = (center.getBlockX() - radius) >> 4;
        int maxChunkX = (center.getBlockX() + radius) >> 4;
        int minChunkZ = (center.getBlockZ() - radius) >> 4;
        int maxChunkZ = (center.getBlockZ() + radius) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (BlockState state : world.getChunkAt(cx, cz).getTileEntities()) {
                    if (state instanceof Chest chest) {
                        fillChest(chest.getBlockInventory());
                        filled++;
                    }
                }
            }
        }
        plugin.getLogger().info("Filled " + filled + " chests in arena " + arena.getName()
                + " (rarity=" + plugin.lootRarityManager().get().name().toLowerCase(Locale.ROOT) + ")");
    }

    public void fillChest(@NotNull Inventory inventory) {
        inventory.clear();
        int min = plugin.lootRarityManager().itemsMin();
        int max = Math.max(min, plugin.lootRarityManager().itemsMax());
        int count = ThreadLocalRandom.current().nextInt(min, max + 1);
        List<ItemStack> pool = buildPool();
        if (pool.isEmpty()) {
            return;
        }
        for (int i = 0; i < count; i++) {
            ItemStack pick = pool.get(ThreadLocalRandom.current().nextInt(pool.size())).clone();
            int slot = ThreadLocalRandom.current().nextInt(inventory.getSize());
            inventory.setItem(slot, pick);
        }
    }

    private @NotNull List<ItemStack> buildPool() {
        FileConfiguration loot = plugin.configs().loot();
        String path = plugin.lootRarityManager().poolPath();
        ConfigurationSection section = loot.getConfigurationSection(path);
        if (section == null) {
            section = loot.getConfigurationSection("pool");
        }
        List<ItemStack> pool = new ArrayList<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                Material material = Material.matchMaterial(entry.getString("material", "STONE"));
                if (material == null) {
                    continue;
                }
                int weight = Math.max(1, entry.getInt("weight", 1));
                int amountMin = entry.getInt("amount-min", 1);
                int amountMax = Math.max(amountMin, entry.getInt("amount-max", amountMin));
                for (int i = 0; i < weight; i++) {
                    int amount = ThreadLocalRandom.current().nextInt(amountMin, amountMax + 1);
                    ItemStack stack = new ItemStack(material, amount);
                    applyEnchants(stack, entry.getConfigurationSection("enchants"));
                    pool.add(stack);
                }
            }
        }
        return pool.isEmpty() ? defaultPool() : pool;
    }

    private void applyEnchants(@NotNull ItemStack stack, @Nullable ConfigurationSection enchants) {
        if (enchants == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        for (String key : enchants.getKeys(false)) {
            Enchantment enchantment = Enchantment.getByName(key.toUpperCase(Locale.ROOT));
            if (enchantment == null) {
                // Try namespaced key style (e.g. minecraft:sharpness via Registry)
                try {
                    var namespaced = org.bukkit.NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT));
                    enchantment = org.bukkit.Registry.ENCHANTMENT.get(namespaced);
                } catch (Throwable ignored) {
                    enchantment = null;
                }
            }
            if (enchantment == null) {
                continue;
            }
            int level = Math.max(1, enchants.getInt(key, 1));
            meta.addEnchant(enchantment, level, true);
        }
        stack.setItemMeta(meta);
    }

    private @NotNull List<ItemStack> defaultPool() {
        return List.of(
                new ItemStack(Material.STONE_SWORD),
                new ItemStack(Material.IRON_SWORD),
                new ItemStack(Material.BOW),
                new ItemStack(Material.ARROW, 16),
                new ItemStack(Material.BREAD, 8),
                new ItemStack(Material.COOKED_BEEF, 4),
                new ItemStack(Material.GOLDEN_APPLE),
                new ItemStack(Material.WATER_BUCKET),
                new ItemStack(Material.COBBLESTONE, 32),
                new ItemStack(Material.OAK_PLANKS, 32),
                new ItemStack(Material.IRON_PICKAXE),
                new ItemStack(Material.SHIELD),
                new ItemStack(Material.TORCH, 16)
        );
    }
}
