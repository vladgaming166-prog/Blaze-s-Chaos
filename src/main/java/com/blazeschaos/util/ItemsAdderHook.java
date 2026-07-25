package com.blazeschaos.util;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Soft ItemsAdder bridge for custom Chaos Shard textures when the plugin is present.
 */
public final class ItemsAdderHook {

    private ItemsAdderHook() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    /**
     * @return ItemsAdder custom stack, or null if unavailable / missing id
     */
    public static @Nullable ItemStack customStack(@NotNull String namespacedId) {
        if (!available() || namespacedId.isBlank()) {
            return null;
        }
        try {
            Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = customStack.getMethod("getInstance", String.class).invoke(null, namespacedId);
            if (stack == null) {
                return null;
            }
            Object item = customStack.getMethod("getItemStack").invoke(stack);
            if (item instanceof ItemStack itemStack) {
                return itemStack.clone();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static @NotNull ItemStack chaosShardOrFallback(@NotNull BlazesChaosPlugin plugin,
                                                          @NotNull Material fallback) {
        String id = plugin.getConfig().getString("solo-survival.shard-item.itemsadder-id", "");
        ItemStack custom = customStack(id == null ? "" : id);
        if (custom != null) {
            return custom;
        }
        return new ItemStack(fallback);
    }
}
