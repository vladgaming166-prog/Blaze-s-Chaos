package com.blazeschaos.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class ItemBuilder {

    private final ItemStack item;

    public ItemBuilder(@NotNull Material material) {
        this.item = new ItemStack(material);
    }

    public ItemBuilder(@NotNull ItemStack item) {
        this.item = item.clone();
    }

    public @NotNull ItemBuilder name(@NotNull Component name) {
        return meta(meta -> meta.displayName(name));
    }

    public @NotNull ItemBuilder name(@NotNull String miniMessage) {
        return name(ColorUtil.parse(miniMessage));
    }

    public @NotNull ItemBuilder lore(@NotNull Component... lines) {
        return meta(meta -> meta.lore(Arrays.asList(lines)));
    }

    public @NotNull ItemBuilder lore(@NotNull List<String> miniLines) {
        List<Component> lore = new ArrayList<>();
        for (String line : miniLines) {
            lore.add(ColorUtil.parse(line));
        }
        return meta(meta -> meta.lore(lore));
    }

    public @NotNull ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    public @NotNull ItemBuilder glow(boolean glow) {
        if (!glow) {
            return this;
        }
        return meta(meta -> {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
    }

    public @NotNull ItemBuilder flags(@NotNull ItemFlag... flags) {
        return meta(meta -> meta.addItemFlags(flags));
    }

    public @NotNull ItemBuilder meta(@NotNull Consumer<ItemMeta> consumer) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            consumer.accept(meta);
            item.setItemMeta(meta);
        }
        return this;
    }

    public @NotNull ItemStack build() {
        return item.clone();
    }
}
