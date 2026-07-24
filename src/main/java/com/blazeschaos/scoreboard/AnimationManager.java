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
 * Single efficient TAB-style animation engine.
 * <p>
 * Each placeholder resolves to EXACTLY one named animation
 * ({@code %blazechaosanimation_title%} → only "title").
 * <p>
 * Supports:
 * <ul>
 *   <li>Frame cycling ({@code frames} / {@code texts})</li>
 *   <li>Moving RGB / gradient waves that travel across letters</li>
 * </ul>
 */
public final class AnimationManager {

    private static final Pattern[] PATTERNS = {
            Pattern.compile("%blazechaosanimation_([a-zA-Z0-9_-]+)%", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%blazechaos_animation_([a-zA-Z0-9_-]+)%", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%animation:([a-zA-Z0-9_-]+)%", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\{animation:([a-zA-Z0-9_-]+)}", Pattern.CASE_INSENSITIVE)
    };

    /** Classic Blaze's Chaos orange → gold brand palette (Update 3–4). */
    private static final int[] BRAND_PALETTE = {
            0xFF4500, 0xFF6347, 0xFFA500, 0xFFD700, 0xFFA500, 0xFF6347, 0xFF4500
    };

    private static final int[] RAINBOW_PALETTE = {
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
            boolean moving = section.getBoolean("rgb", false)
                    || section.getBoolean("gradient", false)
                    || section.getBoolean("moving-gradient", false)
                    || type.equals("rgb")
                    || type.equals("rainbow")
                    || type.equals("birdflop")
                    || type.equals("gradient")
                    || type.equals("moving-gradient")
                    || type.equals("shifting-gradient");
            boolean rainbow = type.equals("rgb") || type.equals("rainbow") || type.equals("birdflop")
                    || (section.getBoolean("rgb", false) && !section.getBoolean("gradient", false)
                    && !type.equals("gradient") && !type.equals("moving-gradient"));

            List<Integer> palette = parsePalette(section.getStringList("colors"));
            if (palette.isEmpty()) {
                palette = parsePalette(section.getStringList("palette"));
            }

            List<String> frames = section.getStringList("frames");
            if (frames.isEmpty()) {
                frames = section.getStringList("texts");
            }

            String text = section.getString("text", section.getString("input", ""));
            if ((text == null || text.isBlank()) && !frames.isEmpty() && moving) {
                text = frames.get(0);
            }
            boolean bold = section.getBoolean("bold", true);
            double speed = section.getDouble("speed", 0.18);
            double spread = section.getDouble("spread", 0.42);

            Animation animation;
            if (moving && text != null && !text.isBlank()) {
                int[] colors = toArray(palette, rainbow ? RAINBOW_PALETTE : BRAND_PALETTE);
                animation = Animation.movingGradient(interval, stripForRgb(text), colors, bold, speed, spread);
            } else if (!frames.isEmpty()) {
                animation = Animation.frames(interval, frames);
            } else {
                continue;
            }
            animations.put(id, animation);
        }
    }

    private static @NotNull String stripForRgb(@NotNull String input) {
        String text = input;
        text = text.replaceAll("(?i)</?gradient[^>]*>", "");
        text = text.replaceAll("(?i)</?rainbow[^>]*>", "");
        text = text.replaceAll("(?i)</?#[0-9A-Fa-f]{6}>", "");
        text = text.replaceAll("(?i)</?(bold|italic|underlined|strikethrough|obfuscated|reset|b|i|u)>", "");
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

    private static int[] toArray(@NotNull List<Integer> palette, @NotNull int[] fallback) {
        if (palette.isEmpty()) {
            return fallback.clone();
        }
        int[] arr = new int[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            arr[i] = palette.get(i);
        }
        return arr;
    }

    public void tick() {
        ticks++;
    }

    public long ticks() {
        return ticks;
    }

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
        StringBuilder sb = new StringBuilder(input.length() + 64);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            Animation animation = animations.get(name);
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
        private final boolean bold;
        private final double speed;
        private final double spread;

        private enum Kind {FRAMES, MOVING_GRADIENT}

        private Animation(int intervalTicks, @NotNull Kind kind, @NotNull List<String> frames,
                          @Nullable String sourceText, @NotNull int[] palette,
                          boolean bold, double speed, double spread) {
            this.intervalTicks = Math.max(1, intervalTicks);
            this.kind = kind;
            this.frames = List.copyOf(frames);
            this.sourceText = sourceText == null ? "" : sourceText;
            this.palette = palette.clone();
            this.bold = bold;
            this.speed = speed;
            this.spread = spread;
        }

        public static @NotNull Animation frames(int interval, @NotNull List<String> frames) {
            return new Animation(interval, Kind.FRAMES, frames, null, BRAND_PALETTE, false, 0.18, 0.42);
        }

        public static @NotNull Animation movingGradient(int interval, @NotNull String text,
                                                        @NotNull int[] palette, boolean bold,
                                                        double speed, double spread) {
            return new Animation(interval, Kind.MOVING_GRADIENT, List.of(), text, palette, bold, speed, spread);
        }

        public @NotNull String current(long globalTicks) {
            int step = (int) (globalTicks / (long) intervalTicks);
            return switch (kind) {
                case FRAMES -> {
                    if (frames.isEmpty()) {
                        yield "";
                    }
                    yield frames.get(Math.floorMod(step, frames.size()));
                }
                case MOVING_GRADIENT -> renderMovingGradient(step);
            };
        }

        /**
         * TAB-like traveling gradient: colors continuously move across letters.
         * Each character samples a smoothly interpolated point on the palette wave.
         */
        private @NotNull String renderMovingGradient(int step) {
            if (sourceText.isEmpty()) {
                return "";
            }
            StringBuilder out = new StringBuilder(sourceText.length() * 18);
            int visible = 0;
            for (int i = 0; i < sourceText.length(); i++) {
                if (sourceText.charAt(i) != ' ') {
                    visible++;
                }
            }
            int painted = 0;
            double phase = step * speed;
            for (int i = 0; i < sourceText.length(); i++) {
                char ch = sourceText.charAt(i);
                if (ch == ' ') {
                    out.append(' ');
                    continue;
                }
                // Wave travels left→right across visible characters
                double t = painted * spread + phase;
                int color = samplePalette(palette, t);
                out.append("<#").append(String.format("%06X", color & 0xFFFFFF)).append('>');
                if (bold) {
                    out.append("<bold>").append(ch).append("</bold>");
                } else {
                    out.append(ch);
                }
                painted++;
            }
            return out.toString();
        }

        /** Smooth multi-stop palette sample. {@code t} is continuous and wraps. */
        private static int samplePalette(@NotNull int[] palette, double t) {
            if (palette.length == 1) {
                return palette[0];
            }
            int segments = palette.length - 1;
            double wrapped = t - Math.floor(t);
            if (wrapped < 0) {
                wrapped += 1.0;
            }
            double scaled = wrapped * segments;
            int index = (int) Math.floor(scaled);
            if (index >= segments) {
                index = segments - 1;
            }
            double local = scaled - index;
            return lerpColor(palette[index], palette[index + 1], local);
        }

        private static int lerpColor(int a, int b, double t) {
            t = Math.max(0.0, Math.min(1.0, t));
            int ar = (a >> 16) & 0xFF;
            int ag = (a >> 8) & 0xFF;
            int ab = a & 0xFF;
            int br = (b >> 16) & 0xFF;
            int bg = (b >> 8) & 0xFF;
            int bb = b & 0xFF;
            int r = (int) Math.round(ar + (br - ar) * t);
            int g = (int) Math.round(ag + (bg - ag) * t);
            int bl = (int) Math.round(ab + (bb - ab) * t);
            return (r << 16) | (g << 8) | bl;
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
