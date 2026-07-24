package com.blazeschaos.scoreboard;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single efficient animation engine (TAB-style).
 * <p>
 * Each placeholder resolves to EXACTLY one named animation:
 * <ul>
 *   <li>{@code %blazechaosanimation_title%} → animation "title" only</li>
 *   <li>{@code %blazechaosanimation_server%} → animation "server" only</li>
 *   <li>{@code %animation:coins%} / {@code %blazechaos_animation_coins%}</li>
 * </ul>
 * Supports frame lists and Birdflop-style RGB / shifting gradient generators.
 */
public final class AnimationManager {

    private static final Pattern[] PATTERNS = {
            Pattern.compile("%blazechaosanimation_([a-zA-Z0-9_-]+)%", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%blazechaos_animation_([a-zA-Z0-9_-]+)%", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%animation:([a-zA-Z0-9_-]+)%", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\{animation:([a-zA-Z0-9_-]+)}", Pattern.CASE_INSENSITIVE)
    };

    private static final int[] DEFAULT_RGB = {
            0xFF0000, 0xFF4000, 0xFF8000, 0xFFBF00, 0xFFFF00, 0xBFFF00, 0x80FF00, 0x40FF00,
            0x00FF00, 0x00FF40, 0x00FF80, 0x00FFBF, 0x00FFFF, 0x00BFFF, 0x0080FF, 0x0040FF,
            0x0000FF, 0x4000FF, 0x8000FF, 0xBF00FF, 0xFF00FF, 0xFF00BF, 0xFF0080, 0xFF0040
    };

    private final BlazesChaosPlugin plugin;
    private final Map<String, Animation> animations = new LinkedHashMap<>();
    private long ticks;
    private @Nullable BukkitTask task;

    public AnimationManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reload() {
        animations.clear();
        loadFrom(plugin.configs().scoreboardAnimations());
        loadFrom(plugin.configs().globalAnimations());
        plugin.getLogger().info("Loaded " + animations.size() + " animations.");
    }

    private void loadFrom(@NotNull FileConfiguration config) {
        for (String key : config.getKeys(false)) {
            if (key.equalsIgnoreCase("config-version")) {
                continue;
            }
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String id = key.toLowerCase(Locale.ROOT);
            int interval = Math.max(1, section.getInt("interval",
                    section.getInt("change-interval", 20)));

            String type = section.getString("type", "").toLowerCase(Locale.ROOT);
            boolean rgb = section.getBoolean("rgb", false)
                    || type.equals("rgb")
                    || type.equals("rainbow")
                    || type.equals("birdflop");
            boolean gradient = section.getBoolean("gradient", false)
                    || type.equals("gradient")
                    || type.equals("shifting-gradient");

            List<Integer> palette = parsePalette(section.getStringList("colors"));
            if (palette.isEmpty()) {
                palette = parsePalette(section.getStringList("palette"));
            }

            List<String> frames = section.getStringList("frames");
            if (frames.isEmpty()) {
                frames = section.getStringList("texts");
            }

            String text = section.getString("text", section.getString("input", ""));
            if ((rgb || gradient) && (text == null || text.isBlank()) && !frames.isEmpty()) {
                // Use first frame as the RGB/gradient source text (strip simple color codes for wave)
                text = frames.get(0);
            }

            Animation animation;
            if (rgb && text != null && !text.isBlank()) {
                animation = Animation.rgb(interval, stripForRgb(text), palette);
            } else if (gradient && text != null && !text.isBlank()) {
                animation = Animation.gradient(interval, stripForRgb(text), palette);
            } else if (!frames.isEmpty()) {
                // Auto-detect: single static MiniMessage/legacy gradient line → animate it
                if (frames.size() == 1 && looksLikeStaticGradient(frames.get(0))) {
                    animation = Animation.gradient(interval, stripForRgb(frames.get(0)), palette);
                } else if (frames.size() == 1 && section.getBoolean("animate-rgb", false)) {
                    animation = Animation.rgb(interval, stripForRgb(frames.get(0)), palette);
                } else {
                    animation = Animation.frames(interval, frames);
                }
            } else {
                continue;
            }
            animations.put(id, animation);
        }
    }

    private static boolean looksLikeStaticGradient(@NotNull String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("<gradient:") || lower.contains("<rainbow");
    }

    private static @NotNull String stripForRgb(@NotNull String input) {
        String text = input;
        // Remove MiniMessage tags for character wave source
        text = text.replaceAll("(?i)</?gradient[^>]*>", "");
        text = text.replaceAll("(?i)</?rainbow[^>]*>", "");
        text = text.replaceAll("(?i)</?#[0-9A-Fa-f]{6}>", "");
        text = text.replaceAll("(?i)</?(bold|italic|underlined|strikethrough|obfuscated|reset|b|i|u)>", "");
        // Keep &l style as formatting hints? Strip color codes but keep bold
        text = text.replaceAll("(?i)&[0-9a-fk-or]", "");
        text = text.replaceAll("(?i)&#[0-9a-f]{6}", "");
        text = text.replaceAll("(?i)#([0-9a-f]{6})", "");
        return text;
    }

    private static @NotNull List<Integer> parsePalette(@NotNull List<String> raw) {
        List<Integer> out = new ArrayList<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String hex = entry.trim().replace("#", "").replace("&", "");
            if (hex.length() != 6) {
                continue;
            }
            try {
                out.add(Integer.parseInt(hex, 16));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public void tick() {
        ticks++;
    }

    public long ticks() {
        return ticks;
    }

    /**
     * Replace each animation placeholder with ONLY that animation's current frame.
     * Never mixes frames across different animation names.
     */
    public @NotNull String resolve(@NotNull String input) {
        if (input.indexOf('%') < 0 && input.indexOf('{') < 0) {
            return input;
        }
        String text = input;
        for (Pattern pattern : PATTERNS) {
            text = replaceNamed(pattern, text);
        }
        return text;
    }

    /** Current frame for a single named animation (independent). */
    public @NotNull String frame(@NotNull String name) {
        Animation animation = animations.get(name.toLowerCase(Locale.ROOT));
        return animation == null ? "" : animation.current(ticks);
    }

    private @NotNull String replaceNamed(@NotNull Pattern pattern, @NotNull String input) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return input;
        }
        matcher.reset();
        StringBuilder sb = new StringBuilder(input.length() + 32);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            Animation animation = animations.get(name);
            // Unknown name → leave placeholder intact (do not substitute another animation)
            String replacement = animation == null ? matcher.group(0) : animation.current(ticks);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public @Nullable Animation get(@NotNull String name) {
        return animations.get(name.toLowerCase(Locale.ROOT));
    }

    public @NotNull Map<String, Animation> all() {
        return Collections.unmodifiableMap(animations);
    }

    public static final class Animation {
        private final int intervalTicks;
        private final Kind kind;
        private final List<String> frames;
        private final String sourceText;
        private final int[] palette;

        private enum Kind {FRAMES, RGB, GRADIENT}

        private Animation(int intervalTicks, @NotNull Kind kind, @NotNull List<String> frames,
                          @Nullable String sourceText, @NotNull int[] palette) {
            this.intervalTicks = Math.max(1, intervalTicks);
            this.kind = kind;
            this.frames = List.copyOf(frames);
            this.sourceText = sourceText == null ? "" : sourceText;
            this.palette = palette.length == 0 ? DEFAULT_RGB.clone() : palette.clone();
        }

        public static @NotNull Animation frames(int interval, @NotNull List<String> frames) {
            return new Animation(interval, Kind.FRAMES, frames, null, DEFAULT_RGB);
        }

        public static @NotNull Animation rgb(int interval, @NotNull String text, @NotNull List<Integer> palette) {
            int[] colors = toArray(palette);
            return new Animation(interval, Kind.RGB, List.of(), text, colors);
        }

        public static @NotNull Animation gradient(int interval, @NotNull String text, @NotNull List<Integer> palette) {
            int[] colors = toArray(palette);
            return new Animation(interval, Kind.GRADIENT, List.of(), text, colors);
        }

        private static int[] toArray(@NotNull List<Integer> palette) {
            if (palette.isEmpty()) {
                return DEFAULT_RGB.clone();
            }
            int[] arr = new int[palette.size()];
            for (int i = 0; i < palette.size(); i++) {
                arr[i] = palette.get(i);
            }
            return arr;
        }

        public @NotNull String current(long globalTicks) {
            int step = (int) ((globalTicks / (long) intervalTicks));
            return switch (kind) {
                case FRAMES -> {
                    if (frames.isEmpty()) {
                        yield "";
                    }
                    int index = Math.floorMod(step, frames.size());
                    yield frames.get(index);
                }
                case RGB -> renderRgb(step);
                case GRADIENT -> renderGradient(step);
            };
        }

        private @NotNull String renderRgb(int step) {
            if (sourceText.isEmpty()) {
                return "";
            }
            StringBuilder out = new StringBuilder(sourceText.length() * 10);
            int len = sourceText.length();
            for (int i = 0; i < len; i++) {
                char ch = sourceText.charAt(i);
                if (ch == ' ') {
                    out.append(' ');
                    continue;
                }
                int color = palette[Math.floorMod(step + i, palette.length)];
                out.append(toAmpHex(color)).append(ch);
            }
            return out.toString();
        }

        private @NotNull String renderGradient(int step) {
            if (sourceText.isEmpty()) {
                return "";
            }
            int c1 = palette[Math.floorMod(step, palette.length)];
            int c2 = palette[Math.floorMod(step + Math.max(1, palette.length / 3), palette.length)];
            int c3 = palette[Math.floorMod(step + Math.max(2, palette.length / 2), palette.length)];
            // Moving 3-stop MiniMessage gradient
            return "<gradient:" + toHash(c1) + ":" + toHash(c2) + ":" + toHash(c3) + ">"
                    + sourceText + "</gradient>";
        }

        private static @NotNull String toAmpHex(int rgb) {
            String hex = String.format("%06X", rgb & 0xFFFFFF);
            StringBuilder out = new StringBuilder("&#");
            out.append(hex);
            return out.toString();
        }

        private static @NotNull String toHash(int rgb) {
            return String.format("#%06X", rgb & 0xFFFFFF);
        }

        public int changeInterval() {
            return intervalTicks;
        }

        public @NotNull List<String> texts() {
            if (kind == Kind.FRAMES) {
                return frames;
            }
            return List.of(current(0));
        }
    }
}
