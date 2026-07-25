package com.blazeschaos.npc;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum NpcAnimationType {
    IDLE,
    WAVE,
    SPIN,
    LOOK_AROUND,
    JUMP,
    CELEBRATE,
    RANDOM,
    WALK_IN_PLACE;

    public static @NotNull NpcAnimationType parse(@NotNull String raw) {
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (key.equals("WALKING") || key.equals("WALKING_IN_PLACE") || key.equals("WALK")) {
            return WALK_IN_PLACE;
        }
        return valueOf(key);
    }

    public @NotNull NpcAnimationType next() {
        NpcAnimationType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
