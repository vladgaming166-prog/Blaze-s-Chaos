package com.blazeschaos.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses MiniMessage, legacy (& codes), § codes, and hex colors (#RRGGBB / &#RRGGBB / &x&R&R...).
 */
public final class ColorUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP =
            LegacyComponentSerializer.legacyAmpersand().toBuilder().hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.legacySection().toBuilder().hexColors().build();
    private static final Pattern HEX_HASH = Pattern.compile("(?i)#([0-9A-F]{6})");
    private static final Pattern HEX_AMP = Pattern.compile("(?i)&?#([0-9A-F]{6})");

    private ColorUtil() {
    }

    public static @NotNull Component parse(@NotNull String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String text = normalize(input);
        // Prefer MiniMessage when angle-bracket tags are present (gradients, etc.)
        if (text.indexOf('<') >= 0 && looksLikeMiniMessage(text)) {
            try {
                return MINI.deserialize(text).decoration(TextDecoration.ITALIC, false);
            } catch (Exception ignored) {
                // fall through to legacy
            }
        }
        if (text.indexOf('§') >= 0 && text.indexOf('&') < 0) {
            return LEGACY_SECTION.deserialize(text).decoration(TextDecoration.ITALIC, false);
        }
        return LEGACY_AMP.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Convert common hex formats into legacy {@code &x&R&R&G&G&B&B} so both MiniMessage-free
     * and legacy parsers handle them.
     */
    public static @NotNull String normalize(@NotNull String input) {
        String text = input;
        Matcher amp = HEX_AMP.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (amp.find()) {
            amp.appendReplacement(sb, Matcher.quoteReplacement(toLegacyHex(amp.group(1))));
        }
        amp.appendTail(sb);
        text = sb.toString();

        // Bare #RRGGBB only when not already part of MiniMessage <#...>
        Matcher hash = HEX_HASH.matcher(text);
        sb = new StringBuilder();
        while (hash.find()) {
            int start = hash.start();
            if (start > 0 && text.charAt(start - 1) == '<') {
                hash.appendReplacement(sb, Matcher.quoteReplacement(hash.group(0)));
                continue;
            }
            hash.appendReplacement(sb, Matcher.quoteReplacement(toLegacyHex(hash.group(1))));
        }
        hash.appendTail(sb);
        return sb.toString();
    }

    private static @NotNull String toLegacyHex(@NotNull String hex) {
        StringBuilder out = new StringBuilder("&x");
        for (char c : hex.toCharArray()) {
            out.append('&').append(c);
        }
        return out.toString();
    }

    private static boolean looksLikeMiniMessage(@NotNull String text) {
        return text.contains("</") || text.contains("<gradient") || text.contains("<bold")
                || text.contains("<red") || text.contains("<green") || text.contains("<aqua")
                || text.contains("<yellow") || text.contains("<gold") || text.contains("<gray")
                || text.contains("<white") || text.contains("<black") || text.contains("<dark_")
                || text.contains("<#") || text.contains("<rainbow") || text.contains("<italic")
                || text.contains("<underlined") || text.contains("<strikethrough") || text.contains("<obfuscated")
                || text.contains("<reset") || text.contains("<color");
    }

    public static @NotNull String strip(@NotNull String input) {
        String normalized = normalize(input);
        try {
            normalized = MINI.stripTags(normalized);
        } catch (Exception ignored) {
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', normalized));
    }

    public static @NotNull MiniMessage mini() {
        return MINI;
    }
}
