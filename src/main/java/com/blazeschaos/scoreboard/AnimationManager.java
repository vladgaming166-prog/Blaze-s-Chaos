package com.blazeschaos.scoreboard;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TAB-style animation engine.
 * Supports:
 * <ul>
 *   <li>{@code %animation:name%}</li>
 *   <li>{@code {animation:name}}</li>
 *   <li>{@code %blazechaos_animation_name%}</li>
 *   <li>{@code %blazechaosanimation_name%}</li>
 * </ul>
 * Loads {@code scoreboardanimations.yml} and {@code animations.yml}.
 * Uses {@code interval}/{@code frames} or {@code change-interval}/{@code texts}.
 */
public final class AnimationManager {

    private static final Pattern[] PATTERNS = {
            Pattern.compile("%animation:([a-zA-Z0-9_-]+)%", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\{animation:([a-zA-Z0-9_-]+)}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%blazechaos_animation_([a-zA-Z0-9_-]+)%", Pattern.CASE_INSENSITIVE),
            Pattern.compile("%blazechaosanimation_([a-zA-Z0-9_-]+)%", Pattern.CASE_INSENSITIVE)
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
        // Advance every tick so placeholders stay smooth across scoreboard/tab/hologram refresh rates
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
            // TAB-style: interval is in ticks between frame changes
            int interval = Math.max(1, section.getInt("interval",
                    section.getInt("change-interval", 20)));
            List<String> texts = section.getStringList("frames");
            if (texts.isEmpty()) {
                texts = section.getStringList("texts");
            }
            if (texts.isEmpty()) {
                continue;
            }
            animations.put(key.toLowerCase(Locale.ROOT), new Animation(interval, texts));
        }
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
            text = replace(pattern, text);
        }
        return text;
    }

    public @NotNull String frame(@NotNull String name) {
        Animation animation = animations.get(name.toLowerCase(Locale.ROOT));
        return animation == null ? "" : animation.current(ticks);
    }

    private @NotNull String replace(@NotNull Pattern pattern, @NotNull String input) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return input;
        }
        matcher.reset();
        StringBuilder sb = new StringBuilder();
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
        private final List<String> frames;

        public Animation(int intervalTicks, @NotNull List<String> frames) {
            this.intervalTicks = Math.max(1, intervalTicks);
            this.frames = List.copyOf(frames);
        }

        public @NotNull String current(long globalTicks) {
            int frame = (int) ((globalTicks / (long) intervalTicks) % frames.size());
            if (frame < 0) {
                frame = 0;
            }
            return frames.get(frame);
        }

        public int changeInterval() {
            return intervalTicks;
        }

        public @NotNull List<String> texts() {
            return frames;
        }
    }
}
