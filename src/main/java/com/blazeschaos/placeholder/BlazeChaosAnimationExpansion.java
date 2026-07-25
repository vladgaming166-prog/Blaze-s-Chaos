package com.blazeschaos.placeholder;

import com.blazeschaos.BlazesChaosPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for {@code %blazechaosanimation_<name>%}.
 */
public final class BlazeChaosAnimationExpansion extends PlaceholderExpansion {

    private final BlazesChaosPlugin plugin;

    public BlazeChaosAnimationExpansion(@NotNull BlazesChaosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "blazechaosanimation";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Blaze";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(@Nullable Player player, @NotNull String params) {
        if (params.isBlank()) {
            return "";
        }
        return plugin.animationManager().frame(params.trim());
    }
}
