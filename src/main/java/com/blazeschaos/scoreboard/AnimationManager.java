package com.blazeschaos.scoreboard;

import com.blazeschaos.BlazesChaosPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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
 * Animation engine for scoreboards, holograms, placeholders, etc.
 * Loads both scoreboardanimations.yml and animations.yml.
 */
public final class AnimationManager {

    private static final Pattern PERCENT = Pattern.compile("%animation:([a-zA-Z0-9_-]+)%", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACE = Pattern.compile("\\{animation:([a-zA-Z0-9_-]+)}", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLAZE = Pattern.compile("%blazechaos_animation_([a-zA-Z0-9_-]+)%", Pattern.CASE_INSENSITIVE);

    private final BlazesChaosPlugin plugin;
    private final Map<String, Animation> animations = new LinkedHashMap<>();
    private long ticks;

    public AnimationManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        animations.clear();
        loadFrom(plugin.configs().scoreboardAnimations());
        loadFrom(plugin.configs().globalAnimations());
        if (plugin.configs().debug()) {
            plugin.getLogger().info("Loaded " + animations.size() + " animations.");
        }
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
            int interval = Math.max(1, section.getInt("change-interval",
                    section.getInt("interval", 20)));
            List<String> texts = section.getStringList("texts");
            if (texts.isEmpty()) {
                texts = section.getStringList("frames");
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
        String text = replace(PERCENT, input);
        text = replace(BRACE, text);
        text = replace(BLAZE, text);
        return text;
    }

    public @NotNull String frame(@NotNull String name) {
        Animation animation = animations.get(name.toLowerCase(Locale.ROOT));
        return animation == null ? "" : animation.current(ticks);
    }

    private @NotNull String replace(@NotNull Pattern pattern, @NotNull String input) {
        Matcher matcher = pattern.matcher(input);
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
        private final int changeInterval;
        private final List<String> texts;

        public Animation(int changeInterval, @NotNull List<String> texts) {
            this.changeInterval = changeInterval;
            this.texts = List.copyOf(texts);
        }

        public @NotNull String current(long globalTicks) {
            int frame = (int) ((globalTicks / Math.max(1, changeInterval)) % texts.size());
            return texts.get(Math.max(0, frame));
        }

        public int changeInterval() {
            return changeInterval;
        }

        public @NotNull List<String> texts() {
            return texts;
        }
    }
}
