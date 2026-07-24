package com.blazeschaos.game;

public enum GameState {
    LOBBY("Lobby"),
    WAITING("Waiting"),
    STARTING("Starting"),
    PLAYING("Playing"),
    CHAOS_EVENT("Chaos Event"),
    DEATHMATCH("Deathmatch"),
    ENDING("Ending"),
    RESETTING("Resetting");

    private final String display;

    GameState(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public boolean isJoinable() {
        return this == LOBBY || this == WAITING || this == STARTING;
    }

    public boolean isActive() {
        return this == PLAYING || this == CHAOS_EVENT || this == DEATHMATCH;
    }

    public boolean isEndingOrReset() {
        return this == ENDING || this == RESETTING;
    }
}
