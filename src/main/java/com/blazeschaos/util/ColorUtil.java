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
 * <p>
 * Restores the pre-break behaviour: any string containing MiniMessage {@code <tags>}
 * is parsed with MiniMessage. Legacy {@code &} codes are used only when no {@code <}
 * tags are present. Hex helpers apply only on the legacy path so gradients like
 * {@code <gradient:#FFFFFF:#45A000>} are never mangled into raw visible tags.
 */
public final class ColorUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand()
            .toBuilder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Pattern HEX_BARE = Pattern.compile("(?i)(?<![&<])#([0-9A-F]{6})");
    private static final Pattern HEX_AMP = Pattern.compile("(?i)&#([0-9A-F]{6})");

    private ColorUtil() {
    }

    public static @NotNull Component parse(@NotNull String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String text = input;

        // MiniMessage whenever angle-bracket tags exist (same rule as the working version).
        // Never rewrite # colors inside these strings.
        if (text.indexOf('<') >= 0) {
            return MINI.deserialize(text).decoration(TextDecoration.ITALIC, false);
        }

        // Legacy-only: & codes, optional # / &# hex
        if (text.indexOf('&') >= 0 || text.indexOf('#') >= 0 || text.indexOf('§') >= 0) {
            text = normalizeLegacyHex(text);
            return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC, false);
        }

        // Plain text — MiniMessage handles it safely (previous default path)
        return MINI.deserialize(text).decoration(TextDecoration.ITALIC, false);
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
