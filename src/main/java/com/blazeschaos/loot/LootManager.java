package com.blazeschaos.loot;

import com.blazeschaos.BlazesChaosPlugin;
import com.blazeschaos.arena.Arena;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
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
                    world.loadChunk(cx, cz);
                }
                for (BlockState state : world.getChunkAt(cx, cz).getTileEntities()) {
                    if (state instanceof Chest chest) {
                        fillChest(chest.getBlockInventory());
                        filled++;
                    }
                }
            }
        }
        plugin.getLogger().info("Filled " + filled + " chests in arena " + arena.getName());
    }

    public void fillChest(@NotNull Inventory inventory) {
        inventory.clear();
        FileConfiguration loot = plugin.configs().loot();
        int min = loot.getInt("items-per-chest.min", 4);
        int max = loot.getInt("items-per-chest.max", 8);
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
        List<ItemStack> pool = new ArrayList<>();
        ConfigurationSection section = plugin.configs().loot().getConfigurationSection("pool");
        if (section == null) {
            return defaultPool();
        }
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
                pool.add(new ItemStack(material, amount));
            }
        }
        return pool.isEmpty() ? defaultPool() : pool;
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
