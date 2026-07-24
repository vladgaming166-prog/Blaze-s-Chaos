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
 * Parses text for Adventure display.
 * Supports MiniMessage, legacy {@code &} codes, {@code &#RRGGBB}/{@code #RRGGBB} hex,
 * and Birdflop-style animated RGB strings.
 */
public final class ColorUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand()
            .toBuilder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Pattern HEX_BARE = Pattern.compile("(?i)(?<![&<:])#([0-9A-F]{6})(?![>0-9A-Fa-f])");
    private static final Pattern HEX_AMP = Pattern.compile("(?i)&#([0-9A-F]{6})");

    private ColorUtil() {
    }

    public static @NotNull Component parse(@NotNull String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String text = input;

        boolean hasMiniTags = looksLikeMiniMessage(text);
        boolean hasLegacyHex = text.contains("&#") || text.contains("&x") || text.contains("§x");

        // Pure MiniMessage (gradients etc.)
        if (hasMiniTags && !hasLegacyHex) {
            return MINI.deserialize(text).decoration(TextDecoration.ITALIC, false);
        }

        // Birdflop RGB / legacy — including mixed with MiniMessage
        if (hasLegacyHex || text.indexOf('&') >= 0 || text.indexOf('§') >= 0 || text.indexOf('#') >= 0) {
            if (hasMiniTags) {
                text = normalizeLegacyHex(text);
                text = legacyHexToMini(text);
                return MINI.deserialize(text).decoration(TextDecoration.ITALIC, false);
            }
            text = normalizeLegacyHex(text);
            return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC, false);
        }

        return MINI.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    private static boolean looksLikeMiniMessage(@NotNull String text) {
        if (text.indexOf('<') < 0) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("<gradient") || lower.contains("<rainbow") || lower.contains("<bold")
                || lower.contains("<italic") || lower.contains("<underlined") || lower.contains("<strikethrough")
                || lower.contains("<obfuscated") || lower.contains("<reset") || lower.contains("<color")
                || lower.contains("<#") || lower.contains("<green") || lower.contains("<red")
                || lower.contains("<gold") || lower.contains("<aqua") || lower.contains("<yellow")
                || lower.contains("<white") || lower.contains("<gray") || lower.contains("<dark_")
                || lower.contains("<hover") || lower.contains("<click") || lower.contains("</");
    }

    private static @NotNull String legacyHexToMini(@NotNull String input) {
        Matcher amp = Pattern.compile("(?i)&#([0-9A-F]{6})").matcher(input);
        StringBuilder sb = new StringBuilder();
        while (amp.find()) {
            amp.appendReplacement(sb, Matcher.quoteReplacement("<#" + amp.group(1) + ">"));
        }
        amp.appendTail(sb);
        // Also convert &x&R&R&G&G&B&B produced by normalizeLegacyHex
        Matcher x = Pattern.compile("(?i)&x(?:&([0-9A-F])){6}").matcher(sb.toString());
        StringBuilder out = new StringBuilder();
        while (x.find()) {
            String raw = x.group(0);
            StringBuilder hex = new StringBuilder(6);
            for (int i = 0; i < raw.length(); i++) {
                if (raw.charAt(i) == '&' && i + 1 < raw.length() && raw.charAt(i + 1) != 'x' && raw.charAt(i + 1) != 'X') {
                    hex.append(raw.charAt(i + 1));
                }
            }
            if (hex.length() == 6) {
                x.appendReplacement(out, Matcher.quoteReplacement("<#" + hex + ">"));
            } else {
                x.appendReplacement(out, Matcher.quoteReplacement(raw));
            }
        }
        x.appendTail(out);
        return out.toString();
    }

    private static @NotNull String normalizeLegacyHex(@NotNull String input) {
        String text = input;
        Matcher amp = HEX_AMP.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (amp.find()) {
            amp.appendReplacement(sb, Matcher.quoteReplacement(toLegacyHex(amp.group(1))));
        }
        amp.appendTail(sb);
        text = sb.toString();

        Matcher bare = HEX_BARE.matcher(text);
        sb = new StringBuilder();
        while (bare.find()) {
            bare.appendReplacement(sb, Matcher.quoteReplacement(toLegacyHex(bare.group(1))));
        }
        bare.appendTail(sb);
        return sb.toString();
    }

    private static @NotNull String toLegacyHex(@NotNull String hex) {
        StringBuilder out = new StringBuilder("&x");
        for (char c : hex.toCharArray()) {
            out.append('&').append(c);
        }
        return out.toString();
    }

    public static @NotNull String strip(@NotNull String input) {
        String text = input;
        try {
            text = MINI.stripTags(text);
        } catch (Exception ignored) {
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
    }

    public static @NotNull MiniMessage mini() {
        return MINI;
    }
}
