package com.blazeschaos.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

public final class ColorUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private ColorUtil() {
    }

    public static @NotNull Component parse(@NotNull String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String text = input;
        if (text.indexOf('&') >= 0 && text.indexOf('<') < 0) {
            return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC, false);
        }
        return MINI.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    public static @NotNull String strip(@NotNull String input) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                MINI.stripTags(input)));
    }

    public static @NotNull MiniMessage mini() {
        return MINI;
    }
}
