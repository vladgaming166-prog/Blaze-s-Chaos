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
 * TAB-style animation definitions from scoreboardanimations.yml.
 * Reference in scoreboard lines with {@code %animation:name%} or {@code {animation:name}}.
 */
public final class AnimationManager {

    private static final Pattern PERCENT = Pattern.compile("%animation:([a-zA-Z0-9_-]+)%", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACE = Pattern.compile("\\{animation:([a-zA-Z0-9_-]+)}", Pattern.CASE_INSENSITIVE);

    private final BlazesChaosPlugin plugin;
    private final Map<String, Animation> animations = new LinkedHashMap<>();
    private long ticks;

    public AnimationManager(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        animations.clear();
        FileConfiguration config = plugin.configs().animations();
        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            int interval = Math.max(1, section.getInt("change-interval", 20));
            List<String> texts = section.getStringList("texts");
            if (texts.isEmpty()) {
                continue;
            }
            animations.put(key.toLowerCase(Locale.ROOT), new Animation(interval, texts));
        }
        if (plugin.configs().debug()) {
            plugin.getLogger().info("Loaded " + animations.size() + " scoreboard animations.");
        }
    }

    public void tick() {
        ticks++;
    }

    public @NotNull String resolve(@NotNull String input) {
        String text = replace(PERCENT, input);
        text = replace(BRACE, text);
        return text;
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
            int frame = (int) ((globalTicks / changeInterval) % texts.size());
            return texts.get(frame);
        }

        public int changeInterval() {
            return changeInterval;
        }

        public @NotNull List<String> texts() {
            return texts;
        }
    }
}
